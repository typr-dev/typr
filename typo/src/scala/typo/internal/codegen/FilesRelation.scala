package typo
package internal
package codegen

import play.api.libs.json.Json
import typo.internal.compat.*

case class FilesRelation(
    naming: Naming,
    names: ComputedNames,
    maybeCols: Option[NonEmptyList[ComputedColumn]],
    maybeFkAnalysis: Option[FkAnalysis],
    options: InternalOptions,
    fks: List[db.ForeignKey]
) {
  def RowFile(rowType: DbLib.RowType, comment: Option[String], maybeUnsavedRow: Option[(ComputedRowUnsaved, ComputedDefault)]): Option[sc.File] =
    maybeCols.map { cols =>
      val members = List[Iterable[sc.Code]](
        names.maybeId.collect { case x: IdComputed.Composite =>
          code"""val ${x.paramName}: ${x.tpe} = ${x.tpe}(${x.cols.map(x => x.name.code).mkCode(", ")})"""
        },
        // id member which points to either `compositeId` val defined above or id column
        if (maybeCols.exists(_.exists(_.name.value == "id"))) None
        else
          names.maybeId.collect {
            case id: IdComputed.Unary     => code"val id = ${id.col.name}"
            case id: IdComputed.Composite => code"val id = ${id.paramName}"
          },
        maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRowNotId).map { extractFkId =>
          val args = extractFkId.colPairs.map { case (inComposite, inId) => code"${inComposite.name} = ${inId.name}" }

          val body =
            code"""|${extractFkId.otherCompositeIdType}(
                     |  ${args.mkCode(",\n")}
                     |)""".stripMargin

          sc.Value(Nil, extractFkId.name.prepended("extract"), Nil, Nil, extractFkId.otherCompositeIdType, body).code
        },
        maybeUnsavedRow.map { case (unsaved, defaults) =>
          val (partOfId, rest) = unsaved.defaultedCols.toList.partition { case ComputedRowUnsaved.DefaultedCol(col, _) => names.isIdColumn(col.dbName) }
          val partOfIdParams = partOfId.map { case ComputedRowUnsaved.DefaultedCol(col, _) => sc.Param(col.name, col.tpe, None) }
          val restParams = rest.map { case ComputedRowUnsaved.DefaultedCol(col, _) =>
            sc.Param(col.name, col.tpe, Some(code"${defaults.Defaulted}.${defaults.Provided}(this.${col.name})"))
          }
          val params = partOfIdParams ++ restParams
          code"""|def toUnsavedRow(${params.map(_.code).mkCode(", ")}): ${unsaved.tpe} =
                 |  ${unsaved.tpe}(${unsaved.unsavedCols.map(col => col.name.code).mkCode(", ")})""".stripMargin
        }
      )
      val formattedMembers = members.flatten match {
        case Nil => sc.Code.Empty
        case nonEmpty =>
          code"""|{
                 |  ${nonEmpty.mkCode("\n")}
                 |}""".stripMargin
      }
      val formattedCols = cols.map { col =>
        val commentPieces = List[Iterable[String]](
          col.dbCol.comment,
          col.dbCol.columnDefault.map(x => s"Default: $x"),
          col.dbCol.maybeGenerated.map(_.asString),
          col.pointsTo.map { case (relationName, columnName) =>
            val rowName = naming.rowName(relationName)
            s"Points to [[${rowName.dotName}.${naming.field(columnName).value}]]"
          },
          col.dbCol.constraints.map(c => s"Constraint ${c.name} affecting columns ${c.columns.map(_.value).mkString(", ")}: ${c.checkClause}"),
          if (options.debugTypes)
            col.dbCol.jsonDescription.maybeJson.map(other => s"debug: ${Json.stringify(other)}")
          else None
        ).flatten

        val comment = commentPieces match {
          case Nil => sc.Code.Empty
          case nonEmpty =>
            val lines = nonEmpty.flatMap(_.linesIterator).map(_.code)
            code"""|/** ${lines.mkCode("\n")} */
                 |""".stripMargin
        }

        code"$comment${col.param.code}"
      }
      val instances =
        options.jsonLibs.flatMap(_.instances(names.RowName, cols)) ++
          options.dbLib.toList.flatMap(_.rowInstances(names.RowName, cols, rowType = rowType))

      val classComment = {
        val lines = List[Iterable[String]](
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
        code"""|/** ${lines.mkString("\n")} */
             |""".stripMargin
      }

      val maybeExtraApply: Option[sc.Code] =
        names.maybeId.collect { case id: IdComputed.Composite =>
          val nonKeyColumns = cols.toList.filter(col => !names.isIdColumn(col.dbCol.name))
          val params = sc.Param(id.paramName, id.tpe, None) :: nonKeyColumns.map(col => sc.Param(col.name, col.tpe, None))
          val args = cols.map { col => if (names.isIdColumn(col.dbCol.name)) code"${id.paramName}.${col.name}" else col.name.code }
          code"""|def apply(${params.map(_.code).mkCode(", ")}) =
               |  new ${names.RowName}(${args.map(_.code).mkCode(", ")})""".stripMargin
        }

      val str =
        code"""${classComment}case class ${names.RowName.name}(
            |  ${formattedCols.mkCode(",\n")}
            |)$formattedMembers
            |
            |${sc.Obj(names.RowName.value, instances, maybeExtraApply)}
            |""".stripMargin

      sc.File(names.RowName, str, secondaryTypes = Nil, scope = Scope.Main)
    }

  val FieldValueFile: Option[sc.File] =
    for {
      fieldOrIdValueName <- names.FieldOrIdValueName
      fieldValueName <- names.FieldValueName
      cols <- maybeCols
    } yield {
      val members = {
        cols.map { col =>
          val parent = if (names.isIdColumn(col.dbName)) fieldOrIdValueName else fieldValueName
          code"case class ${col.name}(override val value: ${col.tpe}) extends $parent(${sc.StrLit(col.dbName.value)}, value)"
        }
      }
      val str =
        code"""sealed abstract class ${fieldOrIdValueName.name}[T](val name: String, val value: T)
              |sealed abstract class ${fieldValueName.name}[T](name: String, value: T) extends ${fieldOrIdValueName.name}(name, value)
              |
              |${sc.Obj(fieldValueName.value, Nil, Some(members.mkCode("\n")))}
              |""".stripMargin

      sc.File(fieldValueName, str, secondaryTypes = List(fieldOrIdValueName), scope = Scope.Main)
    }

  val FieldsFile: Option[sc.File] =
    for {
      fieldsName <- names.FieldsName
      cols <- maybeCols
    } yield {
      val colMembers = cols.map { col =>
        val (cls, tpe) =
          if (names.isIdColumn(col.dbName)) (sc.Type.dsl.IdField, col.tpe)
          else
            col.tpe match {
              case TypesScala.Optional(underlying) => (sc.Type.dsl.OptField, underlying)
              case _                               => (sc.Type.dsl.Field, col.tpe)
            }
        code"def ${col.name}: ${cls.of(tpe, names.RowName)}"
      }

      val fkMembers: List[sc.Code] =
        names.source match {
          case relation: Source.Relation =>
            val byOtherTable = fks.groupBy(_.otherTable)
            fks
              .sortBy(_.constraintName.value)
              .map { fk =>
                val otherTableSource = Source.Table(fk.otherTable)
                val fkType = sc.Type.dsl.ForeignKey.of(
                  sc.Type.Qualified(naming.fieldsName(otherTableSource)),
                  sc.Type.Qualified(naming.rowName(otherTableSource))
                )
                val columnPairs = fk.cols.zip(fk.otherCols).map { case (col, otherCol) =>
                  code".withColumnPair(${naming.field(col)}, _.${naming.field(otherCol)})"
                }
                val fkName = naming.fk(relation.name, fk, includeCols = byOtherTable(fk.otherTable).size > 1)
                val body =
                  code"""|def $fkName: $fkType =
                       |  $fkType(${sc.StrLit(fk.constraintName.value)}, Nil)
                       |    ${columnPairs.mkCode("\n")}""".stripMargin
                (fkName, body)
              }
              .distinctByCompat(_._1)
              .map(_._2)
          case Source.SqlFile(_) => Nil
        }

      val extractFkMember: List[sc.Code] =
        maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRow).flatMap { (x: FkAnalysis.ExtractFkId) =>
          val predicateType = sc.Type.dsl.SqlExpr.of(TypesScala.Boolean)
          val is = {
            val expr = x.colPairs
              .map { case (otherCol, thisCol) => code"${thisCol.name}.isEqual(id.${otherCol.name})" }
              .reduceLeft[sc.Code] { case (acc, current) => code"$acc.and($current)" }
            code"""|def extract${x.name}Is(id: ${x.otherCompositeIdType}): $predicateType =
                   |  $expr""".stripMargin

          }
          val in = {
            val parts = x.colPairs.map { case (otherCol, thisCol) =>
              code"${sc.Type.dsl.CompositeTuplePart.of(x.otherCompositeIdType)}(${thisCol.name})(_.${otherCol.name})(using ${options.dbLib.get.resolveConstAs(sc.Type.ArrayOf(thisCol.tpe))}, implicitly)"
            }
            code"""|def extract${x.name}In(ids: ${sc.Type.ArrayOf(x.otherCompositeIdType)}): $predicateType =
                   |  new ${sc.Type.dsl.CompositeIn}(ids)(${parts.mkCode(", ")})
                   |""".stripMargin
          }

          List(is, in)

        }
      val compositeIdMembers: List[sc.Code] =
        names.maybeId
          .collect {
            case x: IdComputed.Composite if x.cols.forall(_.dbCol.nullability == Nullability.NoNulls) =>
              val predicateType = sc.Type.dsl.SqlExpr.of(TypesScala.Boolean)
              val is = {
                val id = x.paramName
                val expr = x.cols
                  .map(col => code"${col.name}.isEqual($id.${col.name})")
                  .toList
                  .reduceLeft[sc.Code] { case (acc, current) => code"$acc.and($current)" }
                code"""|def compositeIdIs($id: ${x.tpe}): $predicateType =
                       |  $expr""".stripMargin

              }
              val in = {
                val ids = x.paramName.appended("s")
                val parts =
                  x.cols.map(col => code"${sc.Type.dsl.CompositeTuplePart.of(x.tpe)}(${col.name})(_.${col.name})(using ${options.dbLib.get.resolveConstAs(sc.Type.ArrayOf(col.tpe))}, implicitly)")
                code"""|def compositeIdIn($ids: ${sc.Type.ArrayOf(x.tpe)}): $predicateType =
                       |  new ${sc.Type.dsl.CompositeIn}($ids)(${parts.mkCode(", ")})
                       |""".stripMargin
              }

              List(is, in)
          }
          .getOrElse(Nil)

      val ImplName = sc.Ident("Impl")
      val str =
        code"""|trait ${fieldsName.name} {
               |  ${(colMembers ++ fkMembers ++ compositeIdMembers ++ extractFkMember).mkCode("\n")}
               |}
               |
               |object ${fieldsName.name} {
               |  lazy val structure: ${sc.Type.dsl.StructureRelation}[$fieldsName, ${names.RowName}] =
               |    new $ImplName(${TypesScala.Nil})
               |
               |  ${structureFile(ImplName, fieldsName, cols)}
               |}
               |""".stripMargin

      sc.File(fieldsName, str, Nil, scope = Scope.Main)
    }

  def structureFile(implName: sc.Ident, fieldsName: sc.Type.Qualified, cols: NonEmptyList[ComputedColumn]) = {
    val members =
      cols
        .map { col =>
          val (cls, tpe) =
            if (names.isIdColumn(col.dbName)) (sc.Type.dsl.IdField, col.tpe)
            else
              col.tpe match {
                case TypesScala.Optional(underlying) => (sc.Type.dsl.OptField, underlying)
                case _                               => (sc.Type.dsl.Field, col.tpe)
              }

          val readSqlCast = SqlCast.fromPg(col.dbCol.tpe) match {
            case Some(sqlCast) => code"${TypesScala.Some}(${sc.StrLit(sqlCast.typeName)})"
            case None          => TypesScala.None.code
          }
          val writeSqlCast = SqlCast.toPg(col.dbCol) match {
            case Some(sqlCast) => code"${TypesScala.Some}(${sc.StrLit(sqlCast.typeName)})"
            case None          => TypesScala.None.code
          }
          code"override def ${col.name} = ${cls.of(tpe, names.RowName)}(_path, ${sc.StrLit(col.dbName.value)}, $readSqlCast, $writeSqlCast, x => x.${col.name}, (row, value) => row.copy(${col.name} = value))"
        }
        .mkCode("\n")

    val pathList = TypesScala.List.of(sc.Type.dsl.Path)
    val generalizedColumn = sc.Type.dsl.FieldLikeNoHkt.of(sc.Type.Wildcard, names.RowName)
    val columnsList = TypesScala.List.of(generalizedColumn)
    code"""private final class $implName(val _path: $pathList)
          |  extends ${sc.Type.dsl.StructureRelation.of(fieldsName, names.RowName)} {
          |
          |  override lazy val fields: ${fieldsName.name} = new ${fieldsName.name} {
          |    $members
          |  }
          |
          |  override lazy val columns: $columnsList =
          |    $columnsList(${cols.map(x => code"fields.${x.name}").mkCode(", ")})
          |
          |  override def copy(path: $pathList): $implName =
          |    new $implName(path)
          |}
          |""".stripMargin
  }

  def RepoTraitFile(dbLib: DbLib, repoMethods: NonEmptyList[RepoMethod]): sc.File = {
    val renderedMethods = repoMethods.map { repoMethod =>
      dbLib.repoSig(repoMethod) match {
        case Left(DbLib.NotImplementedFor(lib)) => code"// Not implementable for $lib: ${repoMethod.methodName}"
        case Right(sig)                         => code"${repoMethod.comment.fold("")(c => c + "\n")}$sig"
      }
    }
    val str =
      code"""trait ${names.RepoName.name} {
            |  ${renderedMethods.mkCode("\n")}
            |}
            |""".stripMargin

    sc.File(names.RepoName, str, secondaryTypes = Nil, scope = Scope.Main)
  }

  def RepoImplFile(dbLib: DbLib, repoMethods: NonEmptyList[RepoMethod]): sc.File = {
    val renderedMethods: List[sc.Code] = repoMethods.toList.flatMap { repoMethod =>
      dbLib.repoSig(repoMethod).toOption.map { sig =>
        code"""|${repoMethod.comment.fold("")(c => c + "\n")}override $sig = {
               |  ${dbLib.repoImpl(repoMethod)}
               |}""".stripMargin
      }
    }
    val str =
      code"""|class ${names.RepoImplName.name} extends ${names.RepoName} {
             |  ${renderedMethods.mkCode("\n")}
             |}
             |""".stripMargin

    sc.File(names.RepoImplName, str, secondaryTypes = Nil, scope = Scope.Main)
  }

  def RepoMockFile(dbLib: DbLib, idComputed: IdComputed, repoMethods: NonEmptyList[RepoMethod]): sc.File = {
    val maybeToRowParam: Option[sc.Param] =
      repoMethods.toList.collectFirst { case RepoMethod.InsertUnsaved(_, _, unsaved, _, _, _) =>
        sc.Param(sc.Ident("toRow"), TypesScala.Function1.of(unsaved.tpe, names.RowName), None)
      }

    val methods: List[sc.Code] =
      repoMethods.toList.flatMap { repoMethod =>
        dbLib.repoSig(repoMethod).toOption.map { sig =>
          code"""|${repoMethod.comment.fold("")(c => c + "\n")}override $sig = {
                 |  ${dbLib.mockRepoImpl(idComputed, repoMethod, maybeToRowParam)}
                 |}""".stripMargin
        }
      }

    val classParams = List(
      maybeToRowParam,
      Some(
        sc.Param(
          sc.Ident("map"),
          TypesScala.mutableMap.of(idComputed.tpe, names.RowName),
          Some(code"${TypesScala.mutableMap}.empty")
        )
      )
    ).flatten

    val str =
      code"""|class ${names.RepoMockName.name}(${classParams.map(_.code).mkCode(",\n")}) extends ${names.RepoName} {
             |  ${methods.mkCode("\n")}
             |}
             |""".stripMargin

    sc.File(names.RepoMockName, str, secondaryTypes = Nil, scope = Scope.Test)
  }
}
