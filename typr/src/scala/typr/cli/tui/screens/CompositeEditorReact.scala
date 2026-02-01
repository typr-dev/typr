package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.CompositeEditorState
import typr.cli.tui.CompositeEditorTab
import typr.cli.tui.FieldEditState
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.AlignedSourceEditState

/** React-based CompositeEditor with mouse support */
object CompositeEditorReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      typeName: String,
      editorState: CompositeEditorState
  )

  /** UI state managed locally with useState */
  case class UiState(
      selectedTab: CompositeEditorTab,
      setSelectedTab: CompositeEditorTab => Unit,
      selectedFieldIndex: Int,
      setSelectedFieldIndex: Int => Unit,
      selectedAlignedSourceIndex: Int,
      setSelectedAlignedSourceIndex: Int => Unit
  )

  def component(props: Props): Component = {
    Component.named("CompositeEditorReact") { (ctx, area) =>
      val editorState = props.editorState

      // Initialize local UI state
      val (selectedTab, setSelectedTab) = ctx.useState(editorState.selectedTab)
      val (selectedFieldIndex, setSelectedFieldIndex) = ctx.useState(editorState.selectedFieldIndex)
      val (selectedAlignedSourceIndex, setSelectedAlignedSourceIndex) = ctx.useState(editorState.selectedAlignedSourceIndex)

      val ui = UiState(
        selectedTab = selectedTab,
        setSelectedTab = setSelectedTab,
        selectedFieldIndex = selectedFieldIndex,
        setSelectedFieldIndex = setSelectedFieldIndex,
        selectedAlignedSourceIndex = selectedAlignedSourceIndex,
        setSelectedAlignedSourceIndex = setSelectedAlignedSourceIndex
      )

      val chunks = Layout(
        direction = Direction.Horizontal,
        margin = Margin(1),
        constraints = Array(Constraint.Percentage(65), Constraint.Percentage(35))
      ).split(area)

      renderMainPanel(ctx, chunks(0), props, ui)
      renderSidebar(ctx, chunks(1), props)
    }
  }

  private def renderMainPanel(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val editorState = props.editorState

    val title = if (editorState.modified) {
      Spans.from(
        Span.styled(s"Edit Domain Type: ${props.typeName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
        Span.styled(" *", Style(fg = Some(Color.Yellow)))
      )
    } else {
      Spans.from(Span.styled(s"Edit Domain Type: ${props.typeName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))
    }

    val block = BlockWidget(
      title = Some(title),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerArea = Rect(
      x = (area.x + 2).toShort,
      y = (area.y + 2).toShort,
      width = math.max(10, area.width - 4).toShort,
      height = math.max(5, area.height - 4).toShort
    )

    renderTabs(ctx, innerArea, props, ui)
    val contentY = (innerArea.y + 2).toShort
    val contentHeight = math.max(3, innerArea.height - 3).toShort
    val contentArea = Rect(innerArea.x, contentY, innerArea.width, contentHeight)

    ui.selectedTab match {
      case CompositeEditorTab.Fields      => renderFieldsTab(ctx, contentArea, props, ui)
      case CompositeEditorTab.Projections => renderProjectionsTab(ctx, contentArea, props, ui)
      case CompositeEditorTab.Alignment   => renderAlignmentTab(ctx, contentArea, props, ui)
      case CompositeEditorTab.Options     => renderOptionsTab(ctx, contentArea, props)
    }
  }

  private def renderTabs(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    var x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val maxX: Int = area.x + area.width

    val tabs = List(
      ("Fields", CompositeEditorTab.Fields),
      ("Sources", CompositeEditorTab.Projections),
      ("Alignment", CompositeEditorTab.Alignment),
      ("Options", CompositeEditorTab.Options)
    )

    tabs.foreach { case (label, tab) =>
      val isSelected = ui.selectedTab == tab
      val style =
        if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else Style(fg = Some(Color.Gray), bg = Some(Color.DarkGray))

      val text = s" $label "
      safeSetString(ctx.buffer, x, y, maxX, text, style)

      val tabArea = Rect(x, y, text.length.toShort, 1)
      ctx.onClick(tabArea) {
        ui.setSelectedTab(tab)
      }
      x = (x + text.length + 1).toShort
    }
  }

  private def renderFieldsTab(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val editorState = props.editorState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Canonical Fields", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    if (editorState.fields.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No fields defined.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Press [a] to add a field.", Style(fg = Some(Color.DarkGray)))
    } else {
      val rowHeight = 2
      val visibleRows = math.max(1, (height - 4) / rowHeight)
      val scrollOffset = math.max(0, ui.selectedFieldIndex - visibleRows + 1)
      val endIdx = math.min(editorState.fields.size, scrollOffset + visibleRows)
      val visibleFields = editorState.fields.slice(scrollOffset, endIdx)

      visibleFields.zipWithIndex.foreach { case (field, relIdx) =>
        if (y < area.y + height - 3) {
          val idx = scrollOffset + relIdx
          val isSelected = idx == ui.selectedFieldIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.White), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
            safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }

          safeSetString(ctx.buffer, x, y, maxX, s"$prefix${field.name}", Style(fg = Some(Color.Cyan), bg = rowBg, addModifier = Modifier.BOLD))
          val typeStr = field.compactType
          val typeX = (x + 2 + field.name.length + 2).toShort
          safeSetString(ctx.buffer, typeX, y, maxX, typeStr, Style(fg = Some(Color.Yellow), bg = rowBg))
          y = (y + 1).toShort

          if (field.description.nonEmpty && y < area.y + height - 2) {
            val descStyle = Style(fg = Some(Color.DarkGray), bg = rowBg)
            safeSetString(ctx.buffer, (x + 4).toShort, y, maxX, field.description.take(width - 6), descStyle)
          }

          val rowArea = Rect(x, (y - 1).toShort, width.toShort, 2)
          ctx.onClick(rowArea) {
            ui.setSelectedFieldIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    if (editorState.addingField) {
      val newField = FieldEditState.empty
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Adding field:", Style(fg = Some(Color.Green)))
      y = (y + 1).toShort
      val inputStyle = Style(fg = Some(Color.White), bg = Some(Color.Blue))
      safeSetString(ctx.buffer, x, y, maxX, " " * 30, inputStyle)
      safeSetString(ctx.buffer, x, y, maxX, s"Name: \u2588", inputStyle)
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedFieldIndex - 1)
      ui.setSelectedFieldIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, editorState.fields.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedFieldIndex + 1)
      ui.setSelectedFieldIndex(newIdx)
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[a] Add  [d] Delete  [Enter] Edit  [Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  private def renderProjectionsTab(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val editorState = props.editorState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Aligned Sources", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    if (editorState.projections.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No aligned sources defined.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Press [a] to add an aligned source.", Style(fg = Some(Color.DarkGray)))
    } else {
      editorState.projections.zipWithIndex.foreach { case (proj, idx) =>
        if (y < area.y + height - 4) {
          val isSelected = idx == ui.selectedAlignedSourceIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
            safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }

          safeSetString(ctx.buffer, x, y, maxX, prefix, Style(fg = Some(Color.White), bg = rowBg))
          safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, proj.sourceName, Style(fg = Some(Color.Cyan), bg = rowBg, addModifier = Modifier.BOLD))
          safeSetString(ctx.buffer, (x + 2 + proj.sourceName.length + 1).toShort, y, maxX, proj.entityPath, Style(fg = Some(Color.Yellow), bg = rowBg))
          y = (y + 1).toShort

          val modeStr = s"Mode: ${proj.mode}"
          val mappingCount = if (proj.mappings.nonEmpty) s" | ${proj.mappings.size} mappings" else ""
          safeSetString(ctx.buffer, (x + 4).toShort, y, maxX, s"$modeStr$mappingCount", Style(fg = Some(Color.DarkGray), bg = rowBg))

          val rowArea = Rect(x, (y - 1).toShort, width.toShort, 2)
          ctx.onClick(rowArea) {
            ui.setSelectedAlignedSourceIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedAlignedSourceIndex - 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, editorState.projections.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedAlignedSourceIndex + 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[a] Add  [d] Delete  [Enter] Edit  [Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  private def renderAlignmentTab(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val editorState = props.editorState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Field Alignment", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    if (editorState.projections.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No aligned sources to review.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Add aligned sources first.", Style(fg = Some(Color.DarkGray)))
    } else {
      editorState.projections.zipWithIndex.foreach { case (proj, idx) =>
        if (y < area.y + height - 4) {
          val alignState = editorState.alignments.get(proj.key)
          val (statusIcon, statusColor) = alignState.map(_.validationResult).getOrElse(typr.bridge.AlignmentStatus.NotValidated) match {
            case typr.bridge.AlignmentStatus.Compatible      => ("\u2713", Color.Green)
            case typr.bridge.AlignmentStatus.Warning(_)      => ("!", Color.Yellow)
            case typr.bridge.AlignmentStatus.Incompatible(_) => ("\u2717", Color.Red)
            case typr.bridge.AlignmentStatus.NotValidated    => ("\u25CB", Color.Gray)
          }

          val isSelected = idx == ui.selectedAlignedSourceIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }

          safeSetString(ctx.buffer, x, y, maxX, prefix, Style(fg = Some(Color.White), bg = rowBg))
          safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, statusIcon, Style(fg = Some(statusColor), bg = rowBg))
          safeSetString(ctx.buffer, (x + 4).toShort, y, maxX, s"${proj.sourceName}:${proj.entityPath}", Style(fg = Some(Color.Cyan), bg = rowBg))

          val rowArea = Rect(x, y, width.toShort, 1)
          ctx.onClick(rowArea) {
            ui.setSelectedAlignedSourceIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Enter] View details  [r] Revalidate  [Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  private def renderOptionsTab(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val editorState = props.editorState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Type Options", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 3).toShort

    renderFormField(ctx, x, y, width, maxX, "Name", editorState.name, true)
    y = (y + 3).toShort

    renderFormField(ctx, x, y, width, maxX, "Description", editorState.description, false)
    y = (y + 3).toShort

    safeSetString(ctx.buffer, x, y, maxX, "Summary:", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"${editorState.fields.size} fields", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"${editorState.projections.size} aligned sources", Style(fg = Some(Color.White)))

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  private def renderFormField(ctx: RenderContext, x: Short, y: Short, width: Int, maxX: Int, label: String, value: String, isFocused: Boolean): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan))
    safeSetString(ctx.buffer, x, y, maxX, label, labelStyle)

    val inputY = (y + 1).toShort
    val inputWidth = math.min(width - 2, 40)
    val inputStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue))
      else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    val bgStr = " " * inputWidth
    safeSetString(ctx.buffer, x, inputY, maxX, bgStr, inputStyle)

    val displayText = if (value.nonEmpty) {
      val cursor = if (isFocused) "\u2588" else ""
      (value + cursor).take(inputWidth - 1)
    } else if (isFocused) {
      "\u2588"
    } else {
      "(empty)"
    }
    val textStyle =
      if (value.isEmpty && !isFocused) Style(fg = Some(Color.Gray), bg = Some(Color.DarkGray))
      else inputStyle
    safeSetString(ctx.buffer, x, inputY, maxX, displayText, textStyle)
  }

  private def renderSidebar(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val editorState = props.editorState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Info", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerArea = Rect(
      x = (area.x + 2).toShort,
      y = (area.y + 2).toShort,
      width = math.max(10, area.width - 4).toShort,
      height = math.max(5, area.height - 4).toShort
    )

    var y: Short = innerArea.y.toShort
    val x: Short = innerArea.x.toShort
    val maxX: Int = x + innerArea.width.toInt

    safeSetString(ctx.buffer, x, y, maxX, "Type Summary", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    safeSetString(ctx.buffer, x, y, maxX, s"Name: ${editorState.name}", Style(fg = Some(Color.Cyan)))
    y = (y + 2).toShort

    safeSetString(ctx.buffer, x, y, maxX, "Fields:", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort
    editorState.fields.take(5).foreach { f =>
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"${f.name}: ${f.compactType}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
    }
    if (editorState.fields.size > 5) {
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"... +${editorState.fields.size - 5} more", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }
    if (editorState.fields.isEmpty) {
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, "(none)", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Aligned Sources:", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort
    editorState.projections.take(3).foreach { p =>
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"${p.sourceName}:${p.entityPath}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
    }
    if (editorState.projections.size > 3) {
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, s"... +${editorState.projections.size - 3} more", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }
    if (editorState.projections.isEmpty) {
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, "(none)", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Navigation", Style(fg = Some(Color.Yellow)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Tab] Switch tabs", Style(fg = Some(Color.DarkGray)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Up/Down] Navigate", Style(fg = Some(Color.DarkGray)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  /** Safe string writing that truncates to prevent buffer overflows */
  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    if (availableWidth > 0 && x >= 0 && y >= 0 && y < buffer.area.height) {
      buffer.setString(x, y, text.take(availableWidth), style)
    }
  }
}
