package typr
package internal
package codegen

import play.api.libs.json.Json
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

      // For DbLibFoundations (Java DSL), implement TupleN interface if column count <= 100
      // Row keeps its natural types (including Optional) in TupleN
      val colsList = cols.toList
      val (tupleImplements, tupleMethods): (List[jvm.Type], List[jvm.Method]) = options.dbLib match {
        case Some(_: DbLibFoundations) if colsList.size <= 100 =>
          val n = colsList.size
          val colTypes = colsList.map(_.tpe)
          val tupleType = FoundationsTypes.TupleN(n).of(colTypes*)
          val methods = colsList.zipWithIndex.map { case (col, idx) =>
            jvm.Method(
              annotations = Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = jvm.Ident(s"_${idx + 1}"),
              params = Nil,
              implicitParams = Nil,
              tpe = col.tpe,
              throws = Nil,
              body = jvm.Body.Expr(col.name.code),
              isOverride = true,
              isDefault = false
            )
          }
          (List(tupleType), methods)
        case _ => (Nil, Nil)
      }

      jvm.File(
        names.RowName,
        jvm.Adt.Record(
          annotations = typeAnnotations,
          constructorAnnotations = Nil,
          isWrapper = false,
          privateConstructor = false,
          comments = classComment,
          name = names.RowName,
          tparams = Nil,
          params = commentedParams.toList,
          implicitParams = Nil,
          `extends` = None,
          implements = tupleImplements,
          members = members ++ tupleMethods,
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
          privateConstructor = false,
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
    } yield {
      val fileFields = FileFields(lang, naming, names, options, fks, maybeFkAnalysis, maybeCols, dbLib)
      fileFields.generate(fieldsName, cols)
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
      privateConstructor = false,
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
