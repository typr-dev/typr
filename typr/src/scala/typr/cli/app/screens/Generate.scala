package typr.cli.app.screens

import cats.effect.ExitCode
import cats.effect.unsafe.implicits.global
import jatatui.components.chrome.ScreenFrame
import jatatui.components.router.RouterApi
import jatatui.components.scrollable.Scrollable
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.core.text.{Line, Span}
import jatatui.core.widgets.Widget
import jatatui.react.Components.*
import jatatui.react.Element
import jatatui.widgets.Borders
import jatatui.widgets.block.{Block, BorderType}
import jatatui.widgets.paragraph.Paragraph
import tui.crossterm.KeyCode
import typr.TypoLogger
import typr.cli.app.AppApi

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

/** Drive a code-generation run from inside the TUI.
  *
  * Logging: passes a [[ListLogger]] into [[typr.cli.commands.Generate.run]] (the 5-arg overload). All `info`/`warn` lands in the panel below; nothing spills to stdout.
  *
  * Generation runs on a daemon thread; the panel updates live via `requestRerender` every time a new entry lands. Esc / b only navigate away once the run has settled.
  */
object Generate {

  sealed trait Status
  object Status {
    case object Idle extends Status
    case object Running extends Status
    final case class Done(exitCode: ExitCode) extends Status
    final case class Failed(error: Throwable) extends Status
  }

  sealed trait Level
  object Level {
    case object Info extends Level
    case object Warn extends Level
  }

  final case class LogEntry(level: Level, message: String, timestampMs: Long)

  final class ListLogger(rerender: Runnable) extends TypoLogger {
    private val entries = new ConcurrentLinkedQueue[LogEntry]

    def snapshot: List[LogEntry] = entries.iterator.asScala.toList

    override def info(msg: String): Unit = push(Level.Info, msg)
    override def warn(msg: String): Unit = push(Level.Warn, msg)

    private def push(level: Level, msg: String): Unit = {
      entries.add(LogEntry(level, msg, System.currentTimeMillis()))
      rerender.run()
    }
  }

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val rerender = ctx.requestRerender()
      val statusRef = ctx.useRef(() => new AtomicReference[Status](Status.Idle))
      val logger = ctx.useRef(() => new ListLogger(rerender))
      val state = ctx.useState(() => Status.Idle: Status)
      val trackerRef = ctx.useRef(() => new AtomicReference[Option[typr.cli.commands.Generate.ProgressTracker]](None))

      val live = statusRef.get.get()
      if (state.get != live) state.set(live)

      ctx.useEffect(() => {
        statusRef.get.set(Status.Running)
        rerender.run()

        val configPathStr = app.configPath.toString
        val worker = new Thread(
          () => {
            val result: Status =
              try
                Status.Done(
                  typr.cli.commands.Generate
                    .run(
                      configPathStr,
                      None,
                      quiet = false,
                      debug = false,
                      logger.get,
                      onTrackerReady = t => { trackerRef.get.set(Some(t)); rerender.run() }
                    )
                    .unsafeRunSync()
                )
              catch case e: Throwable => Status.Failed(e)
            statusRef.get.set(result)
            rerender.run()
          },
          "typr-generate"
        )
        worker.setDaemon(true)
        worker.start()
      })

      // Periodically nudge a rerender while the run is in flight so per-output duration
      // counters and tracker states refresh even between log-emitting events. A daemon ticker
      // that exits as soon as status leaves Running keeps the cost bounded.
      ctx.useEffect(() => {
        val ticker = new Thread(
          () => {
            while (statusRef.get.get() == Status.Running) {
              try Thread.sleep(500L)
              catch case _: InterruptedException => ()
              rerender.run()
            }
          },
          "generate-tui-ticker"
        )
        ticker.setDaemon(true)
        ticker.start()
      })

      val canGoBack: Boolean = state.get match {
        case Status.Idle | Status.Done(_) | Status.Failed(_) => true
        case Status.Running                                  => false
      }

      val back = () => router.pop()
      if (canGoBack) {
        ctx.onGlobalKey(new KeyCode.Esc, () => back())
        ctx.onGlobalKey(new KeyCode.Char('b'), () => back())
      }

