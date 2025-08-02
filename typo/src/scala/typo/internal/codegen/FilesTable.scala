package typo
package internal
package codegen

import play.api.libs.json.Json
import typo.internal.codegen.DbLib.RowType
import typo.internal.metadb.OpenEnum

case class FilesTable(table: ComputedTable, fkAnalysis: FkAnalysis, options: InternalOptions, genOrdering: GenOrdering, domainsByName: Map[db.RelationName, ComputedDomain]) {
  val relation = FilesRelation(table.naming, table.names, Some(table.cols), Some(fkAnalysis), options, table.dbTable.foreignKeys)
  val RowFile = relation.RowFile(RowType.ReadWriteable, table.dbTable.comment, maybeUnsavedRow = table.maybeUnsavedRow.map(u => (u, table.default)))

  val UnsavedRowFile: Option[sc.File] =
    for {
      unsaved <- table.maybeUnsavedRow
      rowFile <- RowFile
    } yield {
      val comments = scaladoc(s"This class corresponds to a row in table `${table.dbTable.name.value}` which has not been persisted yet")(Nil)

      val toRow: sc.Code = {
        def mkDefaultParamName(col: ComputedColumn): sc.Ident =
          sc.Ident(col.name.value).appended("Default")

        val params: List[sc.Param] =
          unsaved.defaultCols.map { case (col, originalType) => sc.Param(mkDefaultParamName(col), sc.Type.ByName(originalType), None) } ++
            unsaved.alwaysGeneratedCols.map(col => sc.Param(mkDefaultParamName(col), sc.Type.ByName(col.tpe), None))

        val keyValues1 =
          unsaved.defaultCols.map { case (col, _) =>
            val defaultParamName = mkDefaultParamName(col)
            val impl = code"""|${col.name} match {
                   |  case ${table.default.Defaulted}.${table.default.UseDefault} => $defaultParamName
                   |  case ${table.default.Defaulted}.${table.default.Provided}(value) => value
                   |}""".stripMargin
            (col.name, impl)
          } ++
            unsaved.alwaysGeneratedCols.map { col =>
              val defaultParamName = mkDefaultParamName(col)
              (col.name, defaultParamName.code)
            }

        val keyValues2 =
          unsaved.restCols.map { col =>
            (col.name, sc.QIdent.of(col.name).code)
          }

        val keyValues: List[(sc.Ident, sc.Code)] =
          keyValues2 ::: keyValues1.toList

        code"""|def toRow(${params.map(_.code).mkCode(", ")}): ${table.names.RowName} =
             |  ${table.names.RowName}(
             |    ${keyValues.map { case (k, v) => code"$k = $v" }.mkCode(",\n")}
             |  )""".stripMargin
      }

      val formattedCols = unsaved.unsavedCols.map { col =>
        val commentPieces = List[Iterable[String]](
          col.dbCol.columnDefault.map(x => s"Default: $x"),
          col.dbCol.maybeGenerated.map(_.asString),
          col.dbCol.comment,
          col.pointsTo map { case (relationName, columnName) =>
            val shortened = sc.QIdent(relation.dropCommonPrefix(table.naming.rowName(relationName).idents, rowFile.tpe.value.idents))
            s"Points to [[${shortened.dotName}.${table.naming.field(columnName).value}]]"
          },
          col.dbCol.constraints.map(c => s"Constraint ${c.name} affecting columns ${c.columns.map(_.value).mkString(", ")}:  ${c.checkClause}"),
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

        val default = col.dbCol.columnDefault match {
          case Some(_) => code" = ${table.default.Defaulted}.${table.default.UseDefault}"
          case None    => sc.Code.Empty
        }
        code"$comment${col.param.code}$default"
      }

      val instances =
        options.jsonLibs.flatMap(_.instances(unsaved.tpe, unsaved.unsavedCols)) ++
          options.dbLib.toList.flatMap(_.rowInstances(unsaved.tpe, unsaved.unsavedCols, rowType = DbLib.RowType.Writable))

      sc.File(
        unsaved.tpe,
        code"""|$comments
             |case class ${unsaved.tpe.name}(
             |  ${formattedCols.mkCode(",\n")}
             |) {
             |  $toRow
             |}
             |${genObject(unsaved.tpe.value, instances)}
             |""".stripMargin,
        secondaryTypes = Nil,
        scope = Scope.Main
      )
    }

  val IdFile: Option[sc.File] = {
    table.maybeId.flatMap {
      case id: IdComputed.UnaryNormal =>
        val value = sc.Ident("value")
        val comments = scaladoc(s"Type for the primary key of table `${table.dbTable.name.value}`")(Nil)
        val bijection =
          if (options.enableDsl)
            Some {
              val thisBijection = sc.Type.dsl.Bijection.of(id.tpe, id.underlying)
              sc.Given(Nil, sc.Ident("bijection"), Nil, thisBijection, code"$thisBijection(_.$value)(${id.tpe}.apply)")
            }
          else None

        // shortcut for id files wrapping a domain
        val maybeFromString: Option[sc.Value] =
          id.col.dbCol.tpe match {
            case db.Type.DomainRef(name, _, _) =>
              domainsByName.get(name).map { domain =>
                val name = domain.underlying.constraintDefinition match {
                  case Some(_) => domain.tpe.name.map(Naming.camelCase)
                  case None    => sc.Ident("apply")
                }
                sc.Value(Nil, name, List(sc.Param(value, domain.underlyingType, None)), Nil, id.tpe, code"${id.tpe}(${domain.tpe}($value))")
              }
            case _ => None
          }

        val instances = List(
          List(
            genOrdering.ordering(id.tpe, NonEmptyList(sc.Param(value, id.underlying, None)))
          ),
          bijection.toList,
          options.jsonLibs.flatMap(_.wrapperTypeInstances(wrapperType = id.tpe, fieldName = value, underlying = id.underlying)),
          options.dbLib.toList.flatMap(_.wrapperTypeInstances(wrapperType = id.tpe, underlying = id.underlying, overrideDbType = None))
        ).flatten
        Some(
          sc.File(
            id.tpe,
            code"""|$comments
                   |case class ${id.tpe.name}($value: ${id.underlying}) extends AnyVal
                   |${genObject(id.tpe.value, instances ++ maybeFromString)}
                   |""".stripMargin,
            secondaryTypes = Nil,
            scope = Scope.Main
          )
        )
      case x: IdComputed.UnaryOpenEnum =>
        val comments = scaladoc(s"Type for the primary key of table `${table.dbTable.name.value}`. It has some known values: ")(x.openEnum.values.toList.map { v => " - " + v })
        val Underlying: sc.Type.Qualified =
          x.openEnum match {
            case OpenEnum.Text(_) =>
              TypesJava.String
            case OpenEnum.TextDomain(domainRef, _) =>
              sc.Type.Qualified(options.naming.domainName(domainRef.name))
          }
        val underlying = sc.Ident("underlying")

        val members = x.openEnum.values.map { value =>
          val name = options.naming.enumValue(value)
          x.openEnum match {
            case OpenEnum.Text(_) =>
              (name, code"case object $name extends ${x.tpe.name}(${sc.StrLit(value)})")
            case OpenEnum.TextDomain(_, _) =>
              (name, code"case object $name extends ${x.tpe.name}(${Underlying}(${sc.StrLit(value)}))")
          }
        }

        // shortcut for id files wrapping a domain
        val maybeFromString: Option[sc.Value] =
          x.openEnum match {
            case OpenEnum.Text(_) => None
            case OpenEnum.TextDomain(db.Type.DomainRef(name, _, _), _) =>
              domainsByName.get(name).map { domain =>
                val name = domain.underlying.constraintDefinition match {
                  case Some(_) => domain.tpe.name.map(Naming.camelCase)
                  case None    => sc.Ident("apply")
                }
                val value = sc.Ident("value")
                sc.Value(Nil, name, List(sc.Param(value, domain.underlyingType, None)), Nil, x.tpe, code"${x.tpe}(${domain.tpe}($value))")
              }
          }

        val sqlType = x.openEnum match {
          case OpenEnum.Text(_)                  => "text"
          case OpenEnum.TextDomain(domainRef, _) => domainRef.name.quotedValue
        }

        val instances = List(
          options.dbLib.toList.flatMap(_.stringEnumInstances(x.tpe, x.underlying, sqlType, openEnum = true)),
          options.jsonLibs.flatMap(_.stringEnumInstances(x.tpe, x.underlying, openEnum = true)),
          List(
            genOrdering.ordering(x.tpe, NonEmptyList(sc.Param(sc.Ident("value"), TypesJava.String, None)))
          )
        ).flatten

        val obj = genObject.withBody(x.tpe.value, instances)(
          code"""|def apply($underlying: $Underlying): ${x.tpe} =
                 |  ByName.getOrElse($underlying, Unknown($underlying))
                 |${(maybeFromString.map(_.code).toList ++ members.toList.map { case (_, definition) => definition }).mkCode("\n")}
                 |case class Unknown(override val value: $Underlying) extends ${x.tpe}(value)
                 |val All: ${TypesScala.List.of(x.tpe)} = ${TypesScala.List}(${members.map { case (ident, _) => ident.code }.mkCode(", ")})
                 |val ByName: ${TypesScala.Map.of(Underlying, x.tpe)} = All.map(x => (x.value, x)).toMap
            """.stripMargin
        )
        val body =
          code"""|$comments
                 |sealed abstract class ${x.tpe.name}(val value: ${Underlying})
                 |
                 |$obj
                 |""".stripMargin

        Some(sc.File(x.tpe, body, secondaryTypes = Nil, scope = Scope.Main))

      case _: IdComputed.UnaryUserSpecified | _: IdComputed.UnaryNoIdType | _: IdComputed.UnaryInherited =>
        None
      case id @ IdComputed.Composite(cols, tpe, _) =>
        val constructorMethod = fkAnalysis.createWithFkIdsId.map { colsFromFks =>
          val body =
            code"""|${tpe.name}(
                   |  ${colsFromFks.allExpr.map { case (colName, expr) => code"$colName = $expr" }.mkCode(",\n")}
                   |)""".stripMargin

          sc.Value(Nil, sc.Ident("from"), colsFromFks.allParams, Nil, tpe, body)
        }
        val instanceMethods: List[sc.Value] =
          fkAnalysis.extractFksIdsFromId.map { colsToFk =>
            val args = colsToFk.colPairs.map { case (inComposite, inId) => code"${inComposite.name} = ${inId.name}" }

            val body =
              code"""|${colsToFk.otherCompositeIdType}(
                       |  ${args.mkCode(",\n")}
                      |)""".stripMargin

            sc.Value(Nil, colsToFk.name.prepended("extract"), Nil, Nil, colsToFk.otherCompositeIdType, body)
          }
        val renderedInstanceMethods = instanceMethods match {
          case Nil => sc.Code.Empty
          case nonEmpty =>
            code"""|{
                   |${nonEmpty.map(_.code).mkCode("\n")}
                   |}""".stripMargin
        }

        val comments = scaladoc(s"Type for the composite primary key of table `${table.dbTable.name.value}`")(Nil)
        val instances = List(
          List(genOrdering.ordering(id.tpe, cols.map(cc => sc.Param(cc.param.name, cc.tpe, None)))),
          options.jsonLibs.flatMap(_.instances(tpe = id.tpe, cols = cols))
        ).flatten
        Some(
          sc.File(
            id.tpe,
            code"""|$comments
                   |case class ${tpe.name}(
                   |  ${cols.map(_.param.code).mkCode(",\n")}
                   |)$renderedInstanceMethods
                   |${genObject(tpe.value, instances ++ constructorMethod)}
                   |""".stripMargin,
            secondaryTypes = Nil,
            scope = Scope.Main
          )
        )
    }
  }

  private val maybeMockRepo: Option[sc.File] =
    if (options.generateMockRepos.include(table.dbTable.name))
      for {
        id <- table.maybeId
        repoMethods <- table.repoMethods
        dbLib <- options.dbLib
      } yield relation.RepoMockFile(dbLib, id, repoMethods)
    else None

  val all: List[sc.File] = List(
    RowFile,
    relation.FieldsFile,
    UnsavedRowFile,
    for {
      repoMethods <- table.repoMethods
      dbLib <- options.dbLib
    } yield relation.RepoTraitFile(dbLib, repoMethods),
    for {
      repoMethods <- table.repoMethods
      dbLib <- options.dbLib
    } yield relation.RepoImplFile(dbLib, repoMethods),
    relation.FieldValueFile,
    maybeMockRepo,
    IdFile
  ).flatten
}
