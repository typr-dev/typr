package typr
package internal

import typr.internal.codegen.*
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.SqlFile

import scala.collection.immutable
import scala.collection.immutable.SortedMap

object generate {
  private type Files = Map[jvm.Type.Qualified, jvm.File]

  // use this constructor if you need to run `typo` multiple times with different options but same database/scripts
  def apply(
      publicOptions: Options,
      metaDb0: MetaDb,
      graph: ProjectGraph[Selector, List[SqlFile]],
      openEnumsByTable: Map[db.RelationName, OpenEnum]
  ): List[Generated] = {
    Banner.maybePrint(publicOptions)
    val metaDb = publicOptions.rewriteDatabase(metaDb0)
    val pkg = jvm.Type.Qualified(publicOptions.pkg).value
    val customTypesPackage = pkg / jvm.Ident("customtypes")

    val default: ComputedDefault =
      ComputedDefault(publicOptions.naming(customTypesPackage, publicOptions.lang))

    val naming = publicOptions.naming(pkg, publicOptions.lang)
    val language = publicOptions.lang

    /** Old DbLibs (Anorm, Doobie, ZioJdbc) use typr.dsl (Legacy DSL) */
    def requireScalaWithLegacyDsl(lib: String): LangScala = language match {
      case s: LangScala => s.copy(dsl = DslQualifiedNames.Legacy)
      case _            => sys.error(s"You have chosen to generate code for ${lib}, which is a scala library. You need to pick scala as language")
    }

    // Validate: precise types only supported with DbLibName.Typo
    publicOptions.dbLib match {
      case Some(DbLibName.Anorm) | Some(DbLibName.Doobie) | Some(DbLibName.ZioJdbc) if publicOptions.enablePreciseTypes != Selector.None =>
        sys.error("enablePreciseTypes is only supported with DbLibName.Typo. Legacy database libraries (Anorm, Doobie, ZioJdbc) do not support precise types.")
      case _ => // ok
    }

    val options = InternalOptions(
      dbLib = publicOptions.dbLib.map {
        case DbLibName.Anorm =>
          new DbLibAnorm(pkg, publicOptions.inlineImplicits, default, publicOptions.enableStreamingInserts, requireScalaWithLegacyDsl("anorm"))
        case DbLibName.Doobie =>
          new DbLibDoobie(pkg, publicOptions.inlineImplicits, default, publicOptions.enableStreamingInserts, publicOptions.fixVerySlowImplicit, requireScalaWithLegacyDsl("doobie"))
        case DbLibName.Typo =>
          new DbLibFoundations(language, default, publicOptions.enableStreamingInserts, metaDb.dbType.adapter(needsTimestampCasts = false), naming)
        case DbLibName.ZioJdbc =>
          new DbLibZioJdbc(
            pkg,
            publicOptions.inlineImplicits,
            dslEnabled = publicOptions.enableDsl,
            default,
            publicOptions.enableStreamingInserts,
            requireScalaWithLegacyDsl("zio-jdbc")
          )
      },
      lang = language,
      debugTypes = publicOptions.debugTypes,
      enableDsl = publicOptions.enableDsl,
      enableFieldValue = publicOptions.enableFieldValue,
      enablePreciseTypes = publicOptions.enablePreciseTypes,
      enableStreamingInserts = publicOptions.enableStreamingInserts,
      enableTestInserts = publicOptions.enableTestInserts,
      fileHeader = publicOptions.fileHeader,
      generateMockRepos = publicOptions.generateMockRepos,
      enablePrimaryKeyType = publicOptions.enablePrimaryKeyType,
      jsonLibs = publicOptions.jsonLibs.map {
        case JsonLibName.Circe    => JsonLibCirce(pkg, default, publicOptions.inlineImplicits, requireScalaWithLegacyDsl("circe"))
        case JsonLibName.PlayJson => JsonLibPlay(pkg, default, publicOptions.inlineImplicits, requireScalaWithLegacyDsl("play-json"))
        case JsonLibName.ZioJson  => JsonLibZioJson(pkg, default, publicOptions.inlineImplicits, requireScalaWithLegacyDsl("zio-json"))
        case JsonLibName.Jackson  => JsonLibJackson(pkg, default, language)
      },
      keepDependencies = publicOptions.keepDependencies,
      logger = publicOptions.logger,
      naming = naming,
      pkg = pkg,
      readonlyRepo = publicOptions.readonlyRepo,
      typeOverride = publicOptions.typeOverride
    )
    val customTypes = new CustomTypes(customTypesPackage, language)
    val duckDbStructLookup = MetaDb.buildStructLookup(metaDb.duckDbStructTypes)
    val mariaSetTypes = metaDb.mariaSetTypes.map(ComputedMariaSet(naming))
    val mariaSetLookup = mariaSetTypes.map(s => s.sortedValues.toList -> s).toMap
    val scalaTypeMapper = publicOptions.dbLib match {
      case Some(DbLibName.Typo) => TypeMapperJvmNew(language, options.typeOverride, publicOptions.nullabilityOverride, naming, duckDbStructLookup, mariaSetLookup, publicOptions.enablePreciseTypes)
      case _                    => TypeMapperJvmOld(language, options.typeOverride, publicOptions.nullabilityOverride, naming, customTypes)
    }
    val enums = metaDb.enums.map(ComputedStringEnum(naming))
    val domains = metaDb.domains.map(ComputedDomain(naming, scalaTypeMapper))
    val domainsByName = domains.map(d => (d.underlying.name, d)).toMap
    val oracleObjectTypes = metaDb.oracleObjectTypes.values.toList
      .map(ComputedOracleObjectType(naming, scalaTypeMapper))
    val oracleCollectionTypes = metaDb.oracleCollectionTypes.values.toList
      .flatMap(ComputedOracleCollectionType(naming, scalaTypeMapper))
    val duckDbStructTypes = metaDb.duckDbStructTypes
      .map(ComputedDuckDbStruct(naming, scalaTypeMapper))
    val pgCompositeTypes = metaDb.pgCompositeTypes
      .map(ComputedPgCompositeType(naming, scalaTypeMapper))
    val pgCompositeLookup = pgCompositeTypes.map(c => c.underlying.compositeType.name -> c).toMap
    val projectsWithFiles: ProjectGraph[Files, List[jvm.File]] =
      graph.valueFromProject { project =>
        val isRoot = graph == project
        val selector = project.value

        // note, this will import *all* (dependent) relations for the given project. we'll deduplicate in the end
        val computedLazyRelations: SortedMap[db.RelationName, Lazy[HasSource]] =
          rewriteDependentData(metaDb.relations).apply[HasSource] {
            case (_, dbTable: db.Table, eval) =>
              ComputedTable(language, metaDb.dbType, options, default, dbTable, naming, scalaTypeMapper, eval, openEnumsByTable)
            case (_, dbView: db.View, eval) =>
              ComputedView(language, options.logger, dbView, naming, metaDb.typeMapperDb, scalaTypeMapper, eval, options.enableFieldValue.include(dbView.name), options.enableDsl)
          }

        // note, these statements will force the evaluation of some of the lazy values
        val computedSqlFiles: List[ComputedSqlFile] =
          project.scripts.map { sqlScript =>
            ComputedSqlFile(options.logger, sqlScript, options.pkg, naming, metaDb.typeMapperDb, scalaTypeMapper, computedLazyRelations.get, options.lang)
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
        val sqlFileFiles: List[jvm.File] =
          computedSqlFiles.flatMap(x => FilesSqlFile(language, x, naming, options).all)

        val relationFilesByName = computedRelations.flatMap {
          case viewComputed: ComputedView => FilesView(language, viewComputed, options).all.map(x => (viewComputed.view.name, x))
          case tableComputed: ComputedTable =>
            val fkAnalysis = FkAnalysis(computedRelationsByName, tableComputed, options.lang)
            FilesTable(language, tableComputed, fkAnalysis, options, domainsByName).all.map(x => (tableComputed.dbTable.name, x))
          case _ => Nil
        }

        val domainFiles = domains.map(d => FileDomain(d, options, language))
        val oracleObjectTypeFiles = oracleObjectTypes.map(FileOracleObjectType(_, options))
        val oracleCollectionTypeFiles = oracleCollectionTypes.map(FileOracleCollectionType(_, options))
        val adapter = metaDb.dbType.adapter(needsTimestampCasts = false)
        val duckDbStructTypeFiles = duckDbStructTypes.map(FileDuckDbStruct(_, options, adapter, duckDbStructLookup, naming))
        val pgCompositeTypeFiles = pgCompositeTypes.map(FilePgCompositeType(_, options, adapter, pgCompositeLookup, naming))
        val mariaSetTypeFiles = mariaSetTypes.flatMap(FileMariaSet(options, _, adapter))

        val preciseTypeFiles: List[jvm.File] = {
          if (options.enablePreciseTypes == Selector.None) Nil
          else {
            val allCols = computedRelations.flatMap {
              case t: ComputedTable => t.cols.toList.map(c => (t.dbTable.name, c.dbCol))
              case v: ComputedView  => v.cols.toList.map(c => (v.view.name, c.dbCol))
              case _                => Nil
            }

            val preciseConstraints = allCols.flatMap { case (relName, dbCol) =>
              if (!options.enablePreciseTypes.include(relName)) None
              else
                dbCol.tpe match {
                  case db.PgType.VarChar(Some(n)) if n != 2147483647                      => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.PgType.Bpchar(Some(n)) if n != 2147483647                       => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.MariaType.VarChar(Some(n))                                      => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.MariaType.Char(Some(n))                                         => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.MariaType.Decimal(Some(p), s)                                   => Some(PreciseConstraint.DecimalN(p, s.getOrElse(0), dbCol.tpe))
                  case db.MariaType.DateTime(Some(fsp)) if fsp > 0                        => Some(PreciseConstraint.LocalDateTimeN(fsp, dbCol.tpe))
                  case db.MariaType.Timestamp(Some(fsp)) if fsp > 0                       => Some(PreciseConstraint.LocalDateTimeN(fsp, dbCol.tpe))
                  case db.MariaType.Time(Some(fsp)) if fsp > 0                            => Some(PreciseConstraint.LocalTimeN(fsp, dbCol.tpe))
                  case db.MariaType.Binary(Some(n))                                       => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.MariaType.VarBinary(Some(n))                                    => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.DuckDbType.VarChar(Some(n))                                     => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.DuckDbType.Char(Some(n))                                        => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.DuckDbType.Decimal(Some(p), Some(s))                            => Some(PreciseConstraint.DecimalN(p, s, dbCol.tpe))
                  case db.OracleType.Varchar2(Some(n))                                    => Some(PreciseConstraint.NonEmptyStringN(n, dbCol.tpe))
                  case db.OracleType.NVarchar2(Some(n))                                   => Some(PreciseConstraint.NonEmptyStringN(n, dbCol.tpe))
                  case db.OracleType.Char(Some(n))                                        => Some(PreciseConstraint.NonEmptyPaddedStringN(n, dbCol.tpe))
                  case db.OracleType.NChar(Some(n))                                       => Some(PreciseConstraint.NonEmptyPaddedStringN(n, dbCol.tpe))
                  case db.OracleType.Number(Some(p), s) if s.getOrElse(0) == 0 && p <= 38 => Some(PreciseConstraint.DecimalN(p, 0, dbCol.tpe))
                  case db.OracleType.Number(Some(p), Some(s)) if s > 0                    => Some(PreciseConstraint.DecimalN(p, s, dbCol.tpe))
                  case db.OracleType.Timestamp(Some(fsp)) if fsp > 0                      => Some(PreciseConstraint.LocalDateTimeN(fsp, dbCol.tpe))
                  case db.OracleType.TimestampWithTimeZone(Some(fsp)) if fsp > 0          => Some(PreciseConstraint.OffsetDateTimeN(fsp, dbCol.tpe))
                  case db.OracleType.TimestampWithLocalTimeZone(Some(fsp)) if fsp > 0     => Some(PreciseConstraint.OffsetDateTimeN(fsp, dbCol.tpe))
                  case db.OracleType.Raw(Some(n))                                         => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.SqlServerType.VarChar(Some(n)) if n != -1                       => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.SqlServerType.Char(Some(n))                                     => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.SqlServerType.NVarChar(Some(n)) if n != -1                      => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.SqlServerType.NChar(Some(n))                                    => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.SqlServerType.Decimal(Some(p), Some(s))                         => Some(PreciseConstraint.DecimalN(p, s, dbCol.tpe))
                  case db.SqlServerType.Numeric(Some(p), Some(s))                         => Some(PreciseConstraint.DecimalN(p, s, dbCol.tpe))
                  case db.SqlServerType.DateTime2(Some(fsp)) if fsp > 0                   => Some(PreciseConstraint.LocalDateTimeN(fsp, dbCol.tpe))
                  case db.SqlServerType.DateTimeOffset(Some(fsp)) if fsp > 0              => Some(PreciseConstraint.OffsetDateTimeN(fsp, dbCol.tpe))
                  case db.SqlServerType.Time(Some(fsp)) if fsp > 0                        => Some(PreciseConstraint.LocalTimeN(fsp, dbCol.tpe))
                  case db.SqlServerType.Binary(Some(n))                                   => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.SqlServerType.VarBinary(Some(n)) if n != -1                     => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.DB2Type.VarChar(Some(n))                                        => Some(PreciseConstraint.StringN(n, dbCol.tpe))
                  case db.DB2Type.Char(Some(n))                                           => Some(PreciseConstraint.PaddedStringN(n, dbCol.tpe))
                  case db.DB2Type.Decimal(Some(p), Some(s))                               => Some(PreciseConstraint.DecimalN(p, s, dbCol.tpe))
                  case db.DB2Type.Timestamp(Some(fsp)) if fsp > 0                         => Some(PreciseConstraint.LocalDateTimeN(fsp, dbCol.tpe))
                  case db.DB2Type.Binary(Some(n))                                         => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case db.DB2Type.VarBinary(Some(n))                                      => Some(PreciseConstraint.BinaryN(n, dbCol.tpe))
                  case _                                                                  => None
                }
            }.toSet

            preciseConstraints.toList.flatMap { constraint =>
              constraint match {
                case PreciseConstraint.StringN(maxLength, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseStringNName(maxLength))
                  List(FilePreciseType.forStringN(tpe, maxLength, dbType, options, language))
                case PreciseConstraint.NonEmptyStringN(maxLength, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseNonEmptyStringNName(maxLength))
                  List(FilePreciseType.forNonEmptyStringN(tpe, maxLength, dbType, options, language))
                case PreciseConstraint.PaddedStringN(length, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.precisePaddedStringNName(length))
                  List(FilePreciseType.forPaddedStringN(tpe, length, dbType, options, language))
                case PreciseConstraint.NonEmptyPaddedStringN(length, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseNonEmptyPaddedStringNName(length))
                  List(FilePreciseType.forNonEmptyPaddedStringN(tpe, length, dbType, options, language))
                case PreciseConstraint.BinaryN(maxLength, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseBinaryNName(maxLength))
                  List(FilePreciseType.forBinaryN(tpe, maxLength, dbType, options, language))
                case PreciseConstraint.DecimalN(precision, scale, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseDecimalNName(precision, scale))
                  List(FilePreciseType.forDecimalN(tpe, precision, scale, dbType, options, language))
                case PreciseConstraint.LocalDateTimeN(fsp, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseLocalDateTimeNName(fsp))
                  List(FilePreciseType.forLocalDateTimeN(tpe, fsp, dbType, options, language))
                case PreciseConstraint.LocalTimeN(fsp, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseLocalTimeNName(fsp))
                  List(FilePreciseType.forLocalTimeN(tpe, fsp, dbType, options, language))
                case PreciseConstraint.OffsetDateTimeN(fsp, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseOffsetDateTimeNName(fsp))
                  List(FilePreciseType.forOffsetDateTimeN(tpe, fsp, dbType, options, language))
                case PreciseConstraint.InstantN(fsp, dbType) =>
                  val tpe = jvm.Type.Qualified(naming.preciseInstantNName(fsp))
                  List(FilePreciseType.forInstantN(tpe, fsp, dbType, options, language))
              }
            }
          }
        }
        val defaultFile = FileDefault(default, options.jsonLibs, options.dbLib, language)
        val mostFiles: List[jvm.File] =
          List(
            options.dbLib.toList.flatMap(_.additionalFiles),
            defaultFile.file :: defaultFile.additionalFiles,
            enums.map(enm => FileStringEnum(options, enm, adapter)),
            domainFiles,
            oracleObjectTypeFiles,
            oracleCollectionTypeFiles,
            duckDbStructTypeFiles,
            pgCompositeTypeFiles,
            mariaSetTypeFiles,
            preciseTypeFiles,
            customTypes.All.values.map(FileCustomType(options, language)),
            relationFilesByName.map { case (_, f) => f },
            sqlFileFiles
          ).flatten

        val keptMostFiles: List[jvm.File] = {
          val keptRelations: immutable.Iterable[jvm.File] =
            if (options.keepDependencies) relationFilesByName.map { case (_, f) => f }
            else relationFilesByName.collect { case (name, f) if selector.include(name) => f }

          // pgCompositeTypeFiles are entry points only for DbLibFoundations (which has PgStruct support)
          // For other dbLibs (like Anorm used by generate-sources), they're not included
          val compositeEntryPoints = options.dbLib match {
            case Some(_: DbLibFoundations) => pgCompositeTypeFiles
            case _                         => Nil
          }
          minimize(
            mostFiles,
            entryPoints = sqlFileFiles ++ keptRelations ++ domainFiles ++ oracleObjectTypeFiles ++ oracleCollectionTypeFiles ++ duckDbStructTypeFiles ++ compositeEntryPoints
          )
        }

        // package objects have weird scoping, so don't attempt to automatically write imports for them.
        // this should be a stop-gap solution anyway
        val pkgObject = if (isRoot) FilePackageObject.packageObject(options) else None

        val testInsertsDataFiles: List[jvm.File] =
          options.dbLib match {
            case Some(dbLib) =>
              val keptTypes = keptMostFiles.flatMap(x => x.tpe :: x.secondaryTypes).toSet
              val keptTables =
                computedRelations.collect { case x: ComputedTable if options.enableTestInserts.include(x.dbTable.name) && keptTypes(x.names.RepoImplName) => x }
              if (keptTables.nonEmpty) {
                val computed = ComputedTestInserts(project.name, options, language, customTypes, domains, enums, mariaSetTypes, computedRelationsByName, keptTables)
                FileTestInserts(computed, dbLib, language)
              } else Nil
            case _ => Nil
          }

        val allFiles: Iterator[jvm.File] = {
          val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] =
            (keptMostFiles ++ testInsertsDataFiles).groupBy(_.pkg).map { case (pkg, files) =>
              (pkg, files.flatMap(f => (f.name, f.tpe) :: f.secondaryTypes.map(tpe => (tpe.value.name, tpe))).toMap)
            }

          val withImports = (testInsertsDataFiles.iterator ++ keptMostFiles).map(file => addPackageAndImports(language, knownNamesByPkg, file))
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
    deduplicated.toList.flatMap { p => Generated(publicOptions.lang, p.target, p.testTarget, p.value.valuesIterator ++ p.scripts) }
  }

  // projects in graph will have duplicated files, this will pull the files up until they are no longer duplicated
  def deduplicate[S](graph: ProjectGraph[Files, S]): ProjectGraph[Files, S] = {
    def go(current: ProjectGraph[Files, S]): (Files, ProjectGraph[Files, S]) = {
      // start at leaves, so we can pull up files from the bottom up
      val (downstreamAccFiles: List[Files], rewrittenDownstream: List[ProjectGraph[Files, S]]) =
        current.downstream.map(go).unzip

      // these are the files we'll pull up
      val pullUp: Set[jvm.Type.Qualified] = {
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
