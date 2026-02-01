package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, Box, Hint}
import tui.widgets.*
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState

/** React-based rendering for ConnectionTest screen. */
object ConnectionTestReact {

  /** Props for ConnectionTest component */
  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      sourceName: String,
      result: ConnectionTestResult,
      onTest: () => Unit,
      onRetry: () => Unit
  )

  def component(props: Props): Component = {
    Component.named("ConnectionTest") { (ctx, area) =>
      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Length(3), Constraint.Min(8), Constraint.Length(3))
      ).split(area)

      BackButton(s"Connection Test: ${props.sourceName}")(props.callbacks.goBack()).render(ctx, chunks(0))

      val title = ParagraphWidget(
        block = Some(
          BlockWidget(
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded
          )
        ),
        text = Text.from(Spans.from(Span.styled(s"Source: ${props.sourceName}", Style(fg = Some(Color.Yellow)))))
      )
      ctx.frame.renderWidget(title, chunks(1))

      renderResult(ctx, chunks(2), props.result, props.onTest)

      val hint = props.result match {
        case ConnectionTestResult.Pending => "[Enter] Test  [Esc] Back  [q] Quit"
        case ConnectionTestResult.Testing => "[Esc] Cancel  [q] Quit"
        case _                            => "[r] Retry  [Enter/Esc] Back  [q] Quit"
      }
      Hint(hint).render(ctx, chunks(3))
    }
  }

  private def renderResult(ctx: RenderContext, area: Rect, result: ConnectionTestResult, onTest: () => Unit): Unit = {
    val (icon, color, message) = result match {
      case ConnectionTestResult.Pending =>
        ("○", Color.Gray, "Press Enter to test connection")
      case ConnectionTestResult.Testing =>
        ("◕", Color.Cyan, "Testing connection...")
      case ConnectionTestResult.Success(msg) =>
        ("●", Color.Green, s"Success: $msg")
      case ConnectionTestResult.Failure(err) =>
        ("✗", Color.Red, s"Failed: $err")
    }

    val lines = List(
      Spans.nostyle(""),
      Spans.from(
        Span.styled(s"$icon ", Style(fg = Some(color))),
        Span.styled(message, Style(fg = Some(color)))
      ),
      Spans.nostyle("")
    )

    val resultPara = ParagraphWidget(
      block = Some(BlockWidget(borders = Borders.ALL, borderType = BlockWidget.BorderType.Rounded)),
      text = Text.fromSpans(lines*)
    )
    ctx.frame.renderWidget(resultPara, area)

    result match {
      case ConnectionTestResult.Pending =>
        ctx.onClick(area)(onTest())
      case _ => ()
    }
  }
}
