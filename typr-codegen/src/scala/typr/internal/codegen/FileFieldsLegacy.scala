package typr
package internal
package codegen

import typr.internal.compat.ListOps

class FileFieldsLegacy(
    val lang: Lang,
    val naming: Naming,
    val names: ComputedNames,
    val options: InternalOptions,
    val fks: List[db.ForeignKey],
    val maybeFkAnalysis: Option[FkAnalysis],
    val maybeCols: Option[NonEmptyList[ComputedColumn]],
    dbLib: DbLib
) extends FileFields {

  override protected val sqlCast: SqlCast = new SqlCast(needsTimestampCasts = true)

  override def generate(fieldsName: jvm.Type.Qualified, cols: NonEmptyList[ComputedColumn]): jvm.File = {
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

    val fkMembers = foreignKeyMembers
    val compositeIdMembers = compositeIdCodeMembers
    val extractFkMembers = extractFkCodeMembers
    val implMembers = implMemberCode(cols)

    val pathList = lang.ListType.tpe.of(lang.dsl.Path)
    val generalizedColumn = lang.dsl.FieldLikeNoHkt.of(jvm.Type.Wildcard, names.RowName)
    val columnsList = lang.ListType.tpe.of(generalizedColumn)
    val ImplName = jvm.Ident("Impl")

    val str =
      code"""|trait ${fieldsName.name} {
             |  ${(colMembers ++ fkMembers ++ compositeIdMembers ++ extractFkMembers).mkCode("\n")}
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
             |      $implMembers
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

  private def foreignKeyMembers: List[jvm.Code] =
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

  private def compositeIdCodeMembers: List[jvm.Code] =
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
            val fieldExprs = x.cols.map(col => col.name.code).toList
            val fieldNames = x.cols.map(_.name).toList
            val constAsExprs = if (dbLib.needsConstAsForCompositeIn) {
              x.cols.map { col =>
                val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                dbLib.resolveConstAs(arrayTypoType)
              }.toList
            } else Nil
            val inExpr = dbLib.generateCompositeIn(ids.code, x.tpe, fieldExprs, fieldNames, constAsExprs)
            code"""|def compositeIdIn($ids: ${jvm.Type.ArrayOf(x.tpe)}): $predicateType =
                   |  $inExpr
                   |""".stripMargin
          }
          List(is, in)
      }
      .getOrElse(Nil)

  private def extractFkCodeMembers: List[jvm.Code] =
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
        val ids = jvm.Ident("ids")
        val fieldExprs = x.colPairs.map { case (_, thisCol) => thisCol.name.code }.toList
        val fieldNames = x.colPairs.map { case (otherCol, _) => otherCol.name }.toList
        val constAsExprs = if (dbLib.needsConstAsForCompositeIn) {
          x.colPairs.map { case (_, thisCol) =>
            val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(thisCol.tpe), thisCol.typoType)
            dbLib.resolveConstAs(arrayTypoType)
          }.toList
        } else Nil
        val inExpr = dbLib.generateCompositeIn(ids.code, x.otherCompositeIdType, fieldExprs, fieldNames, constAsExprs)
        code"""|def extract${x.name}In($ids: ${jvm.Type.ArrayOf(x.otherCompositeIdType)}): $predicateType =
               |  $inExpr
               |""".stripMargin
      }
      List(is, in)
    }

  private def implMemberCode(cols: NonEmptyList[ComputedColumn]): jvm.Code =
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
}
