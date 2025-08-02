package typo
package internal

import typo.internal.codegen.*
import typo.internal.metadb.OpenEnum
import typo.internal.sqlfiles.SqlFile

import scala.collection.immutable
import scala.collection.immutable.SortedMap

object generate {
  private type Files = Map[sc.Type.Qualified, sc.File]

  // use this constructor if you need to run `typo` multiple times with different options but same database/scripts
  def apply(
      publicOptions: Options,
      metaDb0: MetaDb,
      graph: ProjectGraph[Selector, List[SqlFile]],
      openEnumsByTable: Map[db.RelationName, OpenEnum]
  ): List[Generated] = {
    Banner.maybePrint(publicOptions)
    val metaDb = publicOptions.rewriteDatabase(metaDb0)
    val pkg = sc.Type.Qualified(publicOptions.pkg).value
    val customTypesPackage = pkg / sc.Ident("customtypes")

    val default: ComputedDefault =
      ComputedDefault(publicOptions.naming(customTypesPackage))

    val naming = publicOptions.naming(pkg)
    val options = InternalOptions(
      dbLib = publicOptions.dbLib.map {
        case DbLibName.Anorm  => new DbLibAnorm(pkg, publicOptions.inlineImplicits, default, publicOptions.enableStreamingInserts, publicOptions.dialect)
        case DbLibName.Doobie => new DbLibDoobie(pkg, publicOptions.inlineImplicits, default, publicOptions.enableStreamingInserts, publicOptions.fixVerySlowImplicit, publicOptions.dialect)
        case DbLibName.ZioJdbc =>
          new DbLibZioJdbc(pkg, publicOptions.inlineImplicits, dslEnabled = publicOptions.enableDsl, default, publicOptions.enableStreamingInserts, publicOptions.dialect)
      },
      debugTypes = publicOptions.debugTypes,
      enableDsl = publicOptions.enableDsl,
      enableFieldValue = publicOptions.enableFieldValue,
      enableStreamingInserts = publicOptions.enableStreamingInserts,
      enableTestInserts = publicOptions.enableTestInserts,
      fileHeader = publicOptions.fileHeader,
      generateMockRepos = publicOptions.generateMockRepos,
      enablePrimaryKeyType = publicOptions.enablePrimaryKeyType,
      jsonLibs = publicOptions.jsonLibs.map {
        case JsonLibName.Circe    => JsonLibCirce(pkg, default, publicOptions.inlineImplicits, publicOptions.dialect)
        case JsonLibName.PlayJson => JsonLibPlay(pkg, default, publicOptions.inlineImplicits, publicOptions.dialect)
        case JsonLibName.ZioJson  => JsonLibZioJson(pkg, default, publicOptions.inlineImplicits, publicOptions.dialect)
      },
      keepDependencies = publicOptions.keepDependencies,
      logger = publicOptions.logger,
      naming = naming,
      pkg = pkg,
      readonlyRepo = publicOptions.readonlyRepo,
      typeOverride = publicOptions.typeOverride,
      dialect = publicOptions.dialect
    )
    val customTypes = new CustomTypes(customTypesPackage, publicOptions.dialect)
    val genOrdering = new GenOrdering(customTypes, options.pkg)
    val scalaTypeMapper = TypeMapperScala(options.typeOverride, publicOptions.nullabilityOverride, naming, customTypes)
    val enums = metaDb.enums.map(ComputedStringEnum(naming))
    val domains = metaDb.domains.map(ComputedDomain(naming, scalaTypeMapper))
    val domainsByName = domains.map(d => (d.underlying.name, d)).toMap
    val projectsWithFiles: ProjectGraph[Files, List[sc.File]] =
      graph.valueFromProject { project =>
        val isRoot = graph == project
        val selector = project.value

        // note, this will import *all* (dependent) relations for the given project. we'll deduplicate in the end
        val computedLazyRelations: SortedMap[db.RelationName, Lazy[HasSource]] =
          rewriteDependentData(metaDb.relations).apply[HasSource] {
            case (_, dbTable: db.Table, eval) =>
              ComputedTable(options, default, dbTable, naming, scalaTypeMapper, eval, openEnumsByTable)
            case (_, dbView: db.View, eval) =>
              ComputedView(options.logger, dbView, naming, metaDb.typeMapperDb, scalaTypeMapper, eval, options.enableFieldValue.include(dbView.name), options.enableDsl, options.dialect)
          }

        // note, these statements will force the evaluation of some of the lazy values
        val computedSqlFiles: List[ComputedSqlFile] =
          project.scripts.map { sqlScript =>
            ComputedSqlFile(options.logger, sqlScript, options.pkg, naming, metaDb.typeMapperDb, scalaTypeMapper, computedLazyRelations.get, options.dialect)
          }

        computedLazyRelations.foreach { case (relName, lazyValue) =>
          if (selector.include(relName)) {
            forget(lazyValue.get)
          }
        }

        // here we keep only the values which have been evaluated. this may very well be a bigger set than the sum of the
        // relations chosen by the selector and the sql files
        val computedRelations = computedLazyRelations.flatMap { case (_, lazyValue) => lazyValue.getIfEvaluated }

        val computedRelationsByName: Map[db.RelationName, ComputedTable] =
          computedRelations.iterator.collect { case x: ComputedTable => (x.dbTable.name, x) }.toMap

        // yeah, sorry about the naming overload. this is a list of output files generated for each input sql file
        val sqlFileFiles: List[sc.File] =
          computedSqlFiles.flatMap(x => FilesSqlFile(x, naming, options).all)

        val relationFilesByName = computedRelations.flatMap {
          case viewComputed: ComputedView => FilesView(viewComputed, options).all.map(x => (viewComputed.view.name, x))
          case tableComputed: ComputedTable =>
            val fkAnalysis = FkAnalysis(computedRelationsByName, tableComputed, options.dialect)
            FilesTable(tableComputed, fkAnalysis, options, genOrdering, domainsByName).all.map(x => (tableComputed.dbTable.name, x))
          case _ => Nil
        }

        val domainFiles = domains.map(d => FileDomain(d, options, genOrdering))

        val mostFiles: List[sc.File] =
          List(
            options.dbLib.toList.flatMap(_.additionalFiles),
            List(FileDefault(default, options.jsonLibs, options.dbLib).file),
            enums.map(enm => FileStringEnum(options, enm, genOrdering)),
            domainFiles,
            customTypes.All.values.map(FileCustomType(options, genOrdering)),
            relationFilesByName.map { case (_, f) => f },
            sqlFileFiles
          ).flatten

        val keptMostFiles: List[sc.File] = {
          val keptRelations: immutable.Iterable[sc.File] =
            if (options.keepDependencies) relationFilesByName.map { case (_, f) => f }
            else relationFilesByName.collect { case (name, f) if selector.include(name) => f }

          minimize(mostFiles, entryPoints = sqlFileFiles ++ keptRelations ++ domainFiles)
        }

        // package objects have weird scoping, so don't attempt to automatically write imports for them.
        // this should be a stop-gap solution anyway
        val pkgObject = if (isRoot) FilePackageObject.packageObject(options) else None

        val testInsertsDataFiles: List[sc.File] =
          options.dbLib match {
            case Some(dbLib) =>
              val keptTypes = keptMostFiles.flatMap(x => x.tpe :: x.secondaryTypes).toSet
              val keptTables =
                computedRelations.collect { case x: ComputedTable if options.enableTestInserts.include(x.dbTable.name) && keptTypes(x.names.RepoImplName) => x }
              if (keptTables.nonEmpty) {
                val computed = ComputedTestInserts(project.name, options, customTypes, domains, enums, computedRelationsByName, keptTables)
                FileTestInserts(computed, dbLib)
              } else Nil
            case _ => Nil
          }

        val allFiles: Iterator[sc.File] = {
          val knownNamesByPkg: Map[sc.QIdent, Map[sc.Ident, sc.Type.Qualified]] =
            (keptMostFiles ++ testInsertsDataFiles).groupBy(_.pkg).map { case (pkg, files) =>
              (pkg, files.flatMap(f => (f.name, f.tpe) :: f.secondaryTypes.map(tpe => (tpe.value.name, tpe))).toMap)
            }

          // pick up names from super packages as well
          val knownNamesByPkgWithSuper: Map[sc.QIdent, Map[sc.Ident, sc.Type.Qualified]] =
            knownNamesByPkg.map { case (pkg, known) =>
              var currentPkg = pkg.parentOpt
              var allKnown = known
              while (currentPkg.isDefined) {
                knownNamesByPkg.get(currentPkg.get) match {
                  case Some(known2) => allKnown = known2 ++ allKnown
                  case None         =>
                }
                currentPkg = currentPkg.get.parentOpt
              }
              (pkg, allKnown)
            }
          val withImports = (testInsertsDataFiles.iterator ++ keptMostFiles).map(file => addPackageAndImports(knownNamesByPkgWithSuper, file))
          val all = withImports ++ pkgObject.iterator
          all.map(file => file.copy(contents = options.fileHeader.code ++ file.contents))
        }

        options.logger.info(s"Codegen complete for project ${project.target}")
        // keep files generated for sql files separate, so we dont name clash later
        val sqlTypes = sqlFileFiles.map(_.tpe).toSet
        allFiles.toList.partition(file => sqlTypes.contains(file.tpe)) match {
          case (sqlFiles, otherFiles) =>
            (otherFiles.map(f => f.tpe -> f).toMap, sqlFiles)
        }
      }

    val deduplicated = deduplicate(projectsWithFiles)
    deduplicated.toList.flatMap { p => Generated(p.target, p.testTarget, p.value.valuesIterator ++ p.scripts) }
  }