      val (label, color) = state.get match {
        case Status.Idle                   => ("waiting to start…", Color.GRAY)
        case Status.Running                => ("generating…", Color.CYAN)
        case Status.Done(ExitCode.Success) => ("done · success", Color.GREEN)
        case Status.Done(code)             => (s"done · exit ${code.code}", Color.YELLOW)
        case Status.Failed(err)            =>
          (s"failed · ${err.getClass.getSimpleName}: ${trim(err.getMessage)}", Color.RED)
      }

      val hint = state.get match {
        case Status.Running => "this can take a while on a clean build"
        case Status.Idle    => ""
        case _              => "esc or b to return"
      }

      val statusStyle = Style.empty.withFg(color).withAddModifier(Modifier.BOLD)
      val hintStyle = Style.empty.withFg(Color.DARK_GRAY)

      val trackerOpt = trackerRef.get.get()
      val sourcesPane =
        scrollPanel(sourcesPanelHeader(app), sourcesPanelRows(app))
      val outputsPane =
        scrollPanel(outputsPanelHeader(trackerOpt), trackerOpt.map(outputsPanelRows).getOrElse(Nil))

      val body = column(
        length(1, text(s"  $label", statusStyle)),
        length(1, text(s"  $hint", hintStyle)),
        length(1, empty()),
        fill(
          2,
          jatatui.react.Components.row(
            fill(1, sourcesPane),
            length(2, empty()),
            fill(1, outputsPane)
          )
        ),
        fill(1, logPanel(logger.get))
      )

