package typr.cli.commands

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import typr.*
import typr.avro.AvroCodegen
import typr.cli.config.*
import typr.config.generated.AvroBoundary
import typr.config.generated.DatabaseBoundary
import typr.config.generated.DuckdbBoundary
import typr.config.generated.SqliteBoundary
import typr.config.generated.GrpcBoundary
import typr.config.generated.OpenapiBoundary
import typr.db
import typr.grpc.{GrpcCodegen, ProtoSource}
import typr.openapi.{OpenApiCodegen, OpenApiOptions}
import typr.openapi.parser.OpenApiParser
import typr.internal.{FileSync, generate}
import typr.internal.codegen.addPackageAndImports
import typr.jvm
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.{SqlFile, SqlFileReader}

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

object Generate {

  sealed trait OutputStatus
  object OutputStatus {
    case object Pending extends OutputStatus
    case class Processing(boundaryName: String, step: String) extends OutputStatus
    case class Completed(boundaries: Int, files: Int) extends OutputStatus
    case class Failed(error: String) extends OutputStatus
    case object Skipped extends OutputStatus
  }

  case class BoundaryStats(
      tables: Int,
      views: Int,
      enums: Int,
      domains: Int,
      sqlScripts: Int
  ) {
    def total: Int = tables + views + enums + domains + sqlScripts
    def summary: String = {
      val parts = List(
        if (tables > 0) Some(s"$tables tables") else None,
        if (views > 0) Some(s"$views views") else None,
        if (enums > 0) Some(s"$enums enums") else None,
        if (domains > 0) Some(s"$domains domains") else None,
        if (sqlScripts > 0) Some(s"$sqlScripts scripts") else None
      ).flatten
      if (parts.isEmpty) "empty" else parts.mkString(", ")
    }
  }

  object BoundaryStats {
    val empty: BoundaryStats = BoundaryStats(0, 0, 0, 0, 0)
  }

  case class OutputProgress(
      name: String,
      matchedBoundaries: List[String],
      status: OutputStatus,
      currentBoundary: Option[String],
      boundaryStats: Map[String, BoundaryStats],
      filesWritten: Int,
      filesUnchanged: Int,
      filesDeleted: Int,
      startTime: Option[Long],
      endTime: Option[Long]
  ) {
    def durationMs: Option[Long] = for {
      start <- startTime
      end <- endTime.orElse(Some(System.currentTimeMillis()))
    } yield end - start

    def durationStr: String = durationMs match {
      case Some(ms) if ms < 1000 => s"${ms}ms"
      case Some(ms)              => f"${ms / 1000.0}%.1fs"
      case None                  => ""
    }
  }

  class ProgressTracker(outputNames: List[String]) {
    private val statuses = new ConcurrentHashMap[String, OutputProgress]()
    private val startTime = System.currentTimeMillis()
    @volatile private var totalFilesWritten = 0
    @volatile private var totalFilesUnchanged = 0
    @volatile private var totalFilesDeleted = 0

    outputNames.foreach { name =>
      statuses.put(name, OutputProgress(name, Nil, OutputStatus.Pending, None, Map.empty, 0, 0, 0, None, None))
    }

    def setMatchedBoundaries(name: String, boundaries: List[String]): Unit = {
      val current = statuses.get(name)
      if (current != null) {
        statuses.put(name, current.copy(matchedBoundaries = boundaries))
      }
    }

    def update(name: String, status: OutputStatus): Unit = {
      val current = statuses.get(name)
      if (current != null) {
        val now = System.currentTimeMillis()
        val newCurrent = status match {
          case OutputStatus.Processing(src, _) =>
            val start = current.startTime.getOrElse(now)
            current.copy(status = status, currentBoundary = Some(src), startTime = Some(start))
          case OutputStatus.Completed(_, _) | OutputStatus.Failed(_) | OutputStatus.Skipped =>
            current.copy(status = status, currentBoundary = None, endTime = Some(now))
          case _ =>
            current.copy(status = status, currentBoundary = None)
        }
        statuses.put(name, newCurrent)
      }
    }

    def setBoundaryStats(name: String, boundaryName: String, stats: BoundaryStats): Unit = {
      val current = statuses.get(name)
      if (current != null) {
        statuses.put(name, current.copy(boundaryStats = current.boundaryStats + (boundaryName -> stats)))
      }
    }

    def addFileStats(name: String, written: Int, unchanged: Int, deleted: Int): Unit = {
      val current = statuses.get(name)
      if (current != null) {
        statuses.put(
          name,
          current.copy(
            filesWritten = current.filesWritten + written,
            filesUnchanged = current.filesUnchanged + unchanged,
            filesDeleted = current.filesDeleted + deleted
          )
        )
        totalFilesWritten += written
        totalFilesUnchanged += unchanged
        totalFilesDeleted += deleted
      }
    }

    def completeWithStats(name: String, boundaries: Int, files: Int, written: Int, unchanged: Int, deleted: Int): Unit = {
      val current = statuses.get(name)
      if (current != null) {
        val now = System.currentTimeMillis()
        statuses.put(
          name,
          current.copy(
            status = OutputStatus.Completed(boundaries, files),
            currentBoundary = None,
            filesWritten = current.filesWritten + written,
            filesUnchanged = current.filesUnchanged + unchanged,
            filesDeleted = current.filesDeleted + deleted,
            endTime = Some(now)
          )
        )
        totalFilesWritten += written
        totalFilesUnchanged += unchanged
        totalFilesDeleted += deleted
      }
    }

    def elapsedSeconds: Double = (System.currentTimeMillis() - startTime) / 1000.0