  // projects in graph will have duplicated files, this will pull the files up until they are no longer duplicated
  def deduplicate[S](graph: ProjectGraph[Files, S]): ProjectGraph[Files, S] = {
    def go(current: ProjectGraph[Files, S]): (Files, ProjectGraph[Files, S]) = {
      // start at leaves, so we can pull up files from the bottom up
      val (downstreamAccFiles: List[Files], rewrittenDownstream: List[ProjectGraph[Files, S]]) =
        current.downstream.map(go).unzip

      // these are the files we'll pull up
      val pullUp: Set[sc.Type.Qualified] = {
        val existInMoreThanOneDownStream =
          downstreamAccFiles.flatMap(_.keys).groupBy(identity).collect { case (k, v) if v.size > 1 => k }.toSet

        existInMoreThanOneDownStream ++ current.value.keys
      }

      // compute the set of all types in this graph of projects
      val currentAccFiles = downstreamAccFiles.foldLeft(current.value)(_ ++ _)

      // compute deduplicated version of this project
      val newGraph = current.copy(
        // rewrite downstream projects a second time where we drop files. note that for each level we rewrite all downstream projects again
        downstream = rewrittenDownstream.map(_.mapValue(_ -- pullUp)),
        value = current.value ++ pullUp.map(tpe => (tpe, currentAccFiles(tpe)))
      )

      (currentAccFiles, newGraph)
    }

    go(graph)._2
  }
}
