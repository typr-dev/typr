package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.commands.Generate
import typr.cli.commands.Generate.{OutputProgress, OutputStatus, SourceStats}
import typr.cli.tui.AppScreen
import typr.cli.tui.GeneratingPhase
import typr.cli.tui.GeneratingState
import typr.cli.tui.SourceFetchProgress
import typr.cli.tui.SourceFetchStatus
import typr.cli.tui.TuiLogger
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object GeneratingScreen {
  def handleKey(state: TuiState, genState: AppScreen.Generating, keyCode: KeyCode): TuiState = keyCode match {
    case _: KeyCode.Esc =>
      genState.state.phase match {
        case GeneratingPhase.Completed | GeneratingPhase.Failed(_) =>
          Navigator.goBack(state)
        case _ =>
          state
      }

    case c: KeyCode.Char if c.c() == 'q' =>
      genState.state.phase match {
        case GeneratingPhase.Completed | GeneratingPhase.Failed(_) =>
          Navigator.handleEscape(state)
        case _ =>
          state
      }

    case _ => state
  }

  def render(f: Frame, state: TuiState, genState: AppScreen.Generating): Unit = {
    val gs = genState.state
    val elapsed = gs.elapsedSeconds

    val hasWarnings = gs.logger.exists(_.getTotalWarningCount > 0)

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Min(6),
        if (hasWarnings) Constraint.Length(6) else Constraint.Length(0),
        Constraint.Length(4)
      )
    ).split(f.size)

    renderHeader(f, chunks(0), gs, elapsed)
    renderMainContent(f, chunks(1), gs)
    if (hasWarnings) {
      renderWarnings(f, chunks(2), gs)
    }
    renderFooter(f, chunks(3), gs)
  }

  private def renderHeader(f: Frame, area: Rect, gs: GeneratingState, elapsed: Double): Unit = {
    val phaseText = gs.phase match {
      case GeneratingPhase.Starting        => "Starting..."
      case GeneratingPhase.FetchingSources => "Fetching Sources"
      case GeneratingPhase.GeneratingCode  => "Generating Code"
      case GeneratingPhase.Completed       => "Completed"
      case GeneratingPhase.Failed(_)       => "Failed"
    }

    val (totalWritten, totalUnchanged, totalDeleted) = gs.tracker.map(_.getTotalFiles).getOrElse((gs.filesWritten, 0, 0))

    val titleLine = Spans.from(
      Span.styled("Typr Code Generation", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
      Span.nostyle(f"  [$phaseText]  ${elapsed}%.1fs")
    )

    val statsLine = if (totalWritten > 0 || totalUnchanged > 0 || totalDeleted > 0) {
      Spans.from(
        Span.styled("Files: ", Style(fg = Some(Color.Gray))),
        Span.styled(s"$totalWritten changed", Style(fg = Some(Color.Green))),
        Span.nostyle(" | "),
        Span.styled(s"$totalUnchanged unchanged", Style(fg = Some(Color.DarkGray))),
        if (totalDeleted > 0) Span.nostyle(" | ") else Span.nostyle(""),
        if (totalDeleted > 0) Span.styled(s"$totalDeleted deleted", Style(fg = Some(Color.Red))) else Span.nostyle("")
      )
    } else {
      Spans.nostyle("")
    }

    val buf = f.buffer
    buf.setSpans(area.x + 1, area.y, titleLine, area.width.toInt - 2)
    buf.setSpans(area.x + 1, area.y + 1, statsLine, area.width.toInt - 2)
  }

  private def renderMainContent(f: Frame, area: Rect, gs: GeneratingState): Unit = {
    gs.phase match {
      case GeneratingPhase.Starting =>
        renderStartingMessage(f, area)

      case GeneratingPhase.FetchingSources =>
        renderSourceFetching(f, area, gs.sourceFetches)

      case GeneratingPhase.GeneratingCode =>
        gs.tracker match {
          case Some(tracker) => renderCodeGenerationMultiColumn(f, area, tracker, gs.logger)
          case None          => renderStartingMessage(f, area)
        }

      case GeneratingPhase.Completed =>
        gs.tracker match {
          case Some(tracker) => renderCompletionSummary(f, area, gs, tracker)
          case None          => renderCompletedMessageSimple(f, area, gs)
        }

      case GeneratingPhase.Failed(err) =>
        renderFailedMessage(f, area, err)
    }
  }

  private def renderStartingMessage(f: Frame, area: Rect): Unit = {
    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.nostyle("Initializing code generation...")
    )
    f.renderWidget(para, area)
  }

  private def renderSourceFetching(f: Frame, area: Rect, sources: List[SourceFetchProgress]): Unit = {
    val headerCells = Array("Source", "Type", "Status", "Time").map { h =>
      TableWidget.Cell(Text.nostyle(h), style = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD))
    }
    val header = TableWidget.Row(cells = headerCells, bottomMargin = 0)

    val rows = sources.map { src =>
      val (icon, color) = src.status match {
        case SourceFetchStatus.Pending          => ("○", Color.Gray)
        case SourceFetchStatus.Fetching         => ("◕", Color.Cyan)
        case SourceFetchStatus.Done(_, _, _, _) => ("●", Color.Green)
        case SourceFetchStatus.Failed(_)        => ("✗", Color.Red)
      }

      val statusText = src.status match {
        case SourceFetchStatus.Pending  => "Waiting..."
        case SourceFetchStatus.Fetching => src.currentStep
        case SourceFetchStatus.Done(t, v, e, w) =>
          val base = s"$t tables, $v views"
          if (w > 0) s"$base ($w warnings)" else base
        case SourceFetchStatus.Failed(err) => s"Error: ${err.take(25)}"
      }

      val timeText = src.durationStr

      val cells = Array(
        TableWidget.Cell(Text.nostyle(s"$icon ${src.name}"), style = Style(fg = Some(color))),
        TableWidget.Cell(Text.nostyle(src.sourceType), style = Style(fg = Some(Color.Gray))),
        TableWidget.Cell(Text.nostyle(statusText), style = Style(fg = Some(color))),
        TableWidget.Cell(Text.nostyle(timeText), style = Style(fg = Some(Color.DarkGray)))
      )
      TableWidget.Row(cells, height = 1, bottomMargin = 0)
    }

    val table = TableWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Fetching Sources", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      widths = Array(
        Constraint.Length(20),
        Constraint.Length(12),
        Constraint.Min(30),
        Constraint.Length(10)
      ),
      header = Some(header),
      rows = rows.toArray
    )
    f.renderWidget(table, area)
  }

  private def renderCodeGenerationMultiColumn(
      f: Frame,
      area: Rect,
      tracker: Generate.ProgressTracker,
      logger: Option[TuiLogger]
  ): Unit = {
    val entries = tracker.getAll
    val innerArea = Rect(area.x + 1, area.y + 1, area.width - 2, area.height - 2)

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Generating Code", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val columnWidth = 38
    val availableWidth = innerArea.width.toInt
    val numColumns = Math.max(1, availableWidth / columnWidth)
    val rowsPerColumn = Math.ceil(entries.size.toDouble / numColumns).toInt

    val buf = f.buffer

    entries.zipWithIndex.foreach { case (progress, idx) =>
      val col = idx / rowsPerColumn
      val row = idx % rowsPerColumn

      if (row < innerArea.height.toInt) {
        val x = innerArea.x + (col * columnWidth)
        val y = innerArea.y + row

        val (icon, color) = progress.status match {
          case OutputStatus.Pending          => ("○", Color.Gray)
          case OutputStatus.Processing(_, _) => ("◕", Color.Cyan)
          case OutputStatus.Completed(_, _)  => ("●", Color.Green)
          case OutputStatus.Failed(_)        => ("✗", Color.Red)
          case OutputStatus.Skipped          => ("◌", Color.Yellow)
        }

        val timeStr = progress.durationStr

        val filesInfo = if (progress.filesWritten > 0 || progress.filesUnchanged > 0) {
          s" ${progress.filesWritten}↑${progress.filesUnchanged}="
        } else ""

        val statusText = progress.status match {
          case OutputStatus.Processing(_, step) => s" $step"
          case OutputStatus.Completed(_, _)     => ""
          case OutputStatus.Failed(_)           => " ERR"
          case OutputStatus.Skipped             => " skip"
          case _                                => ""
        }

        val timeDisplay = if (timeStr.nonEmpty) s" $timeStr" else ""

        val maxNameLen = columnWidth - 2 - filesInfo.length - statusText.length - timeDisplay.length - 1
        val truncatedName = if (progress.name.length > maxNameLen) progress.name.take(maxNameLen - 1) + "…" else progress.name

        val line = Spans.from(
          Span.styled(s"$icon ", Style(fg = Some(color))),
          Span.styled(truncatedName, Style(fg = Some(color))),
          Span.styled(filesInfo, Style(fg = Some(Color.Gray))),
          Span.styled(statusText.take(12), Style(fg = Some(Color.DarkGray))),
          Span.styled(timeDisplay, Style(fg = Some(Color.DarkGray)))
        )
        buf.setSpans(x, y, line, columnWidth)
      }
    }
  }

  private def renderCompletionSummary(f: Frame, area: Rect, gs: GeneratingState, tracker: Generate.ProgressTracker): Unit = {
    val entries = tracker.getAll
    val (totalWritten, totalUnchanged, totalDeleted) = tracker.getTotalFiles

    val innerArea = Rect(area.x + 1, area.y + 1, area.width - 2, area.height - 2)

    val resultColor = if (gs.failed > 0) Color.Red else Color.Green
    val resultIcon = if (gs.failed > 0) "✗" else "✓"

    val block = BlockWidget(
      title = Some(
        Spans.from(
          Span.styled(s"$resultIcon Generation Complete", Style(fg = Some(resultColor), addModifier = Modifier.BOLD))
        )
      ),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val buf = f.buffer

    val summaryLine = Spans.from(
      Span.styled(s"${gs.successful} succeeded", Style(fg = Some(Color.Green))),
      if (gs.failed > 0) Span.styled(s", ${gs.failed} failed", Style(fg = Some(Color.Red))) else Span.nostyle(""),
      if (gs.skipped > 0) Span.styled(s", ${gs.skipped} skipped", Style(fg = Some(Color.Yellow))) else Span.nostyle(""),
      Span.nostyle("  |  "),
      Span.styled(s"$totalWritten changed", Style(fg = Some(Color.Green))),
      Span.nostyle(", "),
      Span.styled(s"$totalUnchanged unchanged", Style(fg = Some(Color.DarkGray))),
      if (totalDeleted > 0) Span.styled(s", $totalDeleted deleted", Style(fg = Some(Color.Red))) else Span.nostyle("")
    )
    buf.setSpans(innerArea.x, innerArea.y, summaryLine, innerArea.width.toInt)

    val columnWidth = 35
    val availableWidth = innerArea.width.toInt
    val numColumns = Math.max(1, availableWidth / columnWidth)
    val startY = innerArea.y + 2
    val availableRows = (innerArea.height.toInt - 2).max(1)
    val rowsPerColumn = Math.ceil(entries.size.toDouble / numColumns).toInt.min(availableRows)

    entries.zipWithIndex.foreach { case (progress, idx) =>
      val col = idx / rowsPerColumn
      val row = idx % rowsPerColumn

      if (row < availableRows) {
        val x = innerArea.x + (col * columnWidth)
        val y = startY + row

        val (icon, color) = progress.status match {
          case OutputStatus.Completed(_, _) => ("●", Color.Green)
          case OutputStatus.Failed(_)       => ("✗", Color.Red)
          case OutputStatus.Skipped         => ("◌", Color.Yellow)
          case _                            => ("○", Color.Gray)
        }

        val timeStr = progress.durationStr
        val filesStr = s"${progress.filesWritten}/${progress.filesWritten + progress.filesUnchanged}"

        val maxNameLen = columnWidth - 2 - timeStr.length - filesStr.length - 4
        val truncatedName = if (progress.name.length > maxNameLen) progress.name.take(maxNameLen - 1) + "…" else progress.name

        val spans = Spans.from(
          Span.styled(s"$icon ", Style(fg = Some(color))),
          Span.styled(truncatedName, Style(fg = Some(color))),
          Span.nostyle(" "),
          Span.styled(filesStr, Style(fg = Some(Color.Gray))),
          Span.nostyle(" "),
          Span.styled(timeStr, Style(fg = Some(Color.DarkGray)))
        )
        buf.setSpans(x, y, spans, columnWidth)
      }
    }
  }

  private def renderCompletedMessageSimple(f: Frame, area: Rect, gs: GeneratingState): Unit = {
    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.nostyle(s"Generation complete: ${gs.successful} succeeded, ${gs.failed} failed, ${gs.skipped} skipped (${gs.filesWritten} files)")
    )
    f.renderWidget(para, area)
  }

  private def renderFailedMessage(f: Frame, area: Rect, error: String): Unit = {
    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.from(Spans.from(Span.styled(s"Generation failed: $error", Style(fg = Some(Color.Red)))))
    )
    f.renderWidget(para, area)
  }

  private def renderWarnings(f: Frame, area: Rect, gs: GeneratingState): Unit = {
    val warnings = gs.logger.map(_.getRecentWarnings(4)).getOrElse(Nil)
    val totalWarnings = gs.logger.map(_.getTotalWarningCount).getOrElse(0)

    val block = BlockWidget(
      title = Some(
        Spans.from(
          Span.styled(s"Warnings ($totalWarnings)", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
        )
      ),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(area.x + 1, area.y + 1, area.width - 2, area.height - 2)
    val buf = f.buffer

    warnings.zipWithIndex.foreach { case (warn, idx) =>
      if (idx < innerArea.height.toInt) {
        val source = warn.source.map(s => s"[$s] ").getOrElse("")
        val msg = source + warn.message
        val truncated = if (msg.length > innerArea.width.toInt - 2) msg.take(innerArea.width.toInt - 5) + "..." else msg
        buf.setString(innerArea.x, innerArea.y + idx, truncated, Style(fg = Some(Color.Yellow)))
      }
    }
  }

  private def renderFooter(f: Frame, area: Rect, gs: GeneratingState): Unit = {
    val lines = gs.phase match {
      case GeneratingPhase.Completed =>
        val totalWarnings = gs.logger.map(_.getTotalWarningCount).getOrElse(0)
        val warningNote = if (totalWarnings > 0) s" ($totalWarnings warnings)" else ""
        List(
          Spans.from(Span.styled(s"Press [Esc] or [q] to return to menu$warningNote", Style(fg = Some(Color.DarkGray)))),
          Spans.nostyle("")
        )

      case GeneratingPhase.Failed(err) =>
        List(
          Spans.from(Span.styled(s"✗ Failed: ${err.take(60)}", Style(fg = Some(Color.Red), addModifier = Modifier.BOLD))),
          Spans.from(Span.styled("[Esc] Back to menu", Style(fg = Some(Color.DarkGray))))
        )

      case _ =>
        val recentLog = gs.logger.flatMap(_.getLatestMessage).map(_.message).getOrElse("")
        List(
          Spans.from(
            Span.styled("Running... ", Style(fg = Some(Color.Cyan))),
            Span.styled("(please wait)", Style(fg = Some(Color.DarkGray)))
          ),
          Spans.from(Span.styled(recentLog.take(70), Style(fg = Some(Color.DarkGray))))
        )
    }

    val buf = f.buffer
    lines.zipWithIndex.foreach { case (spans, idx) =>
      buf.setSpans(area.x + 1, area.y + idx, spans, area.width.toInt - 2)
    }
  }
}
