package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.SourceEditorField
import typr.cli.tui.SourceEditorState

/** React-based SourceEditor with form components */
object SourceEditorReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      sourceName: String,
      editorState: SourceEditorState
  )

  def component(props: Props): Component = {
    Component.named("SourceEditorReact") { (ctx, area) =>
      val es = props.editorState
      val hoverPos = props.globalState.hoverPosition

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(
          Constraint.Length(3),
          Constraint.Min(10),
          Constraint.Length(5),
          Constraint.Length(2)
        )
      ).split(area)

      // Header with back button
      BackButton(s"Edit Source: ${props.sourceName}")(props.callbacks.goBack()).render(ctx, chunks(0))

      // Main content area - Fields
      val contentArea = chunks(1)
      val block = BlockWidget(
        title = Some(Spans.from(Span.styled("Configuration", Style(fg = Some(Color.Cyan))))),
        borders = Borders.ALL,
        borderType = BlockWidget.BorderType.Rounded
      )
      ctx.frame.renderWidget(block, contentArea)

      val innerArea = Rect(
        x = (contentArea.x + 2).toShort,
        y = (contentArea.y + 1).toShort,
        width = (contentArea.width - 4).toShort,
        height = (contentArea.height - 2).toShort
      )

      val labelWidth = 14
      var y = innerArea.y.toInt

      // Render fields based on source type
      val fieldList = es.fields

      fieldList.foreach { field =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val isFocused = es.selectedField == field

          field match {
            case SourceEditorField.Type =>
              val typeOptions = es.typeSelect.options.toList
              FormSelectField(
                label = "Type",
                options = typeOptions,
                value = es.typeSelect.selectedValue,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Type)),
                onChange = (v: String) =>
                  props.callbacks.updateSourceEditor { s =>
                    val newSelect = s.typeSelect.copy(selectedIndex = s.typeSelect.options.indexWhere(_._1 == v))
                    s.copy(typeSelect = newSelect, sourceType = Some(v), modified = true)
                  },
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Host =>
              FormTextField(
                label = "Host",
                value = es.host,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Host)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Port =>
              FormTextField(
                label = "Port",
                value = es.port,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Port)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Database =>
              FormTextField(
                label = "Database",
                value = es.database,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Database)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Service =>
              FormTextField(
                label = "Service",
                value = es.service,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Service)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Username =>
              FormTextField(
                label = "Username",
                value = es.username,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Username)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Password =>
              FormTextField(
                label = "Password",
                value = "*" * es.password.length,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Password)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.Path =>
              FormTextField(
                label = "Path",
                value = es.path,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Path)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.SchemaSql =>
              FormTextField(
                label = "Schema SQL",
                value = es.schemaSql,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.SchemaSql)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.SpecPath =>
              FormTextField(
                label = "Spec Path",
                value = es.specPath,
                isFocused = isFocused,
                labelWidth = labelWidth,
                onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.SpecPath)),
                hoverPosition = hoverPos
              ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))

            case SourceEditorField.TestConnection | SourceEditorField.Save =>
              () // These are rendered as buttons separately below
          }

          if (field != SourceEditorField.TestConnection && field != SourceEditorField.Save) {
            y += 1
          }
        }
      }

      // Add buttons row
      y += 1
      if (y < innerArea.y + innerArea.height.toInt) {
        val buttonY = y.toShort
        var buttonX = innerArea.x.toInt

        // Test Connection button
        FormButtonField(
          label = "",
          buttonLabel = "Test Connection",
          isPrimary = false,
          isFocused = es.selectedField == SourceEditorField.TestConnection,
          labelWidth = 0,
          onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.TestConnection)),
          onPress = () => props.callbacks.testSourceConnection(),
          hoverPosition = hoverPos
        ).render(ctx, Rect(buttonX.toShort, buttonY, 20, 1))
        buttonX += 20

        // Save button
        FormButtonField(
          label = "",
          buttonLabel = "Save",
          isPrimary = true,
          isFocused = es.selectedField == SourceEditorField.Save,
          labelWidth = 0,
          onFocus = () => props.callbacks.updateSourceEditor(_.copy(selectedField = SourceEditorField.Save)),
          onPress = () => props.callbacks.saveSourceEditor(),
          hoverPosition = hoverPos
        ).render(ctx, Rect(buttonX.toShort, buttonY, 10, 1))
      }

      // Connection test result area
      renderTestResult(ctx, chunks(2), es)

      // Hints
      Hint("[Tab/Arrow] Navigate  [Enter] Save  [Esc] Cancel").render(ctx, chunks(3))
    }
  }

  private def renderTestResult(ctx: RenderContext, area: Rect, es: SourceEditorState): Unit = {
    val (icon, color, message) = es.connectionResult match {
      case ConnectionTestResult.Pending      => ("○", Color.Gray, "Not tested - click 'Test Connection' to test")
      case ConnectionTestResult.Testing      => ("◕", Color.Cyan, "Testing connection...")
      case ConnectionTestResult.Success(msg) => ("●", Color.Green, s"Success: $msg")
      case ConnectionTestResult.Failure(err) => ("✗", Color.Red, s"Failed: $err")
    }

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Connection Test", Style(fg = Some(color))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerArea = Rect(
      x = (area.x + 2).toShort,
      y = (area.y + 1).toShort,
      width = (area.width - 4).toShort,
      height = (area.height - 2).toShort
    )

    val line = Spans.from(
      Span.styled(s"$icon ", Style(fg = Some(color))),
      Span.styled(message.take(innerArea.width.toInt - 4), Style(fg = Some(color)))
    )
    ctx.buffer.setSpans(innerArea.x, innerArea.y, line, innerArea.width.toInt)
  }
}
