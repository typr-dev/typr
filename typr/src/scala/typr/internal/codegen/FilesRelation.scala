package typr
package internal
package codegen

import play.api.libs.json.Json
import typr.internal.compat.ListOps
import typr.jvm.Code.TypeOps
import typr.jvm.Type

case class FilesRelation(
    lang: Lang,
    naming: Naming,
    names: ComputedNames,
    maybeCols: Option[NonEmptyList[ComputedColumn]],
    maybeFkAnalysis: Option[FkAnalysis],
    options: InternalOptions,
    fks: List[db.ForeignKey]
) {
  // TypeMapperJvmNew (used for DbLibTypo) doesn't need timestamp casts
  // TypeMapperJvmOld (used for all other libs) needs them
  private val sqlCast: SqlCast = options.dbLib match {
    case Some(_: DbLibTypo) => new SqlCast(needsTimestampCasts = false)
    case _                  => new SqlCast(needsTimestampCasts = true)
  }
  def RowFile(rowType: DbLib.RowType, comment: Option[String], maybeUnsavedRow: Option[(ComputedRowUnsaved, ComputedDefault)]): Option[jvm.File] =
    maybeCols.map { cols =>
      val members = List[Iterable[jvm.ClassMember]](
        names.maybeId.collect { case x: IdComputed.Composite =>
          val body = jvm.New(x.tpe, x.cols.toList.map(x => jvm.Arg.Pos(x.name.code)))
          jvm.Method(Nil, jvm.Comments.Empty, Nil, x.paramName, Nil, Nil, x.tpe, Nil, jvm.Body.Expr(body), isOverride = false, isDefault = false)
        },
        // id member which points to either `compositeId` val defined above or id column
        if (maybeCols.exists(_.exists(_.name.value == "id"))) None
        else
          names.maybeId.collect {
            case id: IdComputed.Unary =>
              jvm.Method(Nil, jvm.Comments.Empty, Nil, jvm.Ident("id"), Nil, Nil, id.tpe, Nil, jvm.Body.Expr(id.col.name.code), isOverride = false, isDefault = false)
            case id: IdComputed.Composite =>
              jvm.Method(
                Nil,
                jvm.Comments.Empty,
                Nil,
                jvm.Ident("id"),
                Nil,
                Nil,
                id.tpe,
                Nil,
                jvm.Body.Expr(jvm.ApplyNullary(code"this", id.paramName)),
                isOverride = false,
                isDefault = false
              )
          },
        maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRowNotId).map { extractFkId =>
          val args = extractFkId.colPairs.map { case (inComposite, inId) => jvm.Arg.Named(inComposite.name, inId.name.code) }
          jvm.Method(
            annotations = Nil,
            comments = jvm.Comments.Empty,
            tparams = Nil,
            name = extractFkId.name.prepended("extract"),
            params = Nil,
            implicitParams = Nil,
            tpe = extractFkId.otherCompositeIdType,
            throws = Nil,
            body = jvm.Body.Expr(jvm.New(extractFkId.otherCompositeIdType, args)),
            isOverride = false,
            isDefault = false
          )
        },
        maybeUnsavedRow.map { case (unsaved, defaults) =>
          val (partOfId, rest) = unsaved.defaultedCols.partition { case ComputedRowUnsaved.DefaultedCol(col, _, _) => names.isIdColumn(col.dbName) }
          val partOfIdParams = partOfId.map { case ComputedRowUnsaved.DefaultedCol(col, _, _) => jvm.Param(col.name, col.tpe) }
          val restParams = rest.map { case ComputedRowUnsaved.DefaultedCol(col, _, _) =>
            jvm.Param(col.name, col.tpe).copy(default = Some(code"${defaults.Defaulted}.${defaults.Provided}(this.${col.name})"))
          }
          val params = partOfIdParams ++ restParams
          jvm.Method(
            Nil,
            comments = jvm.Comments.Empty,
            tparams = Nil,
            name = jvm.Ident("toUnsavedRow"),
            params = params,
            implicitParams = Nil,
            tpe = unsaved.tpe,
            throws = Nil,
            body = jvm.Body.Expr(jvm.New(unsaved.tpe, unsaved.unsavedCols.toList.map(col => jvm.Arg.Pos(col.name)))),
            isOverride = false,
            isDefault = false
          )
        }
      ).flatten

      val jsonInstances = options.jsonLibs.map(_.instances(names.RowName, cols))
      val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
      val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

      val commentedParams: NonEmptyList[jvm.Param[jvm.Type]] =
        cols.map { col =>
          val commentPieces = List[Iterable[String]](
            col.dbCol.comment,
            col.dbCol.columnDefault.map(x => s"Default: $x"),
            col.dbCol.maybeGenerated.map(_.asString),
            col.pointsTo.map { case (relationName, columnName) => lang.docLink(naming.rowName(relationName), naming.field(columnName)) },
            col.dbCol.constraints.map(c => s"Constraint ${c.name} affecting columns ${c.columns.map(_.value).mkString(", ")}: ${c.checkClause}"),
            if (options.debugTypes)
              col.dbCol.jsonDescription.maybeJson.map(other => s"debug: ${Json.stringify(other)}")
            else None
          ).flatten

          val annotations = fieldAnnotations.getOrElse(col.name, Nil)
          col.param.copy(annotations = annotations, comments = jvm.Comments(commentPieces))
        }

      val instances: List[jvm.ClassMember] =
        jsonInstances.flatMap(_.givens) ++
          options.dbLib.toList.flatMap(_.rowInstances(names.RowName, cols, rowType = rowType))

      val classComment = {
        jvm.Comments(
          List[Iterable[String]](
            Some(names.source match {
              case Source.Table(name)       => s"Table: ${name.value}"
              case Source.View(name, true)  => s"Materialized View: ${name.value}"
              case Source.View(name, false) => s"View: ${name.value}"
              case Source.SqlFile(relPath)  => s"SQL file: ${relPath.asString}"
            }),
            comment,
            names.maybeId.map {
              case x: IdComputed.Unary     => s"Primary key: ${x.col.dbName.value}"
              case x: IdComputed.Composite => s"Composite primary key: ${x.cols.map(_.dbName.value).mkString(", ")}"
            }
          ).flatten
        )
      }

      val maybeExtraApply: Option[jvm.Method] =
        names.maybeId.collect { case id: IdComputed.Composite =>
          val nonKeyColumns = cols.toList.filter(col => !names.isIdColumn(col.dbCol.name))
          val params = jvm.Param(id.paramName, id.tpe) :: nonKeyColumns.map(col => jvm.Param(col.name, col.tpe))
          val args = cols.map(col => jvm.Arg.Pos(if (names.isIdColumn(col.dbCol.name)) lang.prop(id.paramName.code, col.name) else col.name.code))
          jvm.Method(
            Nil,
            comments = jvm.Comments.Empty,
            Nil,
            jvm.Ident("apply"),
            params,
            Nil,
            names.RowName,
            Nil,
            jvm.Body.Expr(jvm.New(names.RowName, args.toList)),
            isOverride = false,
            isDefault = false
          )
        }

      jvm.File(
        names.RowName,
        jvm.Adt.Record(
          annotations = typeAnnotations,
          constructorAnnotations = Nil,
          isWrapper = false,
          comments = classComment,
          name = names.RowName,
          tparams = Nil,
          params = commentedParams.toList,
          implicitParams = Nil,
          `extends` = None,
          implements = Nil,
          members = members,
          staticMembers = instances ++ maybeExtraApply.toList
        ),
        secondaryTypes = Nil,
        scope = Scope.Main
      )
    }

  val FieldValueFile: Option[jvm.File] =
    for {
      fieldValueName <- names.FieldOrIdValueName
      cols <- maybeCols
    } yield {
      val T = jvm.Type.Abstract(jvm.Ident("T"))
      val abstractMembers = List(
        jvm.Method(Nil, jvm.Comments.Empty, Nil, jvm.Ident("name"), Nil, Nil, TypesJava.String, Nil, jvm.Body.Abstract, isOverride = false, isDefault = false),
        jvm.Method(Nil, jvm.Comments.Empty, Nil, jvm.Ident("value"), Nil, Nil, T, Nil, jvm.Body.Abstract, isOverride = false, isDefault = false)
      )

      val colRecords = cols.toList.map { col =>
        jvm.Adt.Record(
          annotations = Nil,
          constructorAnnotations = Nil,
          isWrapper = false,
          comments = jvm.Comments.Empty,
          name = jvm.Type.Qualified(col.name),
          tparams = Nil,
          params = List(jvm.Param(jvm.Ident("value"), col.tpe)),
          implicitParams = Nil,
          `extends` = None,
          implements = List(fieldValueName.of(col.tpe)),
          members = List(
            jvm.Method(
              Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = jvm.Ident("name"),
              params = Nil,
              implicitParams = Nil,
              tpe = TypesJava.String,
              throws = Nil,
              body = jvm.Body.Expr(jvm.StrLit(col.dbName.value)),
              isOverride = true,
              isDefault = false
            )
          ),
          staticMembers = Nil
        )
      }

      val fieldOrIdValue = jvm.Adt.Sum(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        name = fieldValueName,
        tparams = List(T),
        implements = Nil,
        members = abstractMembers,
        staticMembers = Nil,
        subtypes = colRecords
      )
      jvm.File(fieldValueName, fieldOrIdValue, secondaryTypes = Nil, scope = Scope.Main)
    }

  val FieldsFile: Option[jvm.File] =
    for {
      fieldsName <- names.FieldsName
      cols <- maybeCols
      dbLib <- options.dbLib
    } yield dbLib match {
      // DbLibTypo uses typr-dsl-java which has different parameter order
      case typoLib: DbLibTypo => fieldsFileTypo(fieldsName, cols, typoLib)
      case _                  => fieldsFileScala(fieldsName, cols, dbLib)
    }

  private def fieldsFileTypo(fieldsName: jvm.Type.Qualified, cols: NonEmptyList[ComputedColumn], dbLib: DbLibTypo): jvm.File = {
    // Interface/trait methods for each column
    val colMethods: List[jvm.Method] = cols.toList.map { col =>
      val (cls, tpe) =
        if (names.isIdColumn(col.dbName)) (lang.dsl.IdField, col.tpe)
        else
          col.tpe match {
            case lang.Optional(underlying) => (lang.dsl.OptField, underlying)
            case _                         => (lang.dsl.Field, col.tpe)
          }
      jvm.Method(Nil, jvm.Comments.Empty, Nil, col.name, Nil, Nil, cls.of(tpe, names.RowName), Nil, jvm.Body.Abstract, isOverride = false, isDefault = false)
    }

    // Foreign key methods
    val fkMembers: List[jvm.Method] =
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

              // ForeignKey.of[F, R](constraintName) using GenericMethodCall
              val fkConstruction = jvm.GenericMethodCall(
                lang.dsl.ForeignKey.code,
                jvm.Ident("of"),
                List(otherFieldsType, otherRowType),
                List(jvm.Arg.Pos(jvm.StrLit(fk.constraintName.value).code))
              )

              // Chain withColumnPair calls with explicit type parameters for Kotlin
              val colsByName: Map[db.ColName, ComputedColumn] = maybeCols.map(_.toList.map(c => c.dbName -> c).toMap).getOrElse(Map.empty)
              val colPairs: List[(db.ColName, db.ColName)] = fk.cols.zip(fk.otherCols).toList
              val fkWithPairs = colPairs.foldLeft(fkConstruction.code) { case (acc, (colName, otherColName)) =>
                val thisField = jvm.SelfNullary(naming.field(colName))
                val otherGetter = jvm.FieldGetterRef(otherFieldsType, naming.field(otherColName))
                // Strip nullables since we don't track nullability in the type-system at this level
                val colType = colsByName.get(colName).map(col => col.tpe.stripNullable(lang)).getOrElse(jvm.Type.Qualified("Any")) // fallback to Any if not found
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
                isDefault = true
              )
              (fkName, method)
            }
            .distinctByCompat(_._1)
            .map(_._2)
        case Source.SqlFile(_) => Nil
      }

    // Extract FK methods for composite foreign keys
    val extractFkMember: List[jvm.Method] =
      maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRow).flatMap { (x: FkAnalysis.ExtractFkId) =>
        val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
        val idParam = jvm.Param(jvm.Ident("id"), x.otherCompositeIdType)
        val idsParam = jvm.Param(jvm.Ident("ids"), lang.ListType.tpe.of(x.otherCompositeIdType))

        val isMethod = {
          val equalityExprsList = x.colPairs.map { case (otherCol, thisCol) =>
            val thisField = jvm.SelfNullary(thisCol.name)
            val otherField = lang.prop(idParam.name.code, otherCol.name)
            code"$thisField.isEqual($otherField)"
          }
          jvm.Method(
            annotations = Nil,
            comments = jvm.Comments.Empty,
            tparams = Nil,
            name = jvm.Ident(s"extract${x.name}Is"),
            params = List(idParam),
            implicitParams = Nil,
            tpe = predicateType,
            throws = Nil,
            body = jvm.Body.Expr(dbLib.booleanAndChain(NonEmptyList(equalityExprsList.head, equalityExprsList.tail))),
            isOverride = false,
            isDefault = true
          )
        }

        val inMethod = {
          val parts = x.colPairs.map { case (otherCol, thisCol) =>
            val pgType = dbLib.lookupType(thisCol)
            val fieldExpr = jvm.SelfNullary(thisCol.name)
            dbLib.compositeInPart(thisCol.tpe, x.otherCompositeIdType, names.RowName, fieldExpr, otherCol.name, pgType)
          }
          val body = dbLib.compositeInConstruct(lang.ListType.create(parts.toList), idsParam.name.code)
          jvm.Method(
            annotations = Nil,
            comments = jvm.Comments.Empty,
            tparams = Nil,
            name = jvm.Ident(s"extract${x.name}In"),
            params = List(idsParam),
            implicitParams = Nil,
            tpe = predicateType,
            throws = Nil,
            body = jvm.Body.Expr(body),
            isOverride = false,
            isDefault = true
          )
        }

        List(isMethod, inMethod)
      }

    // Composite ID methods
    val compositeIdMembers: List[jvm.Method] =
      names.maybeId
        .collect {
          case x: IdComputed.Composite if x.cols.forall(_.dbCol.nullability == Nullability.NoNulls) =>
            val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
            val idParam = jvm.Param(x.paramName, x.tpe)
            val idsParam = jvm.Param(x.paramName.appended("s"), lang.ListType.tpe.of(x.tpe))

            val isMethod = {
              val equalityExprs: NonEmptyList[jvm.Code] = x.cols.map { col =>
                val thisField = jvm.SelfNullary(col.name)
                val paramField = lang.prop(idParam.name.code, col.name)
                code"$thisField.isEqual($paramField)"
              }
              jvm.Method(
                annotations = Nil,
                comments = jvm.Comments.Empty,
                tparams = Nil,
                name = jvm.Ident("compositeIdIs"),
                params = List(idParam),
                implicitParams = Nil,
                tpe = predicateType,
                throws = Nil,
                body = jvm.Body.Expr(dbLib.booleanAndChain(equalityExprs)),
                isOverride = false,
                isDefault = true
              )
            }

            val inMethod = {
              jvm.Method(
                annotations = Nil,
                comments = jvm.Comments.Empty,
                tparams = Nil,
                name = jvm.Ident("compositeIdIn"),
                params = List(idsParam),
                implicitParams = Nil,
                tpe = predicateType,
                throws = Nil,
                body = jvm.Body.Expr(
                  dbLib.compositeInConstruct(
                    lang.ListType.create(x.cols.map { col =>
                      val pgType = dbLib.lookupType(col)
                      val fieldExpr = jvm.SelfNullary(col.name)
                      dbLib.compositeInPart(col.tpe, x.tpe, names.RowName, fieldExpr, col.name, pgType)
                    }.toList),
                    idsParam.name.code
                  )
                ),
                isOverride = false,
                isDefault = true
              )
            }

            List(isMethod, inMethod)
        }
        .getOrElse(Nil)

    // Structure implementation - now a record that implements both Relation and Fields
    val ImplName = jvm.Ident("Impl")
    // For interface compliance, _path, copy and columns must use java.util.List (required by Java interface)
    // Also must use typr.dsl types (Java types) not typr.scaladsl types (Scala wrappers)
    // For Kotlin, we use Kotlin's List which maps to java.util.List at runtime
    val javaPathList = lang match {
      case _: LangKotlin => TypesKotlin.List.of(jvm.Type.Qualified("typr.dsl.Path"))
      case _             => TypesJava.List.of(jvm.Type.Qualified("typr.dsl.Path"))
    }
    def emptyPathList: jvm.Code = lang match {
      case _: LangKotlin => code"emptyList<typr.dsl.Path>()"
      case _             => code"java.util.Collections.emptyList()"
    }
    // columns() must also return java.util.List with Java FieldLike type for FieldsExpr interface compliance
    // For Kotlin, use Kotlin's List type which maps to java.util.List at runtime
    val javaFieldLike = jvm.Type.Qualified("typr.dsl.SqlExpr.FieldLike").of(jvm.Type.Wildcard, names.RowName)
    val javaColumnsList = lang match {
      case _: LangKotlin => TypesKotlin.List.of(javaFieldLike)
      case _             => TypesJava.List.of(javaFieldLike)
    }

    // Field implementations for the record
    // Uses _path to avoid conflicts with tables that have a 'path' column
    val fieldImplMethods: List[jvm.Method] = cols.toList.map { col =>
      // For OptField, we need the inner type (unwrapped from Nullable)
      // The pgType parameter should match the field type parameter T, not Option[T]
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
        isOverride = true,
        isDefault = false
      )
    }

    // columns() method - calls each column accessor on this
    // Must use java.util.List for FieldsExpr interface compliance
    // For Scala scalaDsl: must access .underlying to convert Scala wrappers to Java types
    // For Scala javaDsl: types are already Java, no conversion needed
    // For Kotlin: use listOf() which returns kotlin.collections.List (maps to java.util.List at runtime)
    val columnsExpr = {
      val elements = cols.toList.map { c =>
        val accessor = jvm.ApplyNullary(code"this", c.name).code
        lang.dsl.dslPackage match {
          case "typr.scaladsl" | "typr.kotlindsl" => code"$accessor.underlying"
          case _                                  => accessor
        }
      }
      lang match {
        case _: LangKotlin => code"listOf(${elements.mkCode(", ")})"
        case _             => code"java.util.List.of(${elements.mkCode(", ")})"
      }
    }

    val columnsImplMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("columns"),
      params = Nil,
      implicitParams = Nil,
      tpe = javaColumnsList,
      throws = Nil,
      body = jvm.Body.Expr(columnsExpr),
      isOverride = true,
      isDefault = false
    )

    // withPaths method - uses _path to avoid conflicts with tables that have a 'path' column
    val copyPathParam = jvm.Param(jvm.Ident("_path"), javaPathList)
    val withPathsMethod = jvm.Method(
      Nil,
      jvm.Comments.Empty,
      Nil,
      jvm.Ident("withPaths"),
      List(copyPathParam),
      Nil,
      lang.dsl.StructureRelation.of(fieldsName, names.RowName),
      Nil,
      jvm.Body.Expr(jvm.New(jvm.Type.Qualified(ImplName), List(jvm.Arg.Pos(copyPathParam.name.code)))),
      isOverride = true,
      isDefault = false
    )

    // For Kotlin: need explicit _path() method since data class property `_path` generates `get_path()` not `_path()`
    // The Java interface RelationStructure requires a method named `_path()`
    val pathOverrideMethod: Option[jvm.Method] = lang match {
      case _: LangKotlin =>
        Some(
          jvm.Method(
            Nil,
            jvm.Comments.Empty,
            Nil,
            jvm.Ident("_path"),
            Nil,
            Nil,
            javaPathList,
            Nil,
            jvm.Body.Expr(code"_path"),
            isOverride = true,
            isDefault = false
          )
        )
      case _ => None
    }

    // The nested Impl record - implements both Fields and RelationStructure interfaces
    // Uses _path to avoid conflicts with tables that have a 'path' column
    // Order: Fields first, then RelationStructure (both are interfaces)
    val implRecord = jvm.NestedRecord(
      isPrivate = false,
      name = ImplName,
      params = List(jvm.Param(jvm.Ident("_path"), javaPathList)),
      implements = List(fieldsName, lang.dsl.StructureRelation.of(fieldsName, names.RowName)),
      members = fieldImplMethods ++ pathOverrideMethod.toList ++ List(columnsImplMethod, withPathsMethod)
    )

    // Static structure - method for Java, property (val) for Kotlin
    // Kotlin needs a property so that `FieldsType.structure` (property access) works
    // Java needs a method so that `FieldsType.structure()` (method call via prop) works
    val structureMember: jvm.ClassMember = lang match {
      case _: LangKotlin =>
        jvm.Value(
          Nil,
          jvm.Ident("structure"),
          jvm.Type.Qualified(ImplName),
          Some(jvm.New(jvm.Type.Qualified(ImplName), List(jvm.Arg.Pos(emptyPathList))).code),
          isLazy = false,
          isOverride = false
        )
      case _ =>
        jvm.Method(
          Nil,
          jvm.Comments.Empty,
          Nil,
          jvm.Ident("structure"),
          Nil,
          Nil,
          jvm.Type.Qualified(ImplName),
          Nil,
          jvm.Body.Expr(jvm.New(jvm.Type.Qualified(ImplName), List(jvm.Arg.Pos(emptyPathList)))),
          isOverride = false,
          isDefault = false
        )
    }

    // FieldsExpr methods: columns(), decodeRow(), encodeRow()
    val columnsMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("columns"),
      params = Nil,
      implicitParams = Nil,
      tpe = javaColumnsList,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = true,
      isDefault = false
    )

    // Generate rowParser() method that returns RowParser<Row>
    // Uses the Row's existing _rowParser static field
    // For Kotlin and Scala DSL: must use Java typr.runtime.RowParser type (not wrapper) since we implement Java FieldsExpr
    // and access .underlying to get the Java parser from the wrapper
    val (rowParserType, rowParserBody) = lang match {
      case _: LangKotlin =>
        val javaRowParser = jvm.Type.Qualified("typr.runtime.RowParser").of(names.RowName)
        (javaRowParser, code"${names.RowName}._rowParser.underlying")
      case scala: LangScala if scala.dsl == DslQualifiedNames.Scala =>
        val javaRowParser = jvm.Type.Qualified("typr.runtime.RowParser").of(names.RowName)
        (javaRowParser, code"${names.RowName}._rowParser.underlying")
      case _ =>
        (lang.dsl.RowParser.of(names.RowName), code"${names.RowName}._rowParser")
    }
    val rowParserMethod = jvm.Method(
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
      isDefault = true
    )

    // All interface members: column methods (abstract), FK methods (with default body), FieldsExpr methods
    val allMembers: List[jvm.ClassMember] = colMethods ++ fkMembers ++ extractFkMember ++ compositeIdMembers ++ List(columnsMethod, rowParserMethod)

    val fieldsExprType: jvm.Type = {
      val base = lang match {
        // FieldsExpr0 is an abstract class workaround for Scala 3 compiler bug
        case _: LangScala => jvm.Type.Qualified(s"typr.dsl.FieldsExpr0")
        case _            => jvm.Type.Qualified(s"typr.dsl.FieldsExpr")
      }
      base.of(names.RowName)
    }
    // Build the Fields interface using jvm.Class
    // Fields is an interface (not abstract class) to avoid downstream Java issues
    val fieldsClass = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      classType = jvm.ClassType.Interface,
      name = fieldsName,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(fieldsExprType),
      members = allMembers,
      staticMembers = List(structureMember, implRecord)
    )

    jvm.File(fieldsName, fieldsClass.code, Nil, scope = Scope.Main)
  }

  private def fieldsFileScala(fieldsName: jvm.Type.Qualified, cols: NonEmptyList[ComputedColumn], dbLib: DbLib): jvm.File = {
    val colMembers = cols.map { col =>
      val (cls, tpe) =
        if (names.isIdColumn(col.dbName)) (lang.dsl.IdField, col.tpe)
        else
          col.tpe match {
            case lang.Optional(underlying) => (lang.dsl.OptField, underlying)
            case _                         => (lang.dsl.Field, col.tpe)
          }
      code"def ${col.name}: ${cls.of(tpe, names.RowName)}"
    }

    val fkMembers: List[jvm.Code] =
      names.source match {
        case relation: Source.Relation =>
          val byOtherTable = fks.groupBy(_.otherTable)
          fks
            .sortBy(_.constraintName.value)
            .map { fk =>
              val otherTableSource = Source.Table(fk.otherTable)
              val fkType = lang.dsl.ForeignKey.of(
                jvm.Type.Qualified(naming.fieldsName(otherTableSource)),
                jvm.Type.Qualified(naming.rowName(otherTableSource))
              )
              val columnPairs = fk.cols.zip(fk.otherCols).map { case (col, otherCol) =>
                code".withColumnPair(${naming.field(col)}, _.${naming.field(otherCol)})"
              }
              val fkName = naming.fk(relation.name, fk, includeCols = byOtherTable(fk.otherTable).size > 1)
              val body =
                code"""|def $fkName: $fkType =
                     |  $fkType(${jvm.StrLit(fk.constraintName.value)}, Nil)
                     |    ${columnPairs.mkCode("\n")}""".stripMargin
              (fkName, body)
            }
            .distinctByCompat(_._1)
            .map(_._2)
        case Source.SqlFile(_) => Nil
      }

    val extractFkMember: List[jvm.Code] =
      maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRow).flatMap { (x: FkAnalysis.ExtractFkId) =>
        val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
        val is = {
          val expr = x.colPairs
            .map { case (otherCol, thisCol) => code"${thisCol.name}.isEqual(id.${otherCol.name})" }
            .reduceLeft[jvm.Code] { case (acc, current) => code"$acc.and($current)" }
          code"""|def extract${x.name}Is(id: ${x.otherCompositeIdType}): $predicateType =
                 |  $expr""".stripMargin

        }
        val in = {
          val parts = x.colPairs.map { case (otherCol, thisCol) =>
            val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(thisCol.tpe), thisCol.typoType)
            code"${lang.dsl.CompositeTuplePart.of(x.otherCompositeIdType)}(${thisCol.name})(_.${otherCol.name})(using ${dbLib.resolveConstAs(arrayTypoType)}, implicitly)"
          }
          code"""|def extract${x.name}In(ids: ${jvm.Type.ArrayOf(x.otherCompositeIdType)}): $predicateType =
                 |  new ${lang.dsl.CompositeIn}(ids)(${parts.mkCode(", ")})
                 |""".stripMargin
        }

        List(is, in)

      }
    val compositeIdMembers: List[jvm.Code] =
      names.maybeId
        .collect {
          case x: IdComputed.Composite if x.cols.forall(_.dbCol.nullability == Nullability.NoNulls) =>
            val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
            val is = {
              val id = x.paramName
              val expr = x.cols
                .map(col => code"${col.name}.isEqual($id.${col.name})")
                .toList
                .reduceLeft[jvm.Code] { case (acc, current) => code"$acc.and($current)" }
              code"""|def compositeIdIs($id: ${x.tpe}): $predicateType =
                     |  $expr""".stripMargin

            }
            val in = {
              val ids = x.paramName.appended("s")
              val parts =
                x.cols.map { col =>
                  val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                  code"${lang.dsl.CompositeTuplePart.of(x.tpe)}(${col.name})(_.${col.name})(using ${dbLib.resolveConstAs(arrayTypoType)}, implicitly)"
                }
              code"""|def compositeIdIn($ids: ${jvm.Type.ArrayOf(x.tpe)}): $predicateType =
                     |  new ${lang.dsl.CompositeIn}($ids)(${parts.mkCode(", ")})
                     |""".stripMargin
            }

            List(is, in)
        }
        .getOrElse(Nil)

    val ImplName = jvm.Ident("Impl")

    val members =
      cols
        .map { col =>
          val (cls, tpe) =
            if (names.isIdColumn(col.dbName)) (lang.dsl.IdField, col.tpe)
            else
              col.tpe match {
                case lang.Optional(underlying) => (lang.dsl.OptField, underlying)
                case _                         => (lang.dsl.Field, col.tpe)
              }

          val readSqlCast = sqlCast.fromPg(col.dbCol.tpe) match {
            case Some(cast) => lang.Optional.some(jvm.StrLit(cast.typeName))
            case None       => lang.Optional.none
          }
          val writeSqlCast = sqlCast.toPg(col.dbCol) match {
            case Some(cast) => lang.Optional.some(jvm.StrLit(cast.typeName))
            case None       => lang.Optional.none
          }
          code"override def ${col.name} = ${cls.of(tpe, names.RowName)}(_path, ${jvm.StrLit(col.dbName.value)}, $readSqlCast, $writeSqlCast, x => x.${col.name}, (row, value) => row.copy(${col.name} = value))"
        }
        .mkCode("\n")

    val pathList = lang.ListType.tpe.of(lang.dsl.Path)
    val generalizedColumn = lang.dsl.FieldLikeNoHkt.of(jvm.Type.Wildcard, names.RowName)
    val columnsList = lang.ListType.tpe.of(generalizedColumn)

    val str =
      code"""|trait ${fieldsName.name} {
             |  ${(colMembers ++ fkMembers ++ compositeIdMembers ++ extractFkMember).mkCode("\n")}
             |}
             |
             |object ${fieldsName.name} {
             |  lazy val structure: ${lang.dsl.StructureRelation}[$fieldsName, ${names.RowName}] =
             |    new $ImplName(${lang.ListType.create(Nil)})
             |
             |  private final class $ImplName(val _path: $pathList)
             |    extends ${lang.dsl.StructureRelation.of(fieldsName, names.RowName)} {
             |
             |    override lazy val fields: ${fieldsName.name} = new ${fieldsName.name} {
             |      $members
             |    }
             |
             |    override lazy val columns: $columnsList =
             |      $columnsList(${cols.map(x => code"fields.${x.name}").mkCode(", ")})
             |
             |    override def copy(path: $pathList): $ImplName =
             |      new $ImplName(path)
             |  }
             |}
             |""".stripMargin

    jvm.File(fieldsName, str, Nil, scope = Scope.Main)
  }

  def RepoTraitFile(dbLib: DbLib, repoMethods: NonEmptyList[RepoMethod]): jvm.File = {
    val maybeSignatures = repoMethods.toList.map(dbLib.repoSig)

    val cls = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(maybeSignatures.collect { case Left(DbLib.NotImplementedFor(repoMethod, lib)) =>
        s"${repoMethod.methodName}: Not implementable for $lib"
      }),
      classType = jvm.ClassType.Interface,
      name = names.RepoName,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = maybeSignatures.collect { case Right(signature) => signature },
      staticMembers = Nil
    )

    jvm.File(names.RepoName, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  def RepoImplFile(dbLib: DbLib, repoMethods: NonEmptyList[RepoMethod]): jvm.File = {
    val methods: List[jvm.Method] =
      repoMethods.toList.flatMap { repoMethod =>
        dbLib.repoSig(repoMethod) match {
          case Right(sig @ jvm.Method(_, _, _, _, _, _, _, _, jvm.Body.Abstract, _, _)) =>
            Some(sig.copy(body = dbLib.repoImpl(repoMethod), isOverride = true))
          case _ =>
            None
        }
      }
    val cls = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      classType = jvm.ClassType.Class,
      name = names.RepoImplName,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(names.RepoName),
      members = methods,
      staticMembers = Nil
    )

    jvm.File(names.RepoImplName, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  def RepoMockFile(dbLib: DbLib, idComputed: IdComputed, repoMethods: NonEmptyList[RepoMethod]): jvm.File = {
    val maybeToRowParam: Option[jvm.Param[Type.Function1]] = {
      repoMethods.toList.collectFirst { case RepoMethod.InsertUnsaved(_, _, unsaved, _, _, _, _) =>
        jvm.Param(jvm.Ident("toRow"), jvm.Type.Function1(unsaved.tpe, names.RowName))
      }
    }

    val methods: List[jvm.Method] =
      repoMethods.toList.flatMap { repoMethod =>
        dbLib.repoSig(repoMethod) match {
          case Right(sig @ jvm.Method(_, _, _, _, _, _, _, _, jvm.Body.Abstract, _, _)) =>
            Some(sig.copy(body = dbLib.mockRepoImpl(idComputed, repoMethod, maybeToRowParam), isOverride = true))
          case _ =>
            None
        }
      }

    val classParams = List(
      maybeToRowParam,
      Some(
        jvm.Param(
          Nil,
          jvm.Comments.Empty,
          jvm.Ident("map"),
          dbLib.lang.MapOps.mutableImpl.of(idComputed.tpe, names.RowName),
          Some(dbLib.lang.MapOps.newMutableMap(idComputed.tpe, names.RowName))
        )
      )
    ).flatten

    val cls = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      comments = jvm.Comments.Empty,
      name = names.RepoMockName,
      tparams = Nil,
      params = classParams,
      implicitParams = Nil,
      `extends` = None,
      implements = List(names.RepoName),
      members = methods,
      staticMembers = Nil
    )
    jvm.File(names.RepoMockName, cls, secondaryTypes = Nil, scope = Scope.Test)
  }
}
