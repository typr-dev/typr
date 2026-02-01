package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.SourceEditorField
import typr.cli.tui.SourceEditorState
import typr.cli.tui.SourceViewState
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object SourceView {

  def handleKey(state: TuiState, view: AppScreen.SourceView, keyCode: KeyCode): TuiState = {
    val vs = view.state
    val editor = vs.editorState

    keyCode match {
      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case _: KeyCode.Up =>
        val newEditor = editor.copy(selectedField = editor.prevField)
        val newVs = vs.copy(editorState = newEditor)
        state.copy(currentScreen = AppScreen.SourceView(newVs))

      case _: KeyCode.Down =>
        val newEditor = editor.copy(selectedField = editor.nextField)
        val newVs = vs.copy(editorState = newEditor)
        state.copy(currentScreen = AppScreen.SourceView(newVs))

      case c: KeyCode.Char =>
        val newEditor = handleCharInput(editor, c.c())
        val newVs = vs.copy(editorState = newEditor.copy(modified = true))
        state.copy(
          currentScreen = AppScreen.SourceView(newVs),
          hasUnsavedChanges = true
        )

      case _: KeyCode.Backspace =>
        val newEditor = handleBackspace(editor)
        val newVs = vs.copy(editorState = newEditor.copy(modified = true))
        state.copy(
          currentScreen = AppScreen.SourceView(newVs),
          hasUnsavedChanges = true
        )

      case _ => state
    }
  }

  private def handleCharInput(editor: SourceEditorState, c: Char): SourceEditorState = {
    editor.selectedField match {
      case SourceEditorField.Host      => editor.copy(host = editor.host + c)
      case SourceEditorField.Port      => if (c.isDigit) editor.copy(port = editor.port + c) else editor
      case SourceEditorField.Database  => editor.copy(database = editor.database + c)
      case SourceEditorField.Service   => editor.copy(service = editor.service + c)
      case SourceEditorField.Username  => editor.copy(username = editor.username + c)
      case SourceEditorField.Password  => editor.copy(password = editor.password + c)
      case SourceEditorField.Path      => editor.copy(path = editor.path + c)
      case SourceEditorField.SchemaSql => editor.copy(schemaSql = editor.schemaSql + c)
      case SourceEditorField.SpecPath  => editor.copy(specPath = editor.specPath + c)
      case _                           => editor
    }
  }

  private def handleBackspace(editor: SourceEditorState): SourceEditorState = {
    editor.selectedField match {
      case SourceEditorField.Host      => editor.copy(host = editor.host.dropRight(1))
      case SourceEditorField.Port      => editor.copy(port = editor.port.dropRight(1))
      case SourceEditorField.Database  => editor.copy(database = editor.database.dropRight(1))
      case SourceEditorField.Service   => editor.copy(service = editor.service.dropRight(1))
      case SourceEditorField.Username  => editor.copy(username = editor.username.dropRight(1))
      case SourceEditorField.Password  => editor.copy(password = editor.password.dropRight(1))
      case SourceEditorField.Path      => editor.copy(path = editor.path.dropRight(1))
      case SourceEditorField.SchemaSql => editor.copy(schemaSql = editor.schemaSql.dropRight(1))
      case SourceEditorField.SpecPath  => editor.copy(specPath = editor.specPath.dropRight(1))
      case _                           => editor
    }
  }

  def tick(state: TuiState, view: AppScreen.SourceView): TuiState = state

  def render(f: Frame, state: TuiState, view: AppScreen.SourceView): Unit = {
    if (f.size.width < 10 || f.size.height < 6) return // Too small to render

    val vs = view.state

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(5))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, chunks(0), s"Source: ${vs.sourceName}", isBackHovered)

    val area = chunks(1)
    val buf = f.buffer

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerWidth = math.max(0, area.width - 4).toShort
    val innerHeight = math.max(0, area.height - 2).toShort
    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = innerWidth,
      height = innerHeight
    )

    if (innerWidth > 0 && innerHeight > 0) {
      renderConfigContent(buf, innerArea, vs.editorState)
    }

    val hintY = area.y + area.height.toInt - 2
    val hint = "[Esc] Back  [q] Quit"
    val maxHintLen = math.max(0, (area.x + area.width - (area.x + 2)).toInt)
    if (maxHintLen > 0) {
      buf.setString(area.x + 2, hintY, hint.take(maxHintLen), Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderConfigContent(buf: Buffer, area: Rect, editor: SourceEditorState): Unit = {
    if (area.width < 10) return // Too narrow to render

    var y = area.y
    val maxX = area.x + area.width

    val fields = editor.fields
    fields.foreach { field =>
      if (y < area.y + area.height.toInt) {
        val isSelected = field == editor.selectedField
        val (label, value) = field match {
          case SourceEditorField.Type      => ("Type:", editor.sourceType.getOrElse(""))
          case SourceEditorField.Host      => ("Host:", editor.host)
          case SourceEditorField.Port      => ("Port:", editor.port)
          case SourceEditorField.Database  => ("Database:", editor.database)
          case SourceEditorField.Service   => ("Service:", editor.service)
          case SourceEditorField.Username  => ("Username:", editor.username)
          case SourceEditorField.Password  => ("Password:", "*" * editor.password.length)
          case SourceEditorField.Path      => ("Path:", editor.path)
          case SourceEditorField.SchemaSql => ("Schema SQL:", editor.schemaSql.take(40) + (if (editor.schemaSql.length > 40) "..." else ""))
          case SourceEditorField.SpecPath  => ("Spec Path:", editor.specPath)
        }

        val labelStyle = if (isSelected) {
          Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.Gray))
        }

        val valueStyle = if (isSelected) {
          Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }

        val prefix = if (isSelected) "> " else "  "
        val labelX = area.x + 2
        val valueX = labelX + label.length + 1

        if (area.x < maxX) {
          buf.setString(area.x, y, prefix.take((maxX - area.x).toInt), labelStyle)
        }
        if (labelX < maxX) {
          buf.setString(labelX, y, label.take((maxX - labelX).toInt), labelStyle)
        }
        if (valueX < maxX) {
          buf.setString(valueX, y, value.take((maxX - valueX).toInt), valueStyle)
        }
      }
      y += 1
    }
  }
}