    def getAll: List[OutputProgress] = outputNames.map(name => statuses.get(name))

    def isComplete: Boolean = getAll.forall { p =>
      p.status match {
        case OutputStatus.Pending | OutputStatus.Processing(_, _) => false
        case _                                                    => true
      }
    }

    def completedCount: Int = getAll.count { p =>
      p.status match {
        case OutputStatus.Completed(_, _) | OutputStatus.Failed(_) | OutputStatus.Skipped => true
        case _                                                                            => false
      }
    }

    def totalCount: Int = outputNames.size

    def getTotalFiles: (Int, Int, Int) = (totalFilesWritten, totalFilesUnchanged, totalFilesDeleted)

    def summary: (Int, Int, Int) = {
      val entries = getAll
      val successful = entries.count(_.status.isInstanceOf[OutputStatus.Completed])
      val failed = entries.count(_.status.isInstanceOf[OutputStatus.Failed])
      val skipped = entries.count(_.status == OutputStatus.Skipped)
      (successful, failed, skipped)
    }

    def failedEntries: List[(String, String)] = getAll.collect {
      case p if p.status.isInstanceOf[OutputStatus.Failed] =>
        (p.name, p.status.asInstanceOf[OutputStatus.Failed].error)
    }
  }

  def run(
      configPath: String,
      sourceFilter: Option[String],
      quiet: Boolean,
      debug: Boolean
  ): IO[ExitCode] = run(configPath, sourceFilter, quiet, debug, TypoLogger.Console, _ => ())

  /** Overload accepting an explicit [[TypoLogger]] — used by the TUI shell so it can capture structured log entries instead of letting them stream to stdout (which would corrupt the alternate-screen
    * buffer). The 4-arg overload above keeps the CLI call site short and preserves its console-logging behaviour.
    */
  def run(
      configPath: String,
      sourceFilter: Option[String],
      quiet: Boolean,
      debug: Boolean,
      typoLogger: TypoLogger
  ): IO[ExitCode] = run(configPath, sourceFilter, quiet, debug, typoLogger, _ => ())

  /** Overload that also hands the caller a reference to the [[ProgressTracker]] once it's constructed. The TUI uses this to render a live per-output status table by polling tracker state each render
    * — no log-line parsing.
    */
  def run(
      configPath: String,
      sourceFilter: Option[String],
      quiet: Boolean,
      debug: Boolean,
      typoLogger: TypoLogger,
      onTrackerReady: ProgressTracker => Unit
  ): IO[ExitCode] = {
    val result = for {
      // Closed-beta expiry warning — surfaces in CI logs so users see the countdown well before
      // the build refuses. Quiet mode suppresses everything; the warn-on-expired hard refusal
      // lives in typr.cli.Main so it can short-circuit before any IO sets up.
      _ <- IO.unlessA(quiet || !typr.cli.beta.BetaGate.isInWarningWindow())(
        IO(typoLogger.warn(typr.cli.beta.BetaGate.warningLine()))
      )
      _ <- IO.unlessA(quiet)(IO(typoLogger.info(s"Reading config from: $configPath")))

      yamlContent <- IO(Files.readString(Paths.get(configPath)))
      substituted <- IO.fromEither(EnvSubstitution.substitute(yamlContent).left.map(new Exception(_)))

      config <- IO.fromEither(ConfigParser.parse(substituted).left.map(new Exception(_)))
      _ <- IO.whenA(debug)(IO(typoLogger.info(s"Parsed config with ${config.boundaries.map(_.size).getOrElse(0)} boundaries, ${config.outputs.map(_.size).getOrElse(0)} outputs")))

      parsedBoundaries <- config.boundaries
        .getOrElse(Map.empty)
        .toList
        .traverse { case (name, json) =>
          ConfigParser.parseBoundary(json).map(name -> _)
        }
        .map(_.toMap)
        .left
        .map(new Exception(_))
        .liftTo[IO]

      parsedOutputs <- config.outputs
        .getOrElse(Map.empty)
        .toList
        .traverse { case (name, output) =>
          ConfigToOptions.convertOutput(name, output, config.types).map(name -> _)
        }
        .map(_.toMap)
        .left
        .map(new Exception(_))
        .liftTo[IO]

      filtered = sourceFilter match {
        case Some(pattern) =>
          parsedOutputs.filter { case (name, outputConfig) =>
            ConfigToOptions.matchesSource(name, List(pattern)) ||
            outputConfig.sourcePatterns.exists(p => ConfigToOptions.matchesSource(pattern, List(p)))
          }
        case None => parsedOutputs
      }

      buildDir = Path.of(System.getProperty("user.dir"))
      outputNames = filtered.keys.toList.sorted
      tracker = new ProgressTracker(outputNames)
      _ <- IO(onTrackerReady(tracker))

      externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)
      results <- runWithoutTui(filtered, parsedBoundaries, config.types, buildDir, typoLogger, externalTools, tracker)

      _ <- IO.unlessA(quiet)(IO {
        val (successful, failed, skipped) = tracker.summary
        val elapsed = tracker.elapsedSeconds
        typoLogger.info(s"Summary: $successful succeeded, $failed failed, $skipped skipped (${f"$elapsed%.1f"}s)")
        tracker.failedEntries.foreach { case (name, err) =>
          typoLogger.warn(s"  ✗ $name: $err")
        }
      })

      errors = results.collect { case Left(e) => e }
      successCount = results.count(_.isRight)

      _ <- IO.unlessA(quiet)(IO(typoLogger.info(s"Generation complete: $successCount/${results.size} outputs succeeded")))

    } yield if (errors.isEmpty) ExitCode.Success else ExitCode.Error

