package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, Button, FormButtonField, Hint}
import tui.widgets.*
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.SourceEditorField
import typr.cli.tui.SourceEditorState
import typr.config.generated.StringOrArrayString
import typr.config.generated.StringOrArrayArray

/** React-based rendering for SourceView screen. */
object SourceViewReact {

  /** Props for SourceView component */
  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      sourceName: String,
      editorState: SourceEditorState
  )

  def component(props: Props): Component = {
    Component.named("SourceView") { (ctx, area) =>
      val (selectedIndex, setSelectedIndex) = ctx.useState(0)

      // Find outputs that use this source
      val outputsUsingSource = findOutputsUsingSource(props.globalState, props.sourceName)

      if (area.width >= 10 && area.height >= 6) {
        val chunks = Layout(
          direction = Direction.Vertical,
          margin = Margin(1),
          constraints = Array(Constraint.Length(3), Constraint.Min(5))
        ).split(area)

        BackButton(s"Source: ${props.sourceName}")(props.callbacks.goBack()).render(ctx, chunks(0))

        val contentArea = chunks(1)
        val block = BlockWidget(
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
        ctx.frame.renderWidget(block, contentArea)

        val innerWidth = math.max(0, contentArea.width - 4).toShort
        val innerHeight = math.max(0, contentArea.height - 2).toShort
        val innerArea = Rect(
          x = (contentArea.x + 2).toShort,
          y = (contentArea.y + 1).toShort,
          width = innerWidth,
          height = innerHeight
        )

        // Calculate how much space we need at the bottom
        val outputCount = math.min(outputsUsingSource.size, 3)
        val outputSectionHeight = if (outputsUsingSource.nonEmpty) outputCount + 2 else 0 // +1 for header, +1 for spacing
        val bottomReserved = 3 + outputSectionHeight // 1 for remove button, 1 for hint, 1 for spacing

        // Render config content in available space above the bottom section
        val configHeight = math.max(1, innerHeight - bottomReserved)
        val configArea = Rect(innerArea.x, innerArea.y, innerArea.width, configHeight.toShort)
        if (innerWidth > 0 && configHeight > 0) {
          renderConfigContent(ctx, configArea, props.editorState, selectedIndex, setSelectedIndex)
        }

        // Render "Used by outputs" section below config
        val outputsY = (innerArea.y + configHeight + 1).toShort
        renderOutputLinks(ctx, props, outputsUsingSource, innerArea.x.toShort, outputsY, innerWidth)

        // Add Remove button
        val removeY = (contentArea.y + contentArea.height - 3).toShort
        FormButtonField(
          label = "",
          buttonLabel = "Remove Source",
          isPrimary = false,
          isFocused = false,
          labelWidth = 0,
          onFocus = () => (),
          onPress = () => removeSource(props.sourceName, props.callbacks),
          hoverPosition = props.globalState.hoverPosition
        ).render(ctx, Rect((contentArea.x + 2).toShort, removeY, 20, 1))

        val hintY = (contentArea.y + contentArea.height - 2).toShort
        val hint = "[Up/Down] Navigate  [Esc] Back  [q] Quit"
        val maxHintLen = math.max(0, (contentArea.x + contentArea.width - (contentArea.x + 2)).toInt)
        if (maxHintLen > 0) {
          ctx.buffer.setString((contentArea.x + 2).toShort, hintY, hint.take(maxHintLen), Style(fg = Some(Color.DarkGray)))
        }
      }
    }
  }

  /** Find outputs that reference this source */
  private def findOutputsUsingSource(state: GlobalState, sourceName: String): List[String] = {
    state.config.outputs
      .map { outputs =>
        outputs
          .filter { case (_, output) =>
            output.sources match {
              case Some(StringOrArrayString(s))  => s == sourceName
              case Some(StringOrArrayArray(arr)) => arr.contains(sourceName)
              case None                          => true // If no sources specified, all sources are used
            }
          }
          .keys
          .toList
          .sorted
      }
      .getOrElse(Nil)
  }

  /** Render clickable output links */
  private def renderOutputLinks(
      ctx: RenderContext,
      props: Props,
      outputs: List[String],
      x: Short,
      y: Short,
      width: Int
  ): Unit = {
    if (outputs.isEmpty) return

    val buf = ctx.buffer
    val maxX = x + width

    buf.setString(x, y, "Used by outputs:", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD))

    var currentY = (y + 1).toShort
    outputs.take(3).foreach { outputName =>
      val linkText = s"  → $outputName"
      val linkStyle = Style(fg = Some(Color.Yellow))
      val hoverStyle = Some(Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD))

      Button(
        label = linkText.take(width),
        style = linkStyle,
        hoverStyle = hoverStyle,
        hoverPosition = props.globalState.hoverPosition
      )(props.callbacks.navigateTo(Location.OutputEditor(outputName)))
        .render(ctx, Rect(x, currentY, math.min(linkText.length, width).toShort, 1))

      currentY = (currentY + 1).toShort
    }

    if (outputs.size > 3) {
      buf.setString(x, currentY, s"  ... and ${outputs.size - 3} more", Style(fg = Some(Color.DarkGray)))
    }
  }

  /** Remove a source from config and cascade remove from all outputs that reference it */
  private def removeSource(sourceName: String, callbacks: GlobalCallbacks): Unit = {
    callbacks.updateConfig { config =>
      // Remove the source
      val newSources = config.sources.map(_.removed(sourceName))

      // Update outputs to remove references to this source
      val newOutputs = config.outputs.map { outputs =>
        outputs.map { case (outputName, output) =>
          val updatedSources = output.sources.flatMap {
            case StringOrArrayString(s) if s == sourceName => None
            case StringOrArrayString(s)                    => Some(StringOrArrayString(s))
            case StringOrArrayArray(arr) =>
              val filtered = arr.filterNot(_ == sourceName)
              if (filtered.isEmpty) None
              else if (filtered.length == 1) Some(StringOrArrayString(filtered.head))
              else Some(StringOrArrayArray(filtered))
          }
          outputName -> output.copy(sources = updatedSources)
        }
      }

      config.copy(sources = newSources, outputs = newOutputs)
    }
    callbacks.goBack()
  }

  private def renderConfigContent(
      ctx: RenderContext,
      area: Rect,
      editor: SourceEditorState,
      selectedIndex: Int,
      setSelectedIndex: Int => Unit
  ): Unit = {
    if (area.width < 10) return

    val buf = ctx.buffer
    var y = area.y
    val maxX = area.x + area.width

    val fields = editor.fields
    fields.zipWithIndex.foreach { case (field, idx) =>
      if (y < area.y + area.height.toInt) {
        val isSelected = idx == selectedIndex
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
          Style(fg = Some(Color.Yellow), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.Gray))
        }

        val valueStyle = if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
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
          buf.setString(labelX.toShort, y, label.take((maxX - labelX).toInt), labelStyle)
        }
        if (valueX < maxX) {
          buf.setString(valueX.toShort, y, value.take((maxX - valueX).toInt), valueStyle)
        }

        val rowArea = Rect(area.x, y, area.width, 1)
        ctx.onClick(rowArea)(setSelectedIndex(idx))
        ctx.onHover(rowArea)(setSelectedIndex(idx))
      }
      y = (y + 1).toShort
    }

    val maxIdx = math.max(0, fields.length - 1)
    ctx.onScrollUp(area)(setSelectedIndex(math.max(0, selectedIndex - 1)))
    ctx.onScrollDown(area)(setSelectedIndex(math.min(maxIdx, selectedIndex + 1)))
  }
}
