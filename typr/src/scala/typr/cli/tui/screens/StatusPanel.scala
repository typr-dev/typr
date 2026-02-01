package typr.cli.tui.screens

import tui.*
import tui.widgets.*
import typr.cli.tui.SourceError
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.stats

object StatusPanel {

  def render(f: Frame, area: Rect, sourceStatuses: Map[SourceName, SourceStatus]): Unit = {
    val lines = renderSourceStatuses(sourceStatuses)

    val widget = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Sources", Style(fg = Some(Color.Cyan))))),
          borders = Borders.TOP,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(widget, area)
  }

  private def renderSourceStatuses(statuses: Map[SourceName, SourceStatus]): List[Spans] = {
    if (statuses.isEmpty) {
      return List(Spans.from(Span.styled("  No sources configured", Style(fg = Some(Color.DarkGray)))))
    }

    val pending = statuses.collect { case (n, SourceStatus.Pending) => n }
    val loadingMetaDb = statuses.collect { case (n, s: SourceStatus.LoadingMetaDb) => (n, s) }
    val runningSqlGlot = statuses.collect { case (n, s: SourceStatus.RunningSqlGlot) => (n, s) }
    val ready = statuses.collect { case (n, s: SourceStatus.Ready) => (n, s) }
    val failed = statuses.collect { case (n, s: SourceStatus.Failed) => (n, s) }

    val summarySpans = List.newBuilder[Span]

    if (ready.nonEmpty)
      summarySpans += Span.styled(s"${ready.size} ready", Style(fg = Some(Color.Green)))
    if (loadingMetaDb.nonEmpty || runningSqlGlot.nonEmpty) {
      if (ready.nonEmpty) summarySpans += Span.styled(" | ", Style(fg = Some(Color.DarkGray)))
      summarySpans += Span.styled(
        s"${loadingMetaDb.size + runningSqlGlot.size} loading",
        Style(fg = Some(Color.Yellow))
      )
    }
    if (pending.nonEmpty) {
      if (ready.nonEmpty || loadingMetaDb.nonEmpty || runningSqlGlot.nonEmpty)
        summarySpans += Span.styled(" | ", Style(fg = Some(Color.DarkGray)))
      summarySpans += Span.styled(s"${pending.size} pending", Style(fg = Some(Color.DarkGray)))
    }
    if (failed.nonEmpty) {
      summarySpans += Span.styled(" | ", Style(fg = Some(Color.DarkGray)))
      summarySpans += Span.styled(s"${failed.size} failed", Style(fg = Some(Color.Red)))
    }

    val lines = List.newBuilder[Spans]
    lines += Spans.from(Span.nostyle("  ") +: summarySpans.result()*)

    (loadingMetaDb.toList.sortBy(_._1.value) ++ runningSqlGlot.toList.sortBy(_._1.value))
      .take(2)
      .foreach { case (name, status) =>
        val (phase, elapsed) = status match {
          case s: SourceStatus.LoadingMetaDb =>
            val ms = System.currentTimeMillis() - s.startedAt
            ("loading metadb", formatDuration(ms))
          case s: SourceStatus.RunningSqlGlot =>
            val ms = System.currentTimeMillis() - s.sqlGlotStartedAt
            ("running sqlglot", formatDuration(ms))
          case _ => ("", "")
        }
        lines += Spans.from(
          Span.styled(s"    ${name.value}: ", Style(fg = Some(Color.Gray))),
          Span.styled(phase, Style(fg = Some(Color.Yellow))),
          Span.styled(s" $elapsed", Style(fg = Some(Color.DarkGray)))
        )
      }

    failed.toList.sortBy(_._1.value).take(1).foreach { case (name, status) =>
      val errorMsg = status.error.message.take(50)
      val retryHint = if (status.retryCount > 0) s" (retry ${status.retryCount})" else ""
      lines += Spans.from(
        Span.styled(s"    ${name.value}: ", Style(fg = Some(Color.Gray))),
        Span.styled(errorMsg, Style(fg = Some(Color.Red))),
        Span.styled(retryHint, Style(fg = Some(Color.DarkGray)))
      )
    }

    lines.result()
  }

  private def formatDuration(ms: Long): String = {
    if (ms < 1000) s"${ms}ms"
    else f"${ms / 1000.0}%.1fs"
  }

  def shouldShow(statuses: Map[SourceName, SourceStatus]): Boolean = {
    if (statuses.isEmpty) return false
    statuses.values.exists {
      case _: SourceStatus.Ready => false
      case _                     => true
    }
  }

  def summaryLine(statuses: Map[SourceName, SourceStatus]): Spans = {
    if (statuses.isEmpty) {
      Spans.from(Span.styled("No sources", Style(fg = Some(Color.DarkGray))))
    } else {
      val ready = statuses.count(_._2.isInstanceOf[SourceStatus.Ready])
      val total = statuses.size
      val failed = statuses.count(_._2.isInstanceOf[SourceStatus.Failed])

      if (ready == total) {
        val totalStats = statuses.values.collect { case s: SourceStatus.Ready => s.metaDb.stats }
        val totalTables = totalStats.map(_.tableCount).sum
        val totalCols = totalStats.map(_.columnCount).sum
        Spans.from(
          Span.styled(s"$total sources", Style(fg = Some(Color.Green))),
          Span.styled(s" ($totalTables tables, $totalCols cols)", Style(fg = Some(Color.DarkGray)))
        )
      } else if (failed > 0) {
        Spans.from(
          Span.styled(s"$ready/$total ready", Style(fg = Some(Color.Yellow))),
          Span.styled(s", $failed failed", Style(fg = Some(Color.Red)))
        )
      } else {
        Spans.from(
          Span.styled(s"$ready/$total ready", Style(fg = Some(Color.Yellow)))
        )
      }
    }
  }
}