    result.handleErrorWith { e =>
      IO(typoLogger.warn(s"Error: ${e.getMessage}")) *>
        IO.whenA(debug)(IO(typoLogger.warn(e.getStackTrace.mkString("\n")))) *>
        IO.pure(ExitCode.Error)
    }
  }

  private def runWithoutTui(
      outputs: Map[String, ConfigToOptions.OutputConfig],
      boundaries: Map[String, ParsedBoundary],
      globalTypes: Option[Map[String, typr.config.generated.BridgeType]],
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker
  ): IO[List[Either[String, Int]]] = IO {
    runTwoPhaseGeneration(outputs, boundaries, buildDir, typoLogger, externalTools, tracker, (_, _) => ())
  }

  private def runTwoPhaseGeneration(
      outputs: Map[String, ConfigToOptions.OutputConfig],
      boundaries: Map[String, ParsedBoundary],
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      onBoundaryStep: (String, String) => Unit
  ): List[Either[String, Int]] = {
    val fetchedBoundaries = new ConcurrentHashMap[String, FetchedBoundary]()

    val uniqueBoundaryNames = outputs.values.flatMap { outputConfig =>
      boundaries.keys.filter(name => ConfigToOptions.matchesSource(name, outputConfig.sourcePatterns))
    }.toSet

    // Phase 1: Fetch all boundaries in parallel
    val fetchFutures = uniqueBoundaryNames.toList.sorted.map { boundaryName =>
      Future {
        onBoundaryStep(boundaryName, "Starting...")
        boundaries.get(boundaryName).foreach {
          case ParsedBoundary.Database(dbConfig) =>
            fetchDatabaseBoundary(boundaryName, dbConfig, buildDir, typoLogger, externalTools, step => onBoundaryStep(boundaryName, step)) match {
              case Right(fetched) =>
                fetchedBoundaries.put(boundaryName, fetched)
                onBoundaryStep(boundaryName, "Done")
              case Left(error) =>
                onBoundaryStep(boundaryName, s"Failed: $error")
            }
          case ParsedBoundary.DuckDb(duckConfig) =>
            fetchDuckDbBoundary(boundaryName, duckConfig, buildDir, typoLogger, externalTools, step => onBoundaryStep(boundaryName, step)) match {
              case Right(fetched) =>
                fetchedBoundaries.put(boundaryName, fetched)
                onBoundaryStep(boundaryName, "Done")
              case Left(error) =>
                onBoundaryStep(boundaryName, s"Failed: $error")
            }
          case ParsedBoundary.Sqlite(sqliteConfig) =>
            fetchSqliteBoundary(boundaryName, sqliteConfig, buildDir, typoLogger, externalTools, step => onBoundaryStep(boundaryName, step)) match {
              case Right(fetched) =>
                fetchedBoundaries.put(boundaryName, fetched)
                onBoundaryStep(boundaryName, "Done")
              case Left(error) =>
                onBoundaryStep(boundaryName, s"Failed: $error")
            }
          case ParsedBoundary.OpenApi(_) =>
            onBoundaryStep(boundaryName, "Done")
          case ParsedBoundary.JsonSchema(_) =>
            onBoundaryStep(boundaryName, "Skipped")
          case ParsedBoundary.Avro(_) =>
            onBoundaryStep(boundaryName, "Done")
          case ParsedBoundary.Grpc(_) =>
            onBoundaryStep(boundaryName, "Done")
        }
      }
    }
    Await.result(Future.sequence(fetchFutures), Duration.Inf)

    // Phase 2: Generate all outputs in parallel using cached boundaries
    val genFutures = outputs.toList.map { case (outputName, outputConfig) =>
      Future {
        val matchedBoundaryNames = boundaries.keys.filter(name => ConfigToOptions.matchesSource(name, outputConfig.sourcePatterns)).toList

        if (matchedBoundaryNames.isEmpty) {
          tracker.update(outputName, OutputStatus.Skipped)
          Right(0)
        } else {
          val isMultiSource = matchedBoundaryNames.size > 1
          var totalFiles = 0
          var lastError: Option[String] = None

          matchedBoundaryNames.foreach { boundaryName =>
            val effectiveOutputConfig = multiSourceOutputConfig(outputConfig, boundaryName, isMultiSource)
            boundaries.get(boundaryName) match {
              case Some(ParsedBoundary.Avro(avroConfig)) =>
                tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating Avro..."))
                generateAvroForOutput(outputName, boundaryName, avroConfig, effectiveOutputConfig, buildDir, tracker) match {
                  case Right(files) => totalFiles += files
                  case Left(error)  => lastError = Some(error)
                }
              case Some(ParsedBoundary.Grpc(grpcConfig)) =>
                tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating gRPC..."))
                generateGrpcForOutput(outputName, boundaryName, grpcConfig, effectiveOutputConfig, buildDir, tracker) match {
                  case Right(files) => totalFiles += files
                  case Left(error)  => lastError = Some(error)
                }
              case Some(ParsedBoundary.OpenApi(openapiConfig)) =>
                tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating OpenAPI..."))
                generateOpenApiForOutput(outputName, boundaryName, openapiConfig, outputConfig, buildDir, isMultiSource, tracker) match {
                  case Right(files) => totalFiles += files
                  case Left(error)  => lastError = Some(error)
                }
              case _ =>
                Option(fetchedBoundaries.get(boundaryName)).foreach { fetched =>
                  tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating..."))
                  generateWithCachedBoundary(
                    outputName,
                    effectiveOutputConfig,
                    fetched,
                    buildDir,
                    typoLogger,
                    externalTools,
                    tracker,
                    step => tracker.update(outputName, OutputStatus.Processing(boundaryName, step)),
                    skipSqlScripts = isMultiSource
                  ) match {
                    case Right(files) => totalFiles += files
                    case Left(error)  => lastError = Some(error)
                  }
                }
            }
          }

          lastError match {
            case Some(error) =>
              tracker.update(outputName, OutputStatus.Failed(error))
              Left(error)
            case None =>
              tracker.update(outputName, OutputStatus.Completed(matchedBoundaryNames.size, totalFiles))
              Right(totalFiles)
          }
        }
      }
    }
    val results = Await.result(Future.sequence(genFutures), Duration.Inf)

    // Close all data sources
    import scala.jdk.CollectionConverters.*
    fetchedBoundaries.values().asScala.foreach { fetched =>
      try {
        fetched.dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
      } catch {
        case _: Exception =>
      }
    }

    results
  }

  def generateForOutputPublic(
      outputName: String,
      outputConfig: ConfigToOptions.OutputConfig,
      allBoundaries: Map[String, ParsedBoundary],
      globalTypes: Option[Map[String, typr.config.generated.BridgeType]],
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker
  ): Either[String, Int] =
    generateForOutput(outputName, outputConfig, allBoundaries, globalTypes, buildDir, typoLogger, externalTools, tracker)

  private def generateForOutput(
      outputName: String,
      outputConfig: ConfigToOptions.OutputConfig,
      allBoundaries: Map[String, ParsedBoundary],
      globalTypes: Option[Map[String, typr.config.generated.BridgeType]],
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker
  ): Either[String, Int] = {
    val matchedBoundaries = allBoundaries.filter { case (name, _) =>
      ConfigToOptions.matchesSource(name, outputConfig.sourcePatterns)
    }

    tracker.setMatchedBoundaries(outputName, matchedBoundaries.keys.toList.sorted)

    if (matchedBoundaries.isEmpty) {
      tracker.update(outputName, OutputStatus.Skipped)
      return Right(0)
    }

    val isMultiSource = matchedBoundaries.size > 1

    Try {
      var totalFiles = 0

      matchedBoundaries.foreach { case (boundaryName, boundary) =>
        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Connecting..."))

        val effectiveOutputConfig = multiSourceOutputConfig(outputConfig, boundaryName, isMultiSource)

        boundary match {
          case ParsedBoundary.Database(dbConfig) =>
            generateDatabaseForOutput(outputName, boundaryName, dbConfig, effectiveOutputConfig, buildDir, typoLogger, externalTools, tracker, skipSqlScripts = isMultiSource) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.DuckDb(duckConfig) =>
            generateDuckDbForOutput(outputName, boundaryName, duckConfig, effectiveOutputConfig, buildDir, typoLogger, externalTools, tracker, skipSqlScripts = isMultiSource) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.Sqlite(sqliteConfig) =>
            generateSqliteForOutput(outputName, boundaryName, sqliteConfig, effectiveOutputConfig, buildDir, typoLogger, externalTools, tracker, skipSqlScripts = isMultiSource) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.OpenApi(openapiConfig) =>
            generateOpenApiForOutput(outputName, boundaryName, openapiConfig, outputConfig, buildDir, isMultiSource, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.JsonSchema(_) =>
            ()
          case ParsedBoundary.Avro(avroConfig) =>
            generateAvroForOutput(outputName, boundaryName, avroConfig, effectiveOutputConfig, buildDir, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.Grpc(grpcConfig) =>
            generateGrpcForOutput(outputName, boundaryName, grpcConfig, effectiveOutputConfig, buildDir, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
        }
      }

      tracker.update(outputName, OutputStatus.Completed(matchedBoundaries.size, totalFiles))
      Right(totalFiles)
    } match {
      case Success(result) => result
      case Failure(e)      =>
        val errorMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        tracker.update(outputName, OutputStatus.Failed(errorMsg))
        Left(s"$outputName: $errorMsg")
    }
  }

  private def generateDatabaseForOutput(
      outputName: String,
      boundaryName: String,
      dbConfig: DatabaseBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      skipSqlScripts: Boolean
  ): Either[String, Int] = {
    for {
      boundaryConfig <- ConfigToOptions.convertDatabaseBoundary(boundaryName, dbConfig)
      dataSource <- buildDataSource(dbConfig)

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Fetching metadata..."))

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      openEnumMap =
        if (boundaryConfig.openEnumsSelector != Selector.None) {
          Await.result(
            OpenEnum.find(dataSource, typoLogger, Selector.All, boundaryConfig.openEnumsSelector, metadb),
            Duration.Inf
          )
        } else Map.empty

      _ = if (!skipSqlScripts) tracker.update(outputName, OutputStatus.Processing(boundaryName, "Reading SQL scripts..."))

      sqlScripts =
        if (skipSqlScripts) Nil
        else
          boundaryConfig.sqlScriptsPath match {
            case Some(path) =>
              val scriptsPath = buildDir.resolve(path)
              Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
            case None => Nil
          }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
      _ = tracker.setBoundaryStats(outputName, boundaryName, stats)

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, s"Generating (${stats.summary})..."))

      mergedOptions = mergeBoundaryIntoOptions(outputConfig.options, boundaryConfig)
      targetSources = buildDir.resolve(outputConfig.path)

      newFiles = generate
        .orThrow(
          mergedOptions,
          metadb,
          ProjectGraph("", targetSources, None, boundaryConfig.selector, sqlScripts, Nil),
          openEnumMap
        )
        .head

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

      syncResults = newFiles.overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))

      written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      _ = tracker.addFileStats(outputName, written, unchanged, deleted)

      _ = dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    } yield written
  }

  private def generateDuckDbForOutput(
      outputName: String,
      boundaryName: String,
      duckConfig: DuckdbBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      skipSqlScripts: Boolean
  ): Either[String, Int] = {
    for {
      boundaryConfig <- ConfigToOptions.convertDuckDbBoundary(boundaryName, duckConfig)
      dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

      _ = boundaryConfig.schemaSqlPath.foreach { schemaPath =>
        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Loading schema..."))
        val fullPath = buildDir.resolve(schemaPath)
        Await.result(
          dataSource.run { conn =>
            val schemaSql = Files.readString(fullPath)
            val stmt = conn.createStatement()
            stmt.execute(schemaSql)
            stmt.close()
          },
          Duration.Inf
        )
      }

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Fetching metadata..."))

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      _ = if (!skipSqlScripts) tracker.update(outputName, OutputStatus.Processing(boundaryName, "Reading SQL scripts..."))

      sqlScripts =
        if (skipSqlScripts) Nil
        else
          boundaryConfig.sqlScriptsPath match {
            case Some(path) =>
              val scriptsPath = buildDir.resolve(path)
              Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
            case None => Nil
          }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
      _ = tracker.setBoundaryStats(outputName, boundaryName, stats)

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, s"Generating (${stats.summary})..."))

      mergedOptions = mergeBoundaryIntoOptions(outputConfig.options, boundaryConfig)
      targetSources = buildDir.resolve(outputConfig.path)

      newFiles = generate
        .orThrow(
          mergedOptions,
          metadb,
          ProjectGraph("", targetSources, None, boundaryConfig.selector, sqlScripts, Nil),
          Map.empty
        )
        .head

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

      syncResults = newFiles.overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))

      written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      _ = tracker.addFileStats(outputName, written, unchanged, deleted)

      _ = dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    } yield written
  }

  private def generateSqliteForOutput(
      outputName: String,
      boundaryName: String,
      sqliteConfig: SqliteBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      skipSqlScripts: Boolean
  ): Either[String, Int] = {
    for {
      boundaryConfig <- ConfigToOptions.convertSqliteBoundary(boundaryName, sqliteConfig)
      dataSource = TypoDataSource.hikariSqlite(sqliteConfig.path)

      _ = boundaryConfig.schemaSqlPath.foreach { schemaPath =>
        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Loading schema..."))
        val fullPath = buildDir.resolve(schemaPath)
        Await.result(
          dataSource.run { conn =>
            val schemaSql = Files.readString(fullPath)
            val stmt = conn.createStatement()
            executeBatch(stmt, schemaSql)
            stmt.close()
          },
          Duration.Inf
        )
      }

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Fetching metadata..."))

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      _ = if (!skipSqlScripts) tracker.update(outputName, OutputStatus.Processing(boundaryName, "Reading SQL scripts..."))

      sqlScripts =
        if (skipSqlScripts) Nil
        else
          boundaryConfig.sqlScriptsPath match {
            case Some(path) =>
              val scriptsPath = buildDir.resolve(path)
              Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
            case None => Nil
          }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
      _ = tracker.setBoundaryStats(outputName, boundaryName, stats)

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, s"Generating (${stats.summary})..."))

      mergedOptions = mergeBoundaryIntoOptions(outputConfig.options, boundaryConfig)
      targetSources = buildDir.resolve(outputConfig.path)

      newFiles = generate
        .orThrow(
          mergedOptions,
          metadb,
          ProjectGraph("", targetSources, None, boundaryConfig.selector, sqlScripts, Nil),
          Map.empty
        )
        .head

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

      syncResults = newFiles.overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))

      written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      _ = tracker.addFileStats(outputName, written, unchanged, deleted)

      _ = dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    } yield written
  }

  /** SQLite's JDBC driver cannot execute multi-statement strings via a single execute() call. Split on `;` boundaries and run each non-empty statement. We keep this simple — strip line comments and
    * split on `;` outside of single-quoted strings.
    */
  private def executeBatch(stmt: java.sql.Statement, sql: String): Unit = {
    val builder = new StringBuilder
    var inString = false
    var i = 0
    while (i < sql.length) {
      val c = sql.charAt(i)
      // Strip line comments `-- ...` when outside a string literal
      if (!inString && c == '-' && i + 1 < sql.length && sql.charAt(i + 1) == '-') {
        while (i < sql.length && sql.charAt(i) != '\n') i += 1
      } else if (c == '\'') {
        builder.append(c)
        inString = !inString
        i += 1
      } else if (c == ';' && !inString) {
        val piece = builder.toString.trim
        if (piece.nonEmpty) stmt.execute(piece)
        builder.clear()
        i += 1
      } else {
        builder.append(c)
        i += 1
      }
    }
    val tail = builder.toString.trim
    if (tail.nonEmpty) stmt.execute(tail)
  }

  private def mergeBoundaryIntoOptions(outputOptions: Options, boundaryConfig: ConfigToOptions.BoundaryConfig): Options = {
    outputOptions.copy(
      schemaMode = boundaryConfig.schemaMode,
      typeOverride = boundaryConfig.typeOverride,
      openEnums = boundaryConfig.openEnumsSelector,
      enablePreciseTypes =
        if (outputOptions.enablePreciseTypes == Selector.None) Selector.None
        else boundaryConfig.precisionTypesSelector,
      typeDefinitions = outputOptions.typeDefinitions ++ boundaryConfig.typeDefinitions
    )
  }

  private def generateAvroForOutput(
      outputName: String,
      boundaryName: String,
      avroConfig: AvroBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      tracker: ProgressTracker
  ): Either[String, Int] = {
    for {
      avroOptionsRaw <- ConfigToOptions.convertAvroBoundary(
        boundaryName,
        avroConfig,
        jvm.QIdent(outputConfig.options.pkg),
        outputConfig.effectType,
        outputConfig.framework
      )

      avroOptions = {
        def resolveSchemaSource(source: typr.avro.SchemaSource): typr.avro.SchemaSource = source match {
          case typr.avro.SchemaSource.Directory(path) =>
            val absolutePath = if (path.isAbsolute) path else buildDir.resolve(path)
            typr.avro.SchemaSource.Directory(absolutePath)
          case typr.avro.SchemaSource.Multi(sources) =>
            typr.avro.SchemaSource.Multi(sources.map(resolveSchemaSource))
          case other => other
        }
        avroOptionsRaw.copy(schemaSource = resolveSchemaSource(avroOptionsRaw.schemaSource))
      }

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating Avro code..."))

      result = AvroCodegen.generate(avroOptions, outputConfig.options.lang)

      _ <-
        if (result.errors.nonEmpty) {
          Left(result.errors.mkString("; "))
        } else Right(())

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

      targetSources = buildDir.resolve(outputConfig.path)

      knownNamesByPkg = result.files
        .groupBy(_.pkg)
        .map { case (pkg, files) =>
          pkg -> files.flatMap { f =>
            f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
          }.toMap
        }

      fileMap = result.files.map { file =>
        val pathParts = file.tpe.value.idents.map(_.value)
        val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${outputConfig.options.lang.extension}")
        val fileWithImports = addPackageAndImports(outputConfig.options.lang, knownNamesByPkg, file)
        relativePath -> fileWithImports.contents.render(outputConfig.options.lang).asString
      }.toMap

      syncResults = FileSync.syncStrings(
        folder = targetSources,
        fileRelMap = fileMap,
        deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
        softWrite = FileSync.SoftWrite.Yes(Set.empty)
      )

      written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      _ = tracker.addFileStats(outputName, written, unchanged, deleted)
    } yield written
  }

  private def generateGrpcForOutput(
      outputName: String,
      boundaryName: String,
      grpcConfig: GrpcBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      tracker: ProgressTracker
  ): Either[String, Int] = {
    for {
      grpcOptionsRaw <- ConfigToOptions.convertGrpcBoundary(
        boundaryName,
        grpcConfig,
        jvm.QIdent(outputConfig.options.pkg),
        outputConfig.effectType,
        outputConfig.framework
      )

      grpcOptions = grpcOptionsRaw.protoSource match {
        case ProtoSource.Directory(path, includePaths) =>
          val absolutePath = if (path.isAbsolute) path else buildDir.resolve(path)
          val absoluteIncludes = includePaths.map(p => if (p.isAbsolute) p else buildDir.resolve(p))
          grpcOptionsRaw.copy(protoSource = ProtoSource.Directory(absolutePath, absoluteIncludes))
        case ProtoSource.DescriptorSet(path) =>
          val absolutePath = if (path.isAbsolute) path else buildDir.resolve(path)
          grpcOptionsRaw.copy(protoSource = ProtoSource.DescriptorSet(absolutePath))
      }

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating gRPC code..."))

      result = GrpcCodegen.generate(grpcOptions, outputConfig.options.lang)

      _ <-
        if (result.errors.nonEmpty) {
          Left(result.errors.mkString("; "))
        } else Right(())

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

      targetSources = buildDir.resolve(outputConfig.path)

      knownNamesByPkg = result.files
        .groupBy(_.pkg)
        .map { case (pkg, files) =>
          pkg -> files.flatMap { f =>
            f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
          }.toMap
        }

      fileMap = result.files.map { file =>
        val pathParts = file.tpe.value.idents.map(_.value)
        val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${outputConfig.options.lang.extension}")
        val fileWithImports = addPackageAndImports(outputConfig.options.lang, knownNamesByPkg, file)
        relativePath -> fileWithImports.contents.render(outputConfig.options.lang).asString
      }.toMap

      syncResults = FileSync.syncStrings(
        folder = targetSources,
        fileRelMap = fileMap,
        deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
        softWrite = FileSync.SoftWrite.Yes(Set.empty)
      )

      written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      _ = tracker.addFileStats(outputName, written, unchanged, deleted)
    } yield written
  }

  private def generateOpenApiForOutput(
      outputName: String,
      boundaryName: String,
      openapiConfig: OpenapiBoundary,
      outputConfig: ConfigToOptions.OutputConfig,
      buildDir: Path,
      isMultiSource: Boolean,
      tracker: ProgressTracker
  ): Either[String, Int] = {
    val specPathStr = openapiConfig.spec match {
      case Some(p) => p
      case None    => return Left(s"$outputName: OpenAPI boundary '$boundaryName' has no spec path")
    }

    val specPath = {
      val p = Path.of(specPathStr)
      if (p.isAbsolute) p else buildDir.resolve(p)
    }

    if (!Files.exists(specPath)) {
      return Left(s"$outputName: OpenAPI spec not found: $specPath")
    }

    tracker.update(outputName, OutputStatus.Processing(boundaryName, "Parsing OpenAPI spec..."))

    OpenApiParser.parseFile(specPath) match {
      case Left(errors) =>
        Left(s"$outputName: Failed to parse OpenAPI spec: ${errors.mkString(", ")}")
      case Right(parsedSpec) =>
        val basePkg =
          if (isMultiSource) jvm.QIdent(outputConfig.options.pkg) / jvm.Ident(sanitizeBoundaryName(boundaryName))
          else jvm.QIdent(outputConfig.options.pkg)

        val options = OpenApiOptions
          .default(basePkg)
          .copy(
            generateApiInterfaces = openapiConfig.generate_server.getOrElse(true),
            generateWrapperTypes = false
          )

        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating OpenAPI code..."))

        val result = OpenApiCodegen.generateFromSpec(parsedSpec, options, outputConfig.options.lang)

        if (result.errors.nonEmpty) {
          return Left(s"$outputName: OpenAPI generation errors: ${result.errors.mkString(", ")}")
        }

        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Writing files..."))

        val targetSources =
          if (isMultiSource) buildDir.resolve(outputConfig.path).resolve(sanitizeBoundaryName(boundaryName))
          else buildDir.resolve(outputConfig.path)

        val knownNamesByPkg = result.files
          .groupBy(_.pkg)
          .map { case (pkg, files) =>
            pkg -> files.flatMap { f =>
              f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
            }.toMap
          }

        val fileMap = result.files.map { file =>
          val pathParts = file.tpe.value.idents.map(_.value)
          val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${outputConfig.options.lang.extension}")
          val fileWithImports = addPackageAndImports(outputConfig.options.lang, knownNamesByPkg, file)
          relativePath -> fileWithImports.contents.render(outputConfig.options.lang).asString
        }.toMap

        val syncResults = FileSync.syncStrings(
          folder = targetSources,
          fileRelMap = fileMap,
          deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
          softWrite = FileSync.SoftWrite.Yes(Set.empty)
        )

        val written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
        val unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
        val deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

        tracker.addFileStats(outputName, written, unchanged, deleted)
        Right(written)
    }
  }

  private def sanitizeBoundaryName(name: String): String =
    name.replace("-", "_")

  private def multiSourceOutputConfig(
      outputConfig: ConfigToOptions.OutputConfig,
      boundaryName: String,
      isMultiSource: Boolean
  ): ConfigToOptions.OutputConfig =
    if (isMultiSource) {
      val sanitized = sanitizeBoundaryName(boundaryName)
      outputConfig.copy(
        path = outputConfig.path + "/" + sanitized,
        options = outputConfig.options.copy(
          pkg = outputConfig.options.pkg + "." + sanitized
        )
      )
    } else outputConfig

  def buildDataSource(dbConfig: DatabaseBoundary): Either[String, TypoDataSource] = {
    val dbType = dbConfig.`type`.getOrElse("unknown")
    val host = dbConfig.host.getOrElse("localhost")
    val database = dbConfig.database.getOrElse("")
    val username = dbConfig.username.getOrElse("")
    val password = dbConfig.password.getOrElse("")

    Try {
      dbType match {
        case "postgresql" =>
          val port = dbConfig.port.map(_.toInt).getOrElse(5432)
          TypoDataSource.hikariPostgres(host, port, database, username, password)

        case "mariadb" | "mysql" =>
          val port = dbConfig.port.map(_.toInt).getOrElse(3306)
          TypoDataSource.hikariMariaDb(host, port, database, username, password)

        case "sqlserver" =>
          val port = dbConfig.port.map(_.toInt).getOrElse(1433)
          TypoDataSource.hikariSqlServer(host, port, database, username, password)

        case "oracle" =>
          val port = dbConfig.port.map(_.toInt).getOrElse(1521)
          val serviceName = dbConfig.service.orElse(dbConfig.sid).getOrElse("")
          TypoDataSource.hikariOracle(host, port, serviceName, username, password)

        case "db2" =>
          val port = dbConfig.port.map(_.toInt).getOrElse(50000)
          TypoDataSource.hikariDb2(host, port, database, username, password)

        case other =>
          throw new Exception(s"Unknown database type: $other")
      }
    }.toEither.left.map { e =>
      val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
      s"Failed to connect: $msg"
    }
  }

  case class FetchedBoundary(
      name: String,
      metaDb: MetaDb,
      dataSource: TypoDataSource,
      boundaryConfig: ConfigToOptions.BoundaryConfig,
      stats: BoundaryStats,
      openEnums: Map[db.RelationName, OpenEnum],
      sqlScripts: List[SqlFile]
  )

  def fetchDatabaseBoundary(
      boundaryName: String,
      dbConfig: DatabaseBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] = {
    for {
      boundaryConfig <- ConfigToOptions.convertDatabaseBoundary(boundaryName, dbConfig)
      _ = onStep("Connecting...")
      dataSource <- buildDataSource(dbConfig)
      _ = onStep("Fetching metadata...")

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      openEnumMap =
        if (boundaryConfig.openEnumsSelector != Selector.None) {
          Await.result(
            OpenEnum.find(dataSource, typoLogger, Selector.All, boundaryConfig.openEnumsSelector, metadb),
            Duration.Inf
          )
        } else Map.empty

      _ = onStep("Reading SQL scripts...")

      sqlScripts = boundaryConfig.sqlScriptsPath match {
        case Some(path) =>
          val scriptsPath = buildDir.resolve(path)
          Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
        case None => Nil
      }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
    } yield FetchedBoundary(boundaryName, metadb, dataSource, boundaryConfig, stats, openEnumMap, sqlScripts)
  }

  def fetchDuckDbBoundary(
      boundaryName: String,
      duckConfig: DuckdbBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] = {
    for {
      boundaryConfig <- ConfigToOptions.convertDuckDbBoundary(boundaryName, duckConfig)
      dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

      _ = boundaryConfig.schemaSqlPath.foreach { schemaPath =>
        onStep("Loading schema...")
        val fullPath = buildDir.resolve(schemaPath)
        Await.result(
          dataSource.run { conn =>
            val schemaSql = Files.readString(fullPath)
            val stmt = conn.createStatement()
            stmt.execute(schemaSql)
            stmt.close()
          },
          Duration.Inf
        )
      }

      _ = onStep("Fetching metadata...")

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      _ = onStep("Reading SQL scripts...")

      sqlScripts = boundaryConfig.sqlScriptsPath match {
        case Some(path) =>
          val scriptsPath = buildDir.resolve(path)
          Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
        case None => Nil
      }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
    } yield FetchedBoundary(boundaryName, metadb, dataSource, boundaryConfig, stats, Map.empty, sqlScripts)
  }

  def fetchSqliteBoundary(
      boundaryName: String,
      sqliteConfig: SqliteBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] = {
    for {
      boundaryConfig <- ConfigToOptions.convertSqliteBoundary(boundaryName, sqliteConfig)
      dataSource = TypoDataSource.hikariSqlite(sqliteConfig.path)

      _ = boundaryConfig.schemaSqlPath.foreach { schemaPath =>
        onStep("Loading schema...")
        val fullPath = buildDir.resolve(schemaPath)
        Await.result(
          dataSource.run { conn =>
            val schemaSql = Files.readString(fullPath)
            val stmt = conn.createStatement()
            executeBatch(stmt, schemaSql)
            stmt.close()
          },
          Duration.Inf
        )
      }

      _ = onStep("Fetching metadata...")

      metadb = Await.result(
        MetaDb.fromDb(typoLogger, dataSource, boundaryConfig.selector, boundaryConfig.schemaMode, externalTools),
        Duration.Inf
      )

      _ = onStep("Reading SQL scripts...")

      sqlScripts = boundaryConfig.sqlScriptsPath match {
        case Some(path) =>
          val scriptsPath = buildDir.resolve(path)
          Await.result(SqlFileReader(typoLogger, scriptsPath, dataSource, externalTools), Duration.Inf)
        case None => Nil
      }

      stats = BoundaryStats(
        tables = metadb.relations.values.count(_.forceGet.isInstanceOf[db.Table]),
        views = metadb.relations.values.count(_.forceGet.isInstanceOf[db.View]),
        enums = metadb.enums.size,
        domains = metadb.domains.size,
        sqlScripts = sqlScripts.size
      )
    } yield FetchedBoundary(boundaryName, metadb, dataSource, boundaryConfig, stats, Map.empty, sqlScripts)
  }

  def generateWithCachedBoundary(
      outputName: String,
      outputConfig: ConfigToOptions.OutputConfig,
      fetchedBoundary: FetchedBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      onStep: String => Unit,
      skipSqlScripts: Boolean
  ): Either[String, Int] = {
    Try {
      val boundaryName = fetchedBoundary.name
      tracker.setMatchedBoundaries(outputName, List(boundaryName))
      tracker.setBoundaryStats(outputName, boundaryName, fetchedBoundary.stats)

      onStep(s"Generating (${fetchedBoundary.stats.summary})...")

      val mergedOptions = mergeBoundaryIntoOptions(outputConfig.options, fetchedBoundary.boundaryConfig)
      val targetSources = buildDir.resolve(outputConfig.path)

      val sqlScripts = if (skipSqlScripts) Nil else fetchedBoundary.sqlScripts

      val newFiles = generate
        .orThrow(
          mergedOptions,
          fetchedBoundary.metaDb,
          ProjectGraph("", targetSources, None, fetchedBoundary.boundaryConfig.selector, sqlScripts, Nil),
          fetchedBoundary.openEnums
        )
        .head

      onStep("Writing files...")

      val syncResults = newFiles.overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))

      val written = syncResults.count { case (_, s) => s == FileSync.Synced.New || s == FileSync.Synced.Changed }
      val unchanged = syncResults.count { case (_, s) => s == FileSync.Synced.Unchanged }
      val deleted = syncResults.count { case (_, s) => s == FileSync.Synced.Deleted }

      tracker.completeWithStats(outputName, 1, written, written, unchanged, deleted)

      written
    } match {
      case Success(files) => Right(files)
      case Failure(e)     =>
        val errorMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        tracker.update(outputName, OutputStatus.Failed(errorMsg))
        Left(s"$outputName: $errorMsg")
    }
  }

  type SourceStats = BoundaryStats
  type FetchedSource = FetchedBoundary

  def fetchDatabaseSource(
      sourceName: String,
      dbConfig: DatabaseBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] =
    fetchDatabaseBoundary(sourceName, dbConfig, buildDir, typoLogger, externalTools, onStep)

  def fetchDuckDbSource(
      sourceName: String,
      duckConfig: DuckdbBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] =
    fetchDuckDbBoundary(sourceName, duckConfig, buildDir, typoLogger, externalTools, onStep)

  def fetchSqliteSource(
      sourceName: String,
      sqliteConfig: SqliteBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] =
    fetchSqliteBoundary(sourceName, sqliteConfig, buildDir, typoLogger, externalTools, onStep)

  def generateWithCachedSource(
      outputName: String,
      outputConfig: ConfigToOptions.OutputConfig,
      fetchedSource: FetchedBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      onStep: String => Unit
  ): Either[String, Int] =
    generateWithCachedBoundary(outputName, outputConfig, fetchedSource, buildDir, typoLogger, externalTools, tracker, onStep, skipSqlScripts = false)
}
