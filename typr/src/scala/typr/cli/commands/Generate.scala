package typr.cli.commands

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import tui.*
import tui.widgets.*
import typr.*
import typr.avro.AvroCodegen
import typr.cli.config.*
import typr.config.generated.AvroBoundary
import typr.config.generated.DatabaseBoundary
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbBoundary
import typr.config.generated.DuckdbSource
import typr.config.generated.GrpcBoundary
import typr.db
import typr.grpc.{GrpcCodegen, ProtoSource}
import typr.internal.{FileSync, generate}
import typr.internal.codegen.addPackageAndImports
import typr.jvm
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.{SqlFile, SqlFileReader}

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.{Duration as JDuration, Instant}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
  ): IO[ExitCode] = {
    val result = for {
      _ <- IO.unlessA(quiet)(IO.println(s"Reading config from: $configPath"))

      yamlContent <- IO(Files.readString(Paths.get(configPath)))
      substituted <- IO.fromEither(EnvSubstitution.substitute(yamlContent).left.map(new Exception(_)))

      config <- IO.fromEither(ConfigParser.parse(substituted).left.map(new Exception(_)))
      _ <- IO.whenA(debug)(IO.println(s"Parsed config with ${config.boundaries.map(_.size).getOrElse(0)} boundaries, ${config.outputs.map(_.size).getOrElse(0)} outputs"))

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

      results <-
        if (quiet) {
          val typoLogger = TypoLogger.Noop
          val externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)
          runWithoutTui(filtered, parsedBoundaries, config.types, buildDir, typoLogger, externalTools, tracker)
        } else {
          val tuiLogger = TypoLogger.Noop
          val tuiExternalTools = ExternalTools.init(tuiLogger, ExternalToolsConfig.default)
          runWithTui(filtered, parsedBoundaries, config.types, buildDir, tuiLogger, tuiExternalTools, tracker).handleErrorWith { _ =>
            val consoleLogger = TypoLogger.Console
            val consoleExternalTools = ExternalTools.init(consoleLogger, ExternalToolsConfig.default)
            runWithoutTui(filtered, parsedBoundaries, config.types, buildDir, consoleLogger, consoleExternalTools, tracker)
          }
        }

      _ <- IO.unlessA(quiet)(IO {
        val (successful, failed, skipped) = tracker.summary
        val elapsed = tracker.elapsedSeconds
        println()
        println(s"Summary: $successful succeeded, $failed failed, $skipped skipped (${f"$elapsed%.1f"}s)")
        tracker.failedEntries.foreach { case (name, err) =>
          println(s"  ✗ $name: $err")
        }
      })

      errors = results.collect { case Left(e) => e }
      successCount = results.count(_.isRight)

      _ <- IO.unlessA(quiet)(IO.println(s"\nGeneration complete: $successCount/${results.size} outputs succeeded"))

    } yield if (errors.isEmpty) ExitCode.Success else ExitCode.Error

    result.handleErrorWith { e =>
      IO.println(s"Error: ${e.getMessage}") *>
        IO.whenA(debug)(IO.println(e.getStackTrace.mkString("\n"))) *>
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
          case ParsedBoundary.OpenApi(_) | ParsedBoundary.JsonSchema(_) =>
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
          var totalFiles = 0
          var lastError: Option[String] = None

          matchedBoundaryNames.foreach { boundaryName =>
            boundaries.get(boundaryName) match {
              case Some(ParsedBoundary.Avro(avroConfig)) =>
                tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating Avro..."))
                generateAvroForOutput(outputName, boundaryName, avroConfig, outputConfig, buildDir, tracker) match {
                  case Right(files) => totalFiles += files
                  case Left(error)  => lastError = Some(error)
                }
              case Some(ParsedBoundary.Grpc(grpcConfig)) =>
                tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating gRPC..."))
                generateGrpcForOutput(outputName, boundaryName, grpcConfig, outputConfig, buildDir, tracker) match {
                  case Right(files) => totalFiles += files
                  case Left(error)  => lastError = Some(error)
                }
              case _ =>
                Option(fetchedBoundaries.get(boundaryName)).foreach { fetched =>
                  tracker.update(outputName, OutputStatus.Processing(boundaryName, "Generating..."))
                  generateWithCachedBoundary(
                    outputName,
                    outputConfig,
                    fetched,
                    buildDir,
                    typoLogger,
                    externalTools,
                    tracker,
                    step => tracker.update(outputName, OutputStatus.Processing(boundaryName, step))
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

  private def runWithTui(
      outputs: Map[String, ConfigToOptions.OutputConfig],
      boundaries: Map[String, ParsedBoundary],
      globalTypes: Option[Map[String, typr.config.generated.BridgeType]],
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker
  ): IO[List[Either[String, Int]]] =
    if (System.console() == null) {
      IO.raiseError(new RuntimeException("No TTY available"))
    } else {
      IO.blocking {
        withTerminal { (jni, terminal) =>
          val done = new AtomicBoolean(false)
          val resultsHolder = new java.util.concurrent.atomic.AtomicReference[List[Either[String, Int]]](Nil)

          val genFuture = Future {
            val results = runTwoPhaseGeneration(outputs, boundaries, buildDir, typoLogger, externalTools, tracker, (_, _) => ())
            resultsHolder.set(results)
            done.set(true)
            results
          }

          val tickRate = JDuration.ofMillis(100)
          var lastTick = Instant.now()

          while (!done.get()) {
            terminal.draw(f => renderUI(f, tracker))

            val elapsed = JDuration.between(lastTick, Instant.now())
            val timeout = tickRate.minus(elapsed)
            val timeoutDur = new tui.crossterm.Duration(
              Math.max(0, timeout.toSeconds),
              Math.max(0, timeout.getNano)
            )

            if (jni.poll(timeoutDur)) {
              jni.read() match {
                case key: tui.crossterm.Event.Key =>
                  key.keyEvent.code match {
                    case char: tui.crossterm.KeyCode.Char if char.c() == 'q' =>
                      done.set(true)
                    case _ => ()
                  }
                case _ => ()
              }
            }

            if (elapsed.compareTo(tickRate) >= 0) {
              lastTick = Instant.now()
            }
          }

          terminal.draw(f => renderUI(f, tracker))

          Await.result(genFuture, Duration.Inf)
        }
      }
    }

  private def renderUI(f: Frame, tracker: ProgressTracker): Unit = {
    val elapsed = tracker.elapsedSeconds
    val entries = tracker.getAll
    val (totalWritten, totalUnchanged, totalDeleted) = tracker.getTotalFiles

    val rects = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Min(math.max(entries.size + 3, 6)),
        Constraint.Length(5)
      )
    ).split(f.size)

    renderHeader(f, rects(0), tracker, elapsed, totalWritten, totalUnchanged)
    renderOutputTable(f, rects(1), entries)
    renderFooter(f, rects(2), tracker, totalWritten, totalUnchanged, totalDeleted)
  }

  private def renderHeader(f: Frame, area: Rect, tracker: ProgressTracker, elapsed: Double, written: Int, unchanged: Int): Unit = {
    val completed = tracker.completedCount
    val total = tracker.totalCount
    val progressPct = if (total > 0) (completed.toDouble / total * 100).toInt else 0

    val progressBar = {
      val barWidth = 20
      val filled = (barWidth * completed / math.max(total, 1)).toInt
      val empty = barWidth - filled
      s"[${"█" * filled}${"░" * empty}]"
    }

    val titleLine = Spans.from(
      Span.styled("Typr Code Generation", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
      Span.nostyle(f"  $progressBar $progressPct%% ($completed/$total)  ${elapsed}%.1fs")
    )

    val statsLine = Spans.from(
      Span.styled("Files: ", Style(fg = Some(Color.Gray))),
      Span.styled(s"$written written", Style(fg = Some(Color.Green))),
      Span.nostyle(" | "),
      Span.styled(s"$unchanged unchanged", Style(fg = Some(Color.DarkGray)))
    )

    val headerBlock = BlockWidget(
      borders = Borders.NONE
    )

    val headerArea = headerBlock.inner(area)
    f.renderWidget(headerBlock, area)

    val buf = f.buffer
    buf.setSpans(headerArea.x, headerArea.y, titleLine, headerArea.width.toInt)
    buf.setSpans(headerArea.x, headerArea.y + 1, statsLine, headerArea.width.toInt)
  }

  private def renderOutputTable(f: Frame, area: Rect, entries: List[OutputProgress]): Unit = {
    val headerCells = Array("Output", "Boundaries", "Found", "Files", "Status").map { h =>
      TableWidget.Cell(Text.nostyle(h), style = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD))
    }
    val header = TableWidget.Row(cells = headerCells, bottomMargin = 0)

    val rows = entries.map { progress =>
      val (icon, color) = progress.status match {
        case OutputStatus.Pending          => ("○", Color.Gray)
        case OutputStatus.Processing(_, _) => ("◕", Color.Cyan)
        case OutputStatus.Completed(_, _)  => ("●", Color.Green)
        case OutputStatus.Failed(_)        => ("✗", Color.Red)
        case OutputStatus.Skipped          => ("◌", Color.Yellow)
      }

      val statusText = progress.status match {
        case OutputStatus.Pending               => "Waiting..."
        case OutputStatus.Processing(src, step) => s"$step"
        case OutputStatus.Completed(_, _)       => "Done"
        case OutputStatus.Failed(err)           => s"Error: ${err.take(30)}${if (err.length > 30) "..." else ""}"
        case OutputStatus.Skipped               => "No boundaries"
      }

      val boundariesText = progress.matchedBoundaries.size match {
        case 0 => "-"
        case 1 => progress.matchedBoundaries.head
        case n => s"${progress.matchedBoundaries.head} +${n - 1}"
      }

      val statsText = {
        val allStats = progress.boundaryStats.values.foldLeft(BoundaryStats.empty) { (acc, s) =>
          BoundaryStats(
            acc.tables + s.tables,
            acc.views + s.views,
            acc.enums + s.enums,
            acc.domains + s.domains,
            acc.sqlScripts + s.sqlScripts
          )
        }
        if (allStats.total > 0) allStats.summary else "-"
      }

      val filesText =
        if (progress.filesWritten > 0 || progress.filesUnchanged > 0)
          s"${progress.filesWritten}/${progress.filesWritten + progress.filesUnchanged}"
        else "-"

      val cells = Array(
        TableWidget.Cell(Text.nostyle(s"$icon ${progress.name}"), style = Style(fg = Some(color))),
        TableWidget.Cell(Text.nostyle(boundariesText), style = Style(fg = Some(Color.Gray))),
        TableWidget.Cell(Text.nostyle(statsText), style = Style(fg = Some(Color.Gray))),
        TableWidget.Cell(Text.nostyle(filesText), style = Style(fg = Some(Color.Gray))),
        TableWidget.Cell(Text.nostyle(statusText), style = Style(fg = Some(color)))
      )
      TableWidget.Row(cells, height = 1, bottomMargin = 0)
    }

    val table = TableWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      widths = Array(
        Constraint.Length(18),
        Constraint.Length(14),
        Constraint.Length(24),
        Constraint.Length(8),
        Constraint.Min(20)
      ),
      header = Some(header),
      rows = rows.toArray
    )
    f.renderWidget(table, area)
  }

  private def renderFooter(f: Frame, area: Rect, tracker: ProgressTracker, written: Int, unchanged: Int, deleted: Int): Unit = {
    val (successful, failed, skipped) = tracker.summary

    val lines = if (tracker.isComplete) {
      val resultColor = if (failed > 0) Color.Red else Color.Green
      val resultIcon = if (failed > 0) "✗" else "✓"
      List(
        Spans.from(
          Span.styled(s"$resultIcon Complete: ", Style(fg = Some(resultColor), addModifier = Modifier.BOLD)),
          Span.styled(s"$successful succeeded", Style(fg = Some(Color.Green))),
          if (failed > 0) Span.styled(s", $failed failed", Style(fg = Some(Color.Red))) else Span.nostyle(""),
          if (skipped > 0) Span.styled(s", $skipped skipped", Style(fg = Some(Color.Yellow))) else Span.nostyle("")
        ),
        Spans.from(
          Span.styled("Total files: ", Style(fg = Some(Color.Gray))),
          Span.nostyle(s"$written written, $unchanged unchanged" + (if (deleted > 0) s", $deleted deleted" else ""))
        )
      )
    } else {
      List(
        Spans.from(
          Span.styled("Running... ", Style(fg = Some(Color.Cyan))),
          Span.styled("(press 'q' to quit)", Style(fg = Some(Color.DarkGray)))
        ),
        Spans.nostyle("")
      )
    }

    val buf = f.buffer
    lines.zipWithIndex.foreach { case (spans, idx) =>
      buf.setSpans(area.x + 1, area.y + idx, spans, area.width.toInt - 2)
    }
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

    Try {
      var totalFiles = 0

      matchedBoundaries.foreach { case (boundaryName, boundary) =>
        tracker.update(outputName, OutputStatus.Processing(boundaryName, "Connecting..."))

        boundary match {
          case ParsedBoundary.Database(dbConfig) =>
            generateDatabaseForOutput(outputName, boundaryName, dbConfig, outputConfig, buildDir, typoLogger, externalTools, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.DuckDb(duckConfig) =>
            generateDuckDbForOutput(outputName, boundaryName, duckConfig, outputConfig, buildDir, typoLogger, externalTools, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.OpenApi(_) =>
            ()
          case ParsedBoundary.JsonSchema(_) =>
            ()
          case ParsedBoundary.Avro(avroConfig) =>
            generateAvroForOutput(outputName, boundaryName, avroConfig, outputConfig, buildDir, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
          case ParsedBoundary.Grpc(grpcConfig) =>
            generateGrpcForOutput(outputName, boundaryName, grpcConfig, outputConfig, buildDir, tracker) match {
              case Right(files) => totalFiles += files
              case Left(error)  => throw new Exception(error)
            }
        }
      }

      tracker.update(outputName, OutputStatus.Completed(matchedBoundaries.size, totalFiles))
      Right(totalFiles)
    } match {
      case Success(result) => result
      case Failure(e) =>
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
      tracker: ProgressTracker
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

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Reading SQL scripts..."))

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
      tracker: ProgressTracker
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

      _ = tracker.update(outputName, OutputStatus.Processing(boundaryName, "Reading SQL scripts..."))

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

  def generateWithCachedBoundary(
      outputName: String,
      outputConfig: ConfigToOptions.OutputConfig,
      fetchedBoundary: FetchedBoundary,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      tracker: ProgressTracker,
      onStep: String => Unit
  ): Either[String, Int] = {
    Try {
      val boundaryName = fetchedBoundary.name
      tracker.setMatchedBoundaries(outputName, List(boundaryName))
      tracker.setBoundaryStats(outputName, boundaryName, fetchedBoundary.stats)

      onStep(s"Generating (${fetchedBoundary.stats.summary})...")

      val mergedOptions = mergeBoundaryIntoOptions(outputConfig.options, fetchedBoundary.boundaryConfig)
      val targetSources = buildDir.resolve(outputConfig.path)

      val newFiles = generate
        .orThrow(
          mergedOptions,
          fetchedBoundary.metaDb,
          ProjectGraph("", targetSources, None, fetchedBoundary.boundaryConfig.selector, fetchedBoundary.sqlScripts, Nil),
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
      case Failure(e) =>
        val errorMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        tracker.update(outputName, OutputStatus.Failed(errorMsg))
        Left(s"$outputName: $errorMsg")
    }
  }

  type SourceStats = BoundaryStats
  type FetchedSource = FetchedBoundary

  def fetchDatabaseSource(
      sourceName: String,
      dbConfig: DatabaseSource,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] = {
    val json = io.circe.Encoder[DatabaseSource].apply(dbConfig)
    json.as[DatabaseBoundary] match {
      case Right(boundary) => fetchDatabaseBoundary(sourceName, boundary, buildDir, typoLogger, externalTools, onStep)
      case Left(err)       => Left(s"Failed to convert database source: ${err.getMessage}")
    }
  }

  def fetchDuckDbSource(
      sourceName: String,
      duckConfig: DuckdbSource,
      buildDir: Path,
      typoLogger: TypoLogger,
      externalTools: ExternalTools,
      onStep: String => Unit
  ): Either[String, FetchedBoundary] = {
    val json = io.circe.Encoder[DuckdbSource].apply(duckConfig)
    json.as[DuckdbBoundary] match {
      case Right(boundary) => fetchDuckDbBoundary(sourceName, boundary, buildDir, typoLogger, externalTools, onStep)
      case Left(err)       => Left(s"Failed to convert DuckDB source: ${err.getMessage}")
    }
  }

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
    generateWithCachedBoundary(outputName, outputConfig, fetchedSource, buildDir, typoLogger, externalTools, tracker, onStep)
}
