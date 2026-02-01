package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.ConnectionTester

object ConnectionTest {
  private var tester: Option[ConnectionTester] = None

  def handleKey(state: TuiState, test: AppScreen.ConnectionTest, keyCode: KeyCode): TuiState = keyCode match {
    case _: KeyCode.Esc =>
      tester = None
      Navigator.goBack(state)

    case _: KeyCode.Enter =>
      test.result match {
        case ConnectionTestResult.Pending =>
          state.config.sources.flatMap(_.get(test.sourceName)) match {
            case Some(sourceJson) =>
              val newTester = new ConnectionTester()
              tester = Some(newTester)
              newTester.testSource(sourceJson)
              state.copy(currentScreen = test.copy(result = ConnectionTestResult.Testing))
            case None =>
              state.copy(currentScreen = test.copy(result = ConnectionTestResult.Failure("Source not found")))
          }
        case _ =>
          tester = None
          Navigator.goBack(state)
      }

    case c: KeyCode.Char if c.c() == 'r' =>
      state.config.sources.flatMap(_.get(test.sourceName)) match {
        case Some(sourceJson) =>
          val newTester = new ConnectionTester()
          tester = Some(newTester)
          newTester.testSource(sourceJson)
          state.copy(currentScreen = test.copy(result = ConnectionTestResult.Testing))
        case None =>
          state.copy(currentScreen = test.copy(result = ConnectionTestResult.Failure("Source not found")))
      }

    case c: KeyCode.Char if c.c() == 'q' =>
      tester = None
      Navigator.handleEscape(state)

    case _ => state
  }

  def tick(state: TuiState, test: AppScreen.ConnectionTest): TuiState = {
    tester match {
      case Some(t) =>
        val newResult = t.getResult
        if (newResult != test.result) {
          state.copy(currentScreen = test.copy(result = newResult))
        } else {
          state
        }
      case None => state
    }
  }

  def render(f: Frame, state: TuiState, test: AppScreen.ConnectionTest): Unit = {
    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Length(3), Constraint.Min(8), Constraint.Length(3))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, chunks(0), s"Connection Test: ${test.sourceName}", isBackHovered)

    val title = ParagraphWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.from(Spans.from(Span.styled(s"Source: ${test.sourceName}", Style(fg = Some(Color.Yellow)))))
    )
    f.renderWidget(title, chunks(1))

    val (icon, color, message) = test.result match {
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
    f.renderWidget(resultPara, chunks(2))

    val hint = test.result match {
      case ConnectionTestResult.Pending => "[Enter] Test  [Esc] Back  [q] Quit"
      case ConnectionTestResult.Testing => "[Esc] Cancel  [q] Quit"
      case _                            => "[r] Retry  [Enter/Esc] Back  [q] Quit"
    }
    val hintPara = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(hintPara, chunks(3))
  }
}
