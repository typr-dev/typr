package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.Location
import typr.cli.tui.SourceEditorField
import typr.cli.tui.SourceEditorState
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.ConnectionTester

object SourceEditor {
  private var tester: Option[ConnectionTester] = None

  def handleKey(state: TuiState, editor: AppScreen.SourceEditor, keyCode: KeyCode): TuiState = {
    val es = editor.state

    keyCode match {
      case _: KeyCode.Esc =>
        tester = None
        Navigator.goBack(state)

      case _: KeyCode.Tab | _: KeyCode.Down =>
        val newState = es.copy(selectedField = es.nextField)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.BackTab | _: KeyCode.Up =>
        val newState = es.copy(selectedField = es.prevField)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Enter =>
        es.selectedField match {
          case SourceEditorField.Type =>
            val newType = es.typeSelect.selectedValue
            val newState = es.copy(
              sourceType = Some(newType),
              modified = true
            )
            state.copy(currentScreen = editor.copy(state = newState))
          case SourceEditorField.TestConnection =>
            val newTester = new ConnectionTester()
            tester = Some(newTester)
            newTester.testSource(es.toJson)
            val newState = es.copy(connectionResult = ConnectionTestResult.Testing)
            state.copy(currentScreen = editor.copy(state = newState))
          case SourceEditorField.Save =>
            saveSource(state, editor)
          case _ =>
            saveSource(state, editor)
        }

      case _: KeyCode.Left if es.selectedField == SourceEditorField.Type =>
        val newTypeSelect = es.typeSelect.prev
        val newState = es.copy(
          typeSelect = newTypeSelect,
          sourceType = Some(newTypeSelect.selectedValue),
          modified = true
        )
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Right if es.selectedField == SourceEditorField.Type =>
        val newTypeSelect = es.typeSelect.next
        val newState = es.copy(
          typeSelect = newTypeSelect,
          sourceType = Some(newTypeSelect.selectedValue),
          modified = true
        )
        state.copy(currentScreen = editor.copy(state = newState))

      case c: KeyCode.Char if c.c() == 't' && es.selectedField == SourceEditorField.Type =>
        val newTester = new ConnectionTester()
        tester = Some(newTester)
        newTester.testSource(es.toJson)
        val newState = es.copy(connectionResult = ConnectionTestResult.Testing)
        state.copy(currentScreen = editor.copy(state = newState))

      case c: KeyCode.Char =>
        val newState = es.selectedField match {
          case SourceEditorField.Type           => es
          case SourceEditorField.Host           => es.copy(host = es.host + c.c(), modified = true)
          case SourceEditorField.Port           => es.copy(port = es.port + c.c(), modified = true)
          case SourceEditorField.Database       => es.copy(database = es.database + c.c(), modified = true)
          case SourceEditorField.Service        => es.copy(service = es.service + c.c(), modified = true)
          case SourceEditorField.Username       => es.copy(username = es.username + c.c(), modified = true)
          case SourceEditorField.Password       => es.copy(password = es.password + c.c(), modified = true)
          case SourceEditorField.Path           => es.copy(path = es.path + c.c(), modified = true)
          case SourceEditorField.SchemaSql      => es.copy(schemaSql = es.schemaSql + c.c(), modified = true)
          case SourceEditorField.SpecPath       => es.copy(specPath = es.specPath + c.c(), modified = true)
          case SourceEditorField.TestConnection => es
          case SourceEditorField.Save           => es
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Backspace =>
        val newState = es.selectedField match {
          case SourceEditorField.Type           => es
          case SourceEditorField.Host           => es.copy(host = es.host.dropRight(1), modified = true)
          case SourceEditorField.Port           => es.copy(port = es.port.dropRight(1), modified = true)
          case SourceEditorField.Database       => es.copy(database = es.database.dropRight(1), modified = true)
          case SourceEditorField.Service        => es.copy(service = es.service.dropRight(1), modified = true)
          case SourceEditorField.Username       => es.copy(username = es.username.dropRight(1), modified = true)
          case SourceEditorField.Password       => es.copy(password = es.password.dropRight(1), modified = true)
          case SourceEditorField.Path           => es.copy(path = es.path.dropRight(1), modified = true)
          case SourceEditorField.SchemaSql      => es.copy(schemaSql = es.schemaSql.dropRight(1), modified = true)
          case SourceEditorField.SpecPath       => es.copy(specPath = es.specPath.dropRight(1), modified = true)
          case SourceEditorField.TestConnection => es
          case SourceEditorField.Save           => es
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case _ => state
    }
  }

  def tick(state: TuiState, editor: AppScreen.SourceEditor): TuiState = {
    tester match {
      case Some(t) =>
        val result = t.getResult
        if (result != editor.state.connectionResult) {
          val newState = editor.state.copy(connectionResult = result)
          result match {
            case ConnectionTestResult.Success(_) | ConnectionTestResult.Failure(_) =>
              tester = None
            case _ =>
          }
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }
      case None => state
    }
  }

  private def saveSource(state: TuiState, editor: AppScreen.SourceEditor): TuiState = {
    val es = editor.state
    val sourceName = editor.sourceName
    val newJson = es.toJson

    val newSources = state.config.sources.getOrElse(Map.empty) + (sourceName -> newJson)
    val newConfig = state.config.copy(sources = Some(newSources))

    tester = None
    val updatedState = state.copy(
      config = newConfig,
      hasUnsavedChanges = true,
      currentScreen = AppScreen.SourceList(selectedIndex = 1, testResults = Map.empty),
      statusMessage = Some((s"Updated source: $sourceName", Color.Green))
    )
    Navigator.goBack(updatedState)
  }

  def render(f: Frame, state: TuiState, editor: AppScreen.SourceEditor): Unit = {
    val es = editor.state

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Length(3),
        Constraint.Min(10),
        Constraint.Length(5),
        Constraint.Length(3)
      )
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, chunks(0), s"Edit Source: ${editor.sourceName}", isBackHovered)

    val titleBlock = BlockWidget(
      title = Some(
        Spans.from(
          Span.styled(s"Edit Source: ${editor.sourceName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD))
        )
      ),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(titleBlock, chunks(1))

    val modifiedIndicator = if (es.modified) " [modified]" else ""
    val titleText = Spans.from(
      Span.styled(es.sourceType.getOrElse("unknown"), Style(fg = Some(Color.Yellow))),
      Span.styled(modifiedIndicator, Style(fg = Some(Color.Red)))
    )
    f.buffer.setSpans(chunks(1).x + 2, chunks(1).y + 1, titleText, chunks(1).width.toInt - 4)

    renderFields(f, chunks(2), es)
    renderTestResult(f, chunks(3), es)

    val hint = "[Tab/Down] Next  [Shift+Tab/Up] Prev  [t] Test  [Enter] Save  [Esc] Back"
    val hintPara = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(hintPara, chunks(4))
  }

  private def renderFields(f: Frame, area: Rect, es: SourceEditorState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Fields", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(area.x + 2, area.y + 1, area.width - 4, area.height - 2)
    val buf = f.buffer

    val fieldList = es.fields
    fieldList.zipWithIndex.foreach { case (field, idx) =>
      val y = innerArea.y + idx
      if (y < innerArea.y + innerArea.height.toInt) {
        val isSelected = es.selectedField == field
        val (label, value) = field match {
          case SourceEditorField.Type           => ("Type", es.typeSelect.selectedLabel)
          case SourceEditorField.Host           => ("Host", es.host)
          case SourceEditorField.Port           => ("Port", es.port)
          case SourceEditorField.Database       => ("Database", es.database)
          case SourceEditorField.Service        => ("Service", es.service)
          case SourceEditorField.Username       => ("Username", es.username)
          case SourceEditorField.Password       => ("Password", "*" * es.password.length)
          case SourceEditorField.Path           => ("Path", es.path)
          case SourceEditorField.SchemaSql      => ("Schema SQL", es.schemaSql)
          case SourceEditorField.SpecPath       => ("Spec Path", es.specPath)
          case SourceEditorField.TestConnection => ("[Test Connection]", "")
          case SourceEditorField.Save           => ("[Save]", "")
        }

        val labelStyle =
          if (isSelected) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))
        val valueStyle =
          if (isSelected) Style(fg = Some(Color.White), addModifier = Modifier.BOLD) else Style(fg = Some(Color.White))
        val cursor = if (isSelected && field != SourceEditorField.Type) "_" else ""

        val typeHint = if (field == SourceEditorField.Type && isSelected) " [</> t]" else ""

        val line = Spans.from(
          Span.styled(f"$label%-12s", labelStyle),
          Span.styled(": ", Style(fg = Some(Color.DarkGray))),
          Span.styled(value + cursor, valueStyle),
          Span.styled(typeHint, Style(fg = Some(Color.DarkGray)))
        )
        buf.setSpans(innerArea.x, y, line, innerArea.width.toInt)
      }
    }
  }