      // Back button stays in the corner while generating but is inert — visual consistency
      // beats an empty corner mid-run. canGoBack already gates Esc/b above.
      ScreenFrame.of("menu", () => if (canGoBack) back() else (), body)
    }

  private val panelTitleStyle = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val panelMutedStyle = Style.empty.withFg(Color.DARK_GRAY)

  /** Standard side-panel layout: title row at top (always visible), then a scrollable column of rows below. Both halves of the Generate split use this — sources on the left, outputs on the right.
    */
  private def scrollPanel(header: Element, rows: List[Element]): Element =
    column(length(1, header), fill(1, Scrollable.column(rows.asJava)))

  // ─────────────────────────────────── sources panel ───────────────────────────────────

  private def sourcesPanelHeader(app: AppApi): Element = {
    val sources = app.config.sources.getOrElse(Map.empty)
    val statuses = sources.keys.map(n => app.loadStatus.getOrElse(n, typr.cli.app.LoadStatus.NotLoaded)).toList
    val total = statuses.size
    val loadedCount = statuses.count(_ == typr.cli.app.LoadStatus.Loaded)
    val loadingCount = statuses.count(_ == typr.cli.app.LoadStatus.Loading)
    val failedCount = statuses.count(_.isInstanceOf[typr.cli.app.LoadStatus.Failed])
    val extras = List(
      if (loadingCount > 0) Some(s"$loadingCount loading") else None,
      if (failedCount > 0) Some(s"$failedCount failed") else None
    ).flatten
    val suffix = if (extras.isEmpty) "" else extras.mkString(" · ", " · ", "")
    text(s"  sources  ($loadedCount/$total loaded$suffix)", panelTitleStyle)
  }

  private def sourcesPanelRows(app: AppApi): List[Element] = {
    val sources = app.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)
    if (sources.isEmpty) return List(length(1, text("  no sources configured", panelMutedStyle)))

    val nameStyle = Style.empty.withFg(Color.WHITE)
    val maxNameWidth = sources.map(_._1.length).max + 2

    sources.map { case (sourceName, _) =>
      val status = app.loadStatus.getOrElse(sourceName, typr.cli.app.LoadStatus.NotLoaded)
      val (icon, statusText, statusStyle) = status match {
        case typr.cli.app.LoadStatus.Loaded      => ("●", "ready", Style.empty.withFg(Color.GREEN))
        case typr.cli.app.LoadStatus.Loading     => ("◕", "fetching…", Style.empty.withFg(Color.CYAN))
        case typr.cli.app.LoadStatus.Failed(err) => ("✗", err.take(80), Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD))
        case typr.cli.app.LoadStatus.NotLoaded   => ("○", "queued", panelMutedStyle)
      }
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(s"  $icon  ", statusStyle),
          Span.styled(sourceName.padTo(maxNameWidth, ' '), nameStyle),
          Span.styled(statusText, panelMutedStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      length(1, widget(w))
    }
  }

  // ─────────────────────────────────── outputs panel ───────────────────────────────────

  private def outputsPanelHeader(tracker: Option[typr.cli.commands.Generate.ProgressTracker]): Element =
    tracker match {
      case None    => text("  outputs", panelTitleStyle)
      case Some(t) => text(s"  outputs  (${t.completedCount}/${t.totalCount})", panelTitleStyle)
    }

  private def outputsPanelRows(tracker: typr.cli.commands.Generate.ProgressTracker): List[Element] = {
    import typr.cli.commands.Generate.{OutputProgress, OutputStatus}
    val outputs = tracker.getAll
    if (outputs.isEmpty) return List(length(1, text("  (waiting for outputs)", panelMutedStyle)))

    val pendingStyle = panelMutedStyle
    val runningStyle = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
    val okStyle = Style.empty.withFg(Color.GREEN)
    val failStyle = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
    val skipStyle = Style.empty.withFg(Color.YELLOW)

    val maxNameWidth = outputs.map(_.name.length).max + 2

    def cellFor(p: OutputProgress): (String, Style, String) = p.status match {
      case OutputStatus.Pending          => ("○", pendingStyle, "pending")
      case OutputStatus.Processing(b, s) => ("◕", runningStyle, s"$s · $b".take(40))
      case OutputStatus.Completed(b, f)  => ("●", okStyle, s"$b boundaries · $f files")
      case OutputStatus.Failed(err)      => ("✗", failStyle, s"failed: ${err.take(60)}")
      case OutputStatus.Skipped          => ("—", skipStyle, "skipped")
    }

    val rows: List[Element] = outputs.map { p =>
      val (icon, iconStyle, statusText) = cellFor(p)
      val files = (p.filesWritten, p.filesUnchanged, p.filesDeleted) match {
        case (0, 0, 0) => ""
        case (w, u, d) =>
          val parts = List(
            if (w > 0) Some(s"+$w") else None,
            if (u > 0) Some(s"=$u") else None,
            if (d > 0) Some(s"-$d") else None
          ).flatten
          if (parts.isEmpty) "" else s"  ${parts.mkString(" ")}"
      }
      val dur = p.durationStr match {
        case "" => ""
        case d  => s"  $d"
      }
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(s"  $icon  ", iconStyle),
          Span.styled(p.name.padTo(maxNameWidth, ' '), Style.empty.withFg(Color.WHITE)),
          Span.styled(statusText, panelMutedStyle),
          Span.styled(files, Style.empty.withFg(Color.GREEN)),
          Span.styled(dur, panelMutedStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      length(1, widget(w))
    }

    val (succ, fail, skip) = tracker.summary
    val (tw, tu, td) = tracker.getTotalFiles
    val footer = length(
      1,
      text(
        f"  totals  $succ%d ok · $fail%d fail · $skip%d skip  ·  +$tw =$tu -$td  ·  ${tracker.elapsedSeconds}%.1fs",
        panelMutedStyle
      )
    )

    rows ::: List(length(1, empty()), footer)
  }

  private def logPanel(logger: ListLogger): Element = {
    val borderStyle = Style.empty.withFg(Color.DARK_GRAY)
    val titleStyle = Style.empty.withFg(Color.GRAY)
    val infoStyle = Style.empty.withFg(Color.GRAY)
    val warnStyle = Style.empty.withFg(Color.YELLOW)

    val w: Widget = (area, buffer) => {
      if (area.width >= 4 && area.height >= 3) {
        val block = Block.empty
          .withTitle(Line.styled(" output ", titleStyle))
          .withBorders(Borders.ALL)
          .withBorderType(BorderType.Rounded)
          .withBorderStyle(borderStyle)

        val targetLines = math.max(1, area.height - 2)
        val visible = logger.snapshot.takeRight(targetLines)
        val lines = visible.map { e =>
          val style = e.level match {
            case Level.Info => infoStyle
            case Level.Warn => warnStyle
          }
          val prefix = e.level match {
            case Level.Info => "  "
            case Level.Warn => "⚠ "
          }
          Line.from(Span.styled(s"$prefix${e.message}", style))
        }
        val javaLines = java.util.List.copyOf(scala.jdk.CollectionConverters.SeqHasAsJava(lines).asJava)
        Paragraph.of(javaLines).withBlock(block).render(area, buffer)
      }
    }

    widget(w)
  }

  private def trim(s: String): String = {
    val raw = Option(s).getOrElse("(no message)")
    if (raw.length <= 120) raw else raw.take(117) + "…"
  }
}
