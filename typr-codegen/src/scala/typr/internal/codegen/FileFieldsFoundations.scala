package typr
package internal
package codegen

import typr.internal.compat.ListOps
import typr.jvm.Code.TypeOps

class FileFieldsFoundations(
    val lang: Lang,
    val naming: Naming,
    val names: ComputedNames,
    val options: InternalOptions,
    val fks: List[db.ForeignKey],
    val maybeFkAnalysis: Option[FkAnalysis],
    val maybeCols: Option[NonEmptyList[ComputedColumn]],
    dbLib: DbLibFoundations
) extends FileFields {

  override protected val sqlCast: SqlCast = new SqlCast(needsTimestampCasts = false)

  private val dsl: DslQualifiedNamesFoundations = lang.dsl match {
    case f: DslQualifiedNamesFoundations => f
    case other                           => sys.error(s"FileFieldsFoundations requires a modern DSL (Scala/Java/Kotlin), got $other")
  }

  override def generate(fieldsName: jvm.Type.Qualified, cols: NonEmptyList[ComputedColumn]): jvm.File = {
    val colsList = cols.toList
    val useTupleExprN = colsList.size <= 100

    val allMembers: List[jvm.ClassMember] =
      fieldMethods(cols) ++
        pathOverrideMethod.toList ++
        foreignKeyMethods ++
        extractFkMethods(dbLib) ++
        compositeIdMethods(dbLib) ++
        List(columnsMethod(cols), rowParserMethod(), withPathsMethod(fieldsName)) ++
        tupleAccessorMethods(colsList)

    val (extendsType, implementsList) = extendsAndImplements(fieldsName, colsList)

    val fieldsClass = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      classType = jvm.ClassType.Class,
      name = fieldsName,
      tparams = Nil,
      params = List(jvm.Param(jvm.Ident("_path"), pathListType)),
      implicitParams = Nil,
      `extends` = extendsType,
      implements = implementsList,
      members = allMembers,
      staticMembers = List(structureMember(fieldsName))
    )

    jvm.File(fieldsName, fieldsClass.code, Nil, scope = Scope.Main)
  }

  private val pathType: jvm.Type.Qualified = DslQualifiedNames.Java.Path

  private def pathListType: jvm.Type = lang match {
    case _: LangKotlin => TypesKotlin.List.of(pathType)
    case _             => TypesJava.List.of(pathType)
  }

  private def emptyPathList: jvm.Code = lang match {
    case _: LangKotlin => code"emptyList<$pathType>()"
    case _             => code"java.util.Collections.emptyList()"
  }

  private def fieldMethods(cols: NonEmptyList[ComputedColumn]): List[jvm.Method] =
    cols.toList.map { col =>
      val (cls, tpe, typoTypeForLookup) =
        if (names.isIdColumn(col.dbName)) (lang.dsl.IdField, col.tpe, col.typoType)
        else
          col.typoType match {
            case TypoType.Nullable(_, inner) => (lang.dsl.OptField, inner.jvmType, inner)
            case _                           => (lang.dsl.Field, col.tpe, col.typoType)
          }

      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = col.name,
        params = Nil,
        implicitParams = Nil,
        tpe = cls.of(tpe, names.RowName),
        throws = Nil,
        body = jvm.Body.Expr(
          cls
            .of(tpe, names.RowName)
            .construct(
              code"_path",
              jvm.StrLit(col.dbName.value),
              jvm.FieldGetterRef(names.RowName, col.name),
              sqlCast.fromPg(col.dbCol.tpe) match {
                case Some(cast) => lang.Optional.some(jvm.StrLit(cast.typeName))
                case None       => lang.Optional.none
              },
              sqlCast.toPg(col.dbCol) match {
                case Some(cast) => lang.Optional.some(jvm.StrLit(cast.typeName))
                case None       => lang.Optional.none
              },
              lang.rowSetter(col.name),
              dbLib.lookupType(typoTypeForLookup)
            )
        ),
        isOverride = false,
        isDefault = false
      )
    }

  private def pathOverrideMethod: Option[jvm.Method] = lang match {
    case _: LangKotlin | LangJava =>
      Some(
        jvm.Method(
          Nil,
          jvm.Comments.Empty,
          Nil,
          jvm.Ident("_path"),
          Nil,
          Nil,
          pathListType,
          Nil,
          jvm.Body.Expr(code"_path"),
          isOverride = true,
          isDefault = false
        )
      )
    case _ => None
  }

  private def foreignKeyMethods: List[jvm.Method] =
    names.source match {
      case relation: Source.Relation =>
        val byOtherTable = fks.groupBy(_.otherTable)
        fks
          .sortBy(_.constraintName.value)
          .map { fk =>
            val otherTableSource = Source.Table(fk.otherTable)
            val otherFieldsType = jvm.Type.Qualified(naming.fieldsName(otherTableSource))
            val otherRowType = jvm.Type.Qualified(naming.rowName(otherTableSource))
            val fkType = lang.dsl.ForeignKey.of(otherFieldsType, otherRowType)
            val fkName = naming.fk(relation.name, fk, includeCols = byOtherTable(fk.otherTable).size > 1)

            val fkConstruction = jvm.GenericMethodCall(
              lang.dsl.ForeignKey.code,
              jvm.Ident("of"),
              List(otherFieldsType, otherRowType),
              List(jvm.Arg.Pos(jvm.StrLit(fk.constraintName.value).code))
            )

            val colsByName: Map[db.ColName, ComputedColumn] =
              maybeCols.map(_.toList.map(c => c.dbName -> c).toMap).getOrElse(Map.empty)
            val colPairs: List[(db.ColName, db.ColName)] = fk.cols.zip(fk.otherCols).toList
            val fkWithPairs = colPairs.foldLeft(fkConstruction.code) { case (acc, (colName, otherColName)) =>
              val thisField = jvm.SelfNullary(naming.field(colName))
              val otherGetter = jvm.FieldGetterRef(otherFieldsType, naming.field(otherColName))
              val colType = colsByName.get(colName).map(col => col.tpe.stripNullable(lang)).getOrElse(jvm.Type.Qualified("Any"))
              jvm
                .GenericMethodCall(
                  acc,
                  jvm.Ident("withColumnPair"),
                  List(colType),
                  List(jvm.Arg.Pos(thisField.code), jvm.Arg.Pos(otherGetter.code))
                )
                .code
            }

            val method = jvm.Method(
              annotations = Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = fkName,
              params = Nil,
              implicitParams = Nil,
              tpe = fkType,
              throws = Nil,
              body = jvm.Body.Expr(fkWithPairs),
              isOverride = false,
              isDefault = false
            )
            (fkName, method)
          }
          .distinctByCompat(_._1)
          .map(_._2)
      case Source.SqlFile(_) => Nil
    }

  private def columnsMethod(cols: NonEmptyList[ComputedColumn]): jvm.Method = {
    val javaFieldLike = dsl.FieldLikeJava.of(jvm.Type.Wildcard, names.RowName)
    val columnsList = lang match {
      case _: LangKotlin => TypesKotlin.List.of(javaFieldLike)
      case _             => TypesJava.List.of(javaFieldLike)
    }

    val elements = cols.toList.map { c =>
      val accessor = jvm.ApplyNullary(code"this", c.name).code
      dsl match {
        case DslQualifiedNames.Scala | DslQualifiedNames.Kotlin => code"$accessor.underlying"
        case _                                                  => accessor
      }
    }
    val columnsExpr = lang match {
      case _: LangKotlin => code"listOf(${elements.mkCode(", ")})"
      case _             => code"java.util.List.of(${elements.mkCode(", ")})"
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("columns"),
      params = Nil,
      implicitParams = Nil,
      tpe = columnsList,
      throws = Nil,
      body = jvm.Body.Expr(columnsExpr),
      isOverride = true,
      isDefault = false
    )
  }

  private def rowParserMethod(): jvm.Method = {
    val (rowParserType, rowParserBody) = dsl match {
      case DslQualifiedNames.Kotlin | DslQualifiedNames.Scala =>
        val javaRowParser = dsl.RowParserJava.of(names.RowName)
        (javaRowParser, code"${names.RowName}._rowParser.underlying")
      case _ =>
        (dsl.RowParser.of(names.RowName), code"${names.RowName}._rowParser")
    }
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("rowParser"),
      params = Nil,
      implicitParams = Nil,
      tpe = rowParserType,
      throws = Nil,
      body = jvm.Body.Expr(rowParserBody),
      isOverride = true,
      isDefault = false
    )
  }

  private def withPathsMethod(fieldsName: jvm.Type.Qualified): jvm.Method = {
    val copyPathParam = jvm.Param(jvm.Ident("_path"), pathListType)
    jvm.Method(
      Nil,
      jvm.Comments.Empty,
      Nil,
      jvm.Ident("withPaths"),
      List(copyPathParam),
      Nil,
      lang.dsl.StructureRelation.of(fieldsName, names.RowName),
      Nil,
      jvm.Body.Expr(jvm.New(fieldsName, List(jvm.Arg.Pos(copyPathParam.name.code)))),
      isOverride = true,
      isDefault = false
    )
  }

  private def tupleAccessorMethods(colsList: List[ComputedColumn]): List[jvm.Method] =
    if (colsList.size <= 100) {
      colsList.zipWithIndex.map { case (col, idx) =>
        val tpe = col.tpe match {
          case lang.Optional(underlying) => underlying
          case other                     => other
        }
        val accessorBody = lang match {
          case _: LangScala => code"${col.name}"
          case _            => code"${col.name}()"
        }
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident(s"_${idx + 1}"),
          params = Nil,
          implicitParams = Nil,
          tpe = lang.dsl.SqlExpr.of(tpe),
          throws = Nil,
          body = jvm.Body.Expr(accessorBody),
          isOverride = true,
          isDefault = false
        )
      }
    } else Nil

  private def extendsAndImplements(fieldsName: jvm.Type.Qualified, colsList: List[ComputedColumn]): (Option[jvm.Type], List[jvm.Type]) = {
    val n = colsList.size
    val useTupleExprN = n <= 100

    val fieldsExprType =
      if (useTupleExprN) {
        val colTypes = colsList.map { col =>
          col.tpe match {
            case lang.Optional(underlying) => underlying
            case other                     => other
          }
        }
        dsl.TupleExprN(n).of(colTypes*)
      } else {
        dsl.FieldsExpr(names.RowName)
      }

    val fieldsBaseType: Option[jvm.Type] =
      if (useTupleExprN)
        Some(dsl.FieldsBase.of(names.RowName))
      else
        None

    val otherTypes = List(dsl.StructureRelation.of(fieldsName, names.RowName)) ++ fieldsBaseType.toList

    // For Java, TupleExprN is now an abstract class (to work around Scala 3 sealed bug),
    // so it must be in extends, not implements
    lang match {
      case LangJava if useTupleExprN =>
        (Some(fieldsExprType), otherTypes)
      case _ =>
        (None, List(fieldsExprType) ++ otherTypes)
    }
  }

  private def structureMember(fieldsName: jvm.Type.Qualified): jvm.ClassMember =
    jvm.Value(
      Nil,
      jvm.Ident("structure"),
      fieldsName,
      Some(jvm.New(fieldsName, List(jvm.Arg.Pos(emptyPathList))).code),
      isLazy = false,
      isOverride = false
    )
}
