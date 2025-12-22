package typo
package internal
package codegen

import play.api.libs.json.Json
import typo.internal.codegen.DbLib.RowType
import typo.internal.pg.OpenEnum

case class FilesTable(lang: Lang, table: ComputedTable, fkAnalysis: FkAnalysis, options: InternalOptions, domainsByName: Map[db.RelationName, ComputedDomain]) {
  val relation = FilesRelation(lang, table.naming, table.names, Some(table.cols), Some(fkAnalysis), options, table.dbTable.foreignKeys)
  val RowFile = relation.RowFile(RowType.ReadWriteable, table.dbTable.comment, maybeUnsavedRow = table.maybeUnsavedRow.map(u => (u, table.default)))

  val UnsavedRowFile: Option[jvm.File] =
    for {
      unsaved <- table.maybeUnsavedRow
    } yield {
      val comments = scaladoc(List(s"This class corresponds to a row in table `${table.dbTable.name.value}` which has not been persisted yet"))

      val toRow: jvm.Method = {
        def mkDefaultParamName(col: ComputedColumn): jvm.Ident =
          jvm.Ident(col.name.value).appended("Default")

        val params: List[jvm.Param[jvm.Type.Function0]] =
          unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col, originalType, _) => jvm.Param(mkDefaultParamName(col), jvm.Type.Function0(originalType)) } ++
            unsaved.alwaysGeneratedCols.map(col => jvm.Param(mkDefaultParamName(col), jvm.Type.Function0(col.tpe)))

        val keyValues = unsaved.categorizedColumnsOriginalOrder.map {
          case ComputedRowUnsaved.DefaultedCol(col, _, _) =>
            val defaultParamName = mkDefaultParamName(col)
            val impl = code"${col.name}.getOrElse($defaultParamName)"
            jvm.Arg.Named(col.name, impl)
          case ComputedRowUnsaved.AlwaysGeneratedCol(col) =>
            val defaultParamName = mkDefaultParamName(col)
            val c = params.find(_.name == defaultParamName).get
            jvm.Arg.Named(col.name, jvm.Apply0(c))
          case ComputedRowUnsaved.NormalCol(col) =>
            jvm.Arg.Named(col.name, jvm.QIdent.of(col.name).code)
        }

        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("toRow"),
          params = params,
          implicitParams = Nil,
          tpe = table.names.RowName,
          throws = Nil,
          body = jvm.Body.Expr(jvm.New(table.names.RowName, keyValues.toList)),
          isOverride = false,
          isDefault = false
        )
      }

      val jsonInstances = options.jsonLibs.map(_.instances(unsaved.tpe, unsaved.unsavedCols))
      val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
      val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

      val colParams = unsaved.unsavedCols.map { col =>
        col.param.copy(
          annotations = fieldAnnotations.getOrElse(col.name, Nil),
          comments = scaladoc(
            List[Iterable[String]](
              col.dbCol.columnDefault.map(x => s"Default: $x"),
              col.dbCol.maybeGenerated.map(_.asString),
              col.dbCol.comment,
              col.pointsTo map { case (relationName, columnName) => lang.docLink(table.naming.rowName(relationName), table.naming.field(columnName)) },
              col.dbCol.constraints.map(c => s"Constraint ${c.name} affecting columns ${c.columns.map(_.value).mkString(", ")}:  ${c.checkClause}"),
              if (options.debugTypes)
                col.dbCol.jsonDescription.maybeJson.map(other => s"debug: ${Json.stringify(other)}")
              else None
            ).flatten
          ),
          default =
            if (col.dbCol.isDefaulted) Some(jvm.New(jvm.InferredTargs(table.default.Defaulted / table.default.UseDefault), Nil).code)
            else if (lang.Optional.unapply(col.tpe).isDefined) Some(lang.Optional.none)
            else None
        )
      }

      val instances =
        jsonInstances.flatMap(_.givens) ++
          options.dbLib.toList.flatMap(_.rowInstances(unsaved.tpe, unsaved.unsavedCols, rowType = DbLib.RowType.Writable))

      val cls = jvm.Adt.Record(
        annotations = typeAnnotations,
        constructorAnnotations = Nil,
        isWrapper = false,
        comments = comments,
        name = unsaved.tpe,
        tparams = Nil,
        params = colParams.toList,
        implicitParams = Nil,
        `extends` = None,
        implements = Nil,
        members = List(toRow),
        staticMembers = instances
      )

      jvm.File(unsaved.tpe, cls.code, secondaryTypes = Nil, scope = Scope.Main)
    }

  val IdFile: Option[jvm.File] = {
    table.maybeId.flatMap {
      case id: IdComputed.UnaryNormal =>
        val value = jvm.Ident("value")
        val comments = scaladoc(List(s"Type for the primary key of table `${table.dbTable.name.value}`"))
        val bijection =
          if (options.enableDsl)
            Some {
              val thisBijection = lang.dsl.Bijection.of(id.tpe, id.underlying)
              val expr = lang.bijection(id.tpe, id.underlying, jvm.FieldGetterRef(id.tpe, value), jvm.ConstructorMethodRef(id.tpe))
              jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
            }
          else None

        // shortcut for id files wrapping a domain
        val maybeFromString: Option[jvm.Method] =
          id.col.dbCol.tpe match {
            case db.PgType.DomainRef(name, _, _) =>
              domainsByName.get(name).map { domain =>
                val name = domain.underlying.constraintDefinition match {
                  case Some(_) => domain.tpe.name.map(Naming.camelCase)
                  case None    => jvm.Ident("apply")
                }
                jvm.Method(
                  Nil,
                  comments = jvm.Comments.Empty,
                  tparams = Nil,
                  name = name,
                  params = List(jvm.Param(value, domain.underlyingType)),
                  implicitParams = Nil,
                  tpe = id.tpe,
                  throws = Nil,
                  body = jvm.Body.Expr(jvm.New(id.tpe, List(jvm.Arg.Pos(jvm.New(domain.tpe, List(jvm.Arg.Pos(value))))))),
                  isOverride = false,
                  isDefault = false
                )
              }
            case _ => None
          }

        val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = id.tpe, fieldName = value, underlying = id.underlying))
        val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
        val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

        val instances = List(
          bijection.toList,
          jsonInstances.flatMap(_.givens),
          options.dbLib.toList.flatMap(_.wrapperTypeInstances(wrapperType = id.tpe, underlyingJvmType = id.underlying, underlyingDbType = id.col.dbCol.tpe, overrideDbType = None))
        ).flatten

        val paramsWithAnnotations = List(jvm.Param(value, id.underlying)).map { p =>
          fieldAnnotations.get(p.name) match {
            case Some(anns) => p.copy(annotations = p.annotations ++ anns)
            case None       => p
          }
        }

        Some(
          jvm.File(
            id.tpe,
            jvm.Adt.Record(
              annotations = typeAnnotations,
              constructorAnnotations = Nil,
              isWrapper = true,
              comments = comments,
              name = id.tpe,
              tparams = Nil,
              params = paramsWithAnnotations,
              implicitParams = Nil,
              `extends` = None,
              implements = Nil,
              members = Nil,
              staticMembers = instances ++ maybeFromString
            ),
            secondaryTypes = Nil,
            scope = Scope.Main
          )
        )
      case x: IdComputed.UnaryOpenEnum =>
        val comments = scaladoc(s"Type for the primary key of table `${table.dbTable.name.value}`. It has some known values: " +: x.openEnum.values.toList.map { v => " - " + v })

        val (underlyingType, underlyingTypoType, sqlType): (jvm.Type.Qualified, TypoType, String) =
          x.openEnum match {
            case OpenEnum.Text(_) =>
              (lang.String, TypoType.Standard(lang.String, db.PgType.Text), "text")
            case OpenEnum.TextDomain(domainRef, _) =>
              val domainJvmType = jvm.Type.Qualified(options.naming.domainName(domainRef.name))
              (domainJvmType, TypoType.Generated(domainJvmType, domainRef, domainJvmType), domainRef.name.quotedValue)
          }

        val values = x.openEnum.values.map { value =>
          val name = options.naming.enumValue(value)
          x.openEnum match {
            case OpenEnum.Text(_) =>
              (name, jvm.StrLit(value).code)
            case OpenEnum.TextDomain(_, _) =>
              (name, jvm.New(underlyingType, List(jvm.Arg.Pos(jvm.StrLit(value)))).code)
          }
        }

        val instances = List(
          options.dbLib.toList.flatMap(_.stringEnumInstances(x.tpe, underlyingTypoType, sqlType, openEnum = true)),
          options.jsonLibs.flatMap(_.stringEnumInstances(x.tpe, x.underlying, openEnum = true).givens)
        ).flatten

        // shortcut for id files wrapping a domain
        val maybeFromString: Option[jvm.Method] =
          x.openEnum match {
            case OpenEnum.Text(_) => None
            case OpenEnum.TextDomain(db.PgType.DomainRef(name, _, _), _) =>
              domainsByName.get(name).map { domain =>
                val name = domain.underlying.constraintDefinition match {
                  case Some(_) => domain.tpe.name.map(Naming.camelCase)
                  case None    => jvm.Ident("apply")
                }
                val value = jvm.Ident("value")
                jvm.Method(
                  annotations = Nil,
                  comments = jvm.Comments.Empty,
                  tparams = Nil,
                  name = name,
                  params = List(jvm.Param(value, domain.underlyingType)),
                  implicitParams = Nil,
                  tpe = x.tpe,
                  throws = Nil,
                  body = jvm.Body.Expr(code"apply(${jvm.New(x.underlying, List(jvm.Arg.Pos(value)))})"),
                  isOverride = false,
                  isDefault = false
                )
              }
          }

        val xx = jvm.OpenEnum(Nil, comments, x.tpe, underlyingType, values, instances ++ maybeFromString.toList)
        Some(jvm.File(x.tpe, xx, secondaryTypes = Nil, scope = Scope.Main))

      case _: IdComputed.UnaryUserSpecified | _: IdComputed.UnaryNoIdType | _: IdComputed.UnaryInherited =>
        None
      case id @ IdComputed.Composite(cols, tpe, _) =>
        val constructorMethod: Option[jvm.Method] =
          fkAnalysis.createWithFkIdsId.map { colsFromFks =>
            jvm.Method(
              Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = jvm.Ident("from"),
              params = colsFromFks.allParams,
              implicitParams = Nil,
              tpe = tpe,
              throws = Nil,
              body = jvm.Body.Expr(jvm.New(tpe, colsFromFks.allExpr.map { case (colName, expr) => jvm.Arg.Named(colName, expr) })),
              isOverride = false,
              isDefault = false
            )
          }

        val instanceMethods: List[jvm.Method] =
          fkAnalysis.extractFksIdsFromId.map { colsToFk =>
            jvm.Method(
              Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = colsToFk.name.prepended("extract"),
              params = Nil,
              implicitParams = Nil,
              tpe = colsToFk.otherCompositeIdType,
              throws = Nil,
              body = jvm.Body.Expr(jvm.New(colsToFk.otherCompositeIdType, colsToFk.colPairs.map { case (inComposite, inId) => jvm.Arg.Named(inComposite.name, inId.name) })),
              isOverride = false,
              isDefault = false
            )
          }

        val jsonInstances = options.jsonLibs.map(_.instances(tpe = id.tpe, cols = cols))
        val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
        val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)
        val instances: List[jvm.ClassMember] = jsonInstances.flatMap(_.givens)

        // For Oracle with GeneratedKeysIdOnly, composite IDs need a _rowParser to parse generated keys
        val dbLibInstances: List[jvm.ClassMember] =
          options.dbLib.toList.flatMap(_.rowInstances(id.tpe, cols, DbLib.RowType.Readable))

        Some(
          jvm.File(
            id.tpe,
            jvm.Adt.Record(
              annotations = typeAnnotations,
              constructorAnnotations = Nil,
              isWrapper = false,
              comments = scaladoc(List(s"Type for the composite primary key of table `${table.dbTable.name.value}`")),
              name = tpe,
              tparams = Nil,
              params = cols.map { col =>
                val annotations = fieldAnnotations.getOrElse(col.name, Nil)
                col.param.copy(annotations = annotations)
              }.toList,
              implicitParams = Nil,
              `extends` = None,
              implements = Nil,
              members = instanceMethods,
              staticMembers = instances ++ dbLibInstances ++ constructorMethod.toList
            ),
            secondaryTypes = Nil,
            scope = Scope.Main
          )
        )
    }
  }

  private val maybeMockRepo: Option[jvm.File] =
    if (options.generateMockRepos.include(table.dbTable.name))
      for {
        id <- table.maybeId
        repoMethods <- table.repoMethods
        dbLib <- options.dbLib
      } yield relation.RepoMockFile(dbLib, id, repoMethods)
    else None

  val all: List[jvm.File] = List(
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
