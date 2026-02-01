package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.FocusableId
import typr.cli.tui.Location
import typr.cli.tui.OutputEditorState
import typr.cli.tui.OutputWizardState
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object OutputList {
  def focusableElements(itemCount: Int): List[FocusableId] =
    (0 until itemCount).map(i => FocusableId.ListItem(i)).toList

  def handleKey(state: TuiState, list: AppScreen.OutputList, keyCode: KeyCode): TuiState = {
    val outputs = state.config.outputs.getOrElse(Map.empty).toList.sortBy(_._1)
    val itemCount = outputs.length + 1

    keyCode match {
      case _: KeyCode.Up | _: KeyCode.BackTab =>
        val newIdx = math.max(0, list.selectedIndex - 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Down | _: KeyCode.Tab =>
        val newIdx = math.min(itemCount - 1, list.selectedIndex + 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Enter =>
        if (list.selectedIndex == 0) {
          Navigator.navigateTo(
            state,
            Location.OutputWizard(typr.cli.tui.OutputWizardStep.EnterName),
            AppScreen.OutputWizard(OutputWizardState.initial)
          )
        } else {
          val (outputName, output) = outputs(list.selectedIndex - 1)
          val editorState = OutputEditorState.fromOutput(output)
          Navigator.navigateTo(state, Location.OutputEditor(outputName), AppScreen.OutputEditor(outputName, editorState))
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case c: KeyCode.Char if c.c() == 'd' && list.selectedIndex > 0 =>
        val outputName = outputs(list.selectedIndex - 1)._1
        val newOutputs = state.config.outputs.map(_ - outputName)
        val newConfig = state.config.copy(outputs = newOutputs)
        state.copy(
          config = newConfig,
          hasUnsavedChanges = true,
          statusMessage = Some((s"Deleted output: $outputName", Color.Yellow))
        )

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case _ => state
    }
  }

  def render(f: Frame, state: TuiState, list: AppScreen.OutputList): Unit = {
    val outputs = state.config.outputs.getOrElse(Map.empty).toList.sortBy(_._1)

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(10), Constraint.Length(3))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) =>
      BackButton.isClicked(col, row, 1)
    }
    BackButton.renderClickable(f, chunks(0), "Outputs", isBackHovered)

    val addItem = ListWidget.Item(
      Text.from(
        Spans.from(Span.styled("+ Add new output", Style(fg = Some(Color.Green), addModifier = Modifier.BOLD)))
      )
    )

    val outputItems = outputs.map { case (name, output) =>
      val lang = output.language
      val path = output.path
      val pkg = output.`package`

      val sources = output.sources match {
        case Some(typr.config.generated.StringOrArrayString(s))  => s
        case Some(typr.config.generated.StringOrArrayArray(arr)) => arr.mkString(", ")
        case None                                                => "*"
      }

      ListWidget.Item(
        Text.from(
          Spans.from(
            Span.styled(name, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
            Span.nostyle(" - "),
            Span.styled(s"$lang -> $path ($pkg)", Style(fg = Some(Color.Gray)))
          )
        )
      )
    }

    val allItems = (addItem :: outputItems).toArray

    val listWidget = ListWidget(
      block = Some(
        BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      items = allItems,
      highlightStyle = Style(bg = Some(Color.Blue)),
      highlightSymbol = Some("> ")
    )

    val listState = ListWidget.State(offset = 0, selected = Some(list.selectedIndex))
    f.renderStatefulWidget(listWidget, chunks(1))(listState)

    val hint = if (list.selectedIndex == 0) {
      "[Tab/Up/Down] Navigate  [Enter] Add  [Esc] Back"
    } else {
      "[Tab] Nav  [Enter] Edit  [d] Delete  [Esc] Back"
    }
    val hintPara = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(hintPara, chunks(2))
  }
}