  private def renderTestResult(f: Frame, area: Rect, es: SourceEditorState): Unit = {
    val (icon, color, message) = es.connectionResult match {
      case ConnectionTestResult.Pending      => ("○", Color.Gray, "Not tested - press 't' on Type field to test")
      case ConnectionTestResult.Testing      => ("◕", Color.Cyan, "Testing connection...")
      case ConnectionTestResult.Success(msg) => ("●", Color.Green, s"Success: $msg")
      case ConnectionTestResult.Failure(err) => ("✗", Color.Red, s"Failed: $err")
    }

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Connection Test", Style(fg = Some(color))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(area.x + 2, area.y + 1, area.width - 4, area.height - 2)
    val buf = f.buffer

    val line = Spans.from(
      Span.styled(s"$icon ", Style(fg = Some(color))),
      Span.styled(message.take(innerArea.width.toInt - 4), Style(fg = Some(color)))
    )
    buf.setSpans(innerArea.x, innerArea.y, line, innerArea.width.toInt)

    es.connectionResult match {
      case ConnectionTestResult.Failure(err) if err.length > innerArea.width.toInt - 4 =>
        val remaining = err.drop(innerArea.width.toInt - 4)
        val lines = wrapText(remaining, innerArea.width.toInt - 2)
        lines.take(2).zipWithIndex.foreach { case (line, idx) =>
          buf.setString(innerArea.x + 2, innerArea.y + 1 + idx, line, Style(fg = Some(Color.Red)))
        }
      case _ =>
    }
  }

  private def wrapText(text: String, maxWidth: Int): List[String] = {
    if (text.length <= maxWidth) {
      List(text)
    } else {
      val words = text.split(" ")
      val lines = scala.collection.mutable.ListBuffer[String]()
      var currentLine = ""

      words.foreach { word =>
        if (currentLine.isEmpty) {
          currentLine = word
        } else if (currentLine.length + 1 + word.length <= maxWidth) {
          currentLine = currentLine + " " + word
        } else {
          lines += currentLine
          currentLine = word
        }
      }
      if (currentLine.nonEmpty) {
        lines += currentLine
      }
      lines.toList
    }
  }
}
