package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.CompositeWizardState
import typr.cli.tui.CompositeWizardStep
import typr.cli.tui.FieldEditState
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.AlignedSourceEditState
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus

/** React-based CompositeWizard with mouse support */
object CompositeWizardReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      wizardState: CompositeWizardState
  )

  /** UI state managed locally with useState */
  case class UiState(
      selectedSuggestionIndex: Int,
      setSelectedSuggestionIndex: Int => Unit,
      suggestionScrollOffset: Int,
      setSuggestionScrollOffset: Int => Unit,
      selectedFieldIndex: Int,
      setSelectedFieldIndex: Int => Unit,
      selectedAlignedSourceIndex: Int,
      setSelectedAlignedSourceIndex: Int => Unit,
      selectedAlignmentSourceIndex: Int,
      setSelectedAlignmentSourceIndex: Int => Unit,
      sourcePickerIndex: Int,
      setSourcePickerIndex: Int => Unit
  )

  def component(props: Props): Component = {
    Component.named("CompositeWizardReact") { (ctx, area) =>
      val wizardState = props.wizardState

      // Initialize local UI state
      val (selectedSuggestionIndex, setSelectedSuggestionIndex) = ctx.useState(wizardState.selectedSuggestionIndex)
      val (suggestionScrollOffset, setSuggestionScrollOffset) = ctx.useState(wizardState.suggestionScrollOffset)
      val (selectedFieldIndex, setSelectedFieldIndex) = ctx.useState(wizardState.selectedFieldIndex)
      val (selectedAlignedSourceIndex, setSelectedAlignedSourceIndex) = ctx.useState(wizardState.selectedAlignedSourceIndex)
      val (selectedAlignmentSourceIndex, setSelectedAlignmentSourceIndex) = ctx.useState(wizardState.selectedAlignmentSourceIndex)
      val (sourcePickerIndex, setSourcePickerIndex) = ctx.useState(wizardState.sourcePickerIndex)

      val ui = UiState(
        selectedSuggestionIndex = selectedSuggestionIndex,
        setSelectedSuggestionIndex = setSelectedSuggestionIndex,
        suggestionScrollOffset = suggestionScrollOffset,
        setSuggestionScrollOffset = setSuggestionScrollOffset,
        selectedFieldIndex = selectedFieldIndex,
        setSelectedFieldIndex = setSelectedFieldIndex,
        selectedAlignedSourceIndex = selectedAlignedSourceIndex,
        setSelectedAlignedSourceIndex = setSelectedAlignedSourceIndex,
        selectedAlignmentSourceIndex = selectedAlignmentSourceIndex,
        setSelectedAlignmentSourceIndex = setSelectedAlignmentSourceIndex,
        sourcePickerIndex = sourcePickerIndex,
        setSourcePickerIndex = setSourcePickerIndex
      )

      val chunks = Layout(
        direction = Direction.Horizontal,
        margin = Margin(1),
        constraints = Array(Constraint.Percentage(60), Constraint.Percentage(40))
      ).split(area)

      renderMainPanel(ctx, chunks(0), props, ui)
      renderSidebar(ctx, chunks(1), props)
    }
  }

  private def renderMainPanel(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Create Domain Type", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
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

    wizardState.step match {
      case CompositeWizardStep.SelectSuggestion => renderSelectSuggestion(ctx, innerArea, props, ui)
      case CompositeWizardStep.EnterName        => renderEnterName(ctx, innerArea, props)
      case CompositeWizardStep.DefineFields     => renderDefineFields(ctx, innerArea, props, ui)
      case CompositeWizardStep.AddProjections   => renderAddProjections(ctx, innerArea, props, ui)
      case CompositeWizardStep.AlignFields      => renderAlignFields(ctx, innerArea, props, ui)
      case CompositeWizardStep.Review           => renderReview(ctx, innerArea, props)
    }
  }

  private def renderSelectSuggestion(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Select from Database Tables", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Choose a table to create a domain type,", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "or press Tab to start from scratch.", Style(fg = Some(Color.White)))
    y = (y + 2).toShort

    val suggestions = wizardState.suggestions
    if (suggestions.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No suggestions available.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Press Tab to create from scratch.", Style(fg = Some(Color.DarkGray)))
    } else {
      val rowHeight = 2
      val visibleRows = math.max(1, (height - (y - area.y).toInt - 4) / rowHeight)
      val startIdx = ui.suggestionScrollOffset
      val endIdx = math.min(suggestions.size, startIdx + visibleRows)
      val visibleSuggestions = suggestions.slice(startIdx, endIdx)

      visibleSuggestions.zipWithIndex.foreach { case (suggestion, relIdx) =>
        if (y < area.y + height - 3) {
          val idx = startIdx + relIdx
          val isSelected = idx == ui.selectedSuggestionIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.White), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "
          val sourceLabel = if (suggestion.sourceCount > 1) s"[${suggestion.sourceCount} sources]" else "[1 source]"

          // Clear row background if selected
          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }

          safeSetString(ctx.buffer, x, y, maxX, s"$prefix${suggestion.name} $sourceLabel", style)
          y = (y + 1).toShort

          if (y < area.y + height - 2) {
            val detailStyle = Style(fg = Some(Color.Gray), bg = rowBg)
            safeSetString(ctx.buffer, x, y, maxX, s"    ${suggestion.fields.size} fields", detailStyle)
          }

          val rowArea = Rect(x, (y - 1).toShort, width.toShort, 2)
          ctx.onClick(rowArea) {
            ui.setSelectedSuggestionIndex(idx)
          }
          y = (y + 1).toShort
        }
      }

      ctx.onScrollUp(area) {
        val newIdx = math.max(0, ui.selectedSuggestionIndex - 1)
        val newOffset = if (newIdx < ui.suggestionScrollOffset) newIdx else ui.suggestionScrollOffset
        ui.setSelectedSuggestionIndex(newIdx)
        ui.setSuggestionScrollOffset(newOffset)
      }

      ctx.onScrollDown(area) {
        val maxIdx = math.max(0, suggestions.size - 1)
        val newIdx = math.min(maxIdx, ui.selectedSuggestionIndex + 1)
        val newOffset = if (newIdx >= ui.suggestionScrollOffset + visibleRows) newIdx - visibleRows + 1 else ui.suggestionScrollOffset
        ui.setSelectedSuggestionIndex(newIdx)
        ui.setSuggestionScrollOffset(newOffset)
      }
    }

    val hintY = math.min((area.y + height - 1).toInt, (y + 2).toInt).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Up/Down] Navigate  [Enter] Select  [Tab] Skip", Style(fg = Some(Color.DarkGray)))
  }

  private def renderEnterName(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Step 1: Enter Type Name", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Enter a name for your domain type.", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "This will be the canonical type.", Style(fg = Some(Color.White)))
    y = (y + 3).toShort

    // Name input field - large and clickable
    safeSetString(ctx.buffer, x, y, maxX, "Name", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort

    val inputWidth = math.min(width - 2, 40)
    val inputStyle = Style(fg = Some(Color.White), bg = Some(Color.Blue))
    safeSetString(ctx.buffer, x, y, maxX, " " * inputWidth, inputStyle)
    safeSetString(ctx.buffer, x, y, maxX, s"${wizardState.name}\u2588", inputStyle)
    y = (y + 2).toShort

    safeSetString(ctx.buffer, x, y, maxX, "Examples: Customer, Order, ProductInfo", Style(fg = Some(Color.DarkGray)))

    wizardState.error.foreach { err =>
      y = (y + 2).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"Error: $err", Style(fg = Some(Color.Red)))
    }

    val hintY = (area.y + area.height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Tab] Next  [Shift+Tab] Previous  [Esc] Cancel", Style(fg = Some(Color.DarkGray)))
  }

  private def renderDefineFields(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Step 2: Define Fields", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Define the canonical fields for this type.", Style(fg = Some(Color.White)))
    y = (y + 2).toShort

    if (wizardState.fields.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No fields defined yet.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    } else {
      wizardState.fields.zipWithIndex.foreach { case (field, idx) =>
        if (y < area.y + height - 5) {
          val isSelected = idx == ui.selectedFieldIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.White), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }
          safeSetString(ctx.buffer, x, y, maxX, s"$prefix${field.name}: ${field.compactType}", style)

          val rowArea = Rect(x, y, width.toShort, 1)
          ctx.onClick(rowArea) {
            ui.setSelectedFieldIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    if (wizardState.addingField) {
      val currentField = wizardState.fields.lastOption.getOrElse(FieldEditState.empty)
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Adding field:", Style(fg = Some(Color.Green)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"  Name: ${currentField.name}\u2588", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"  Type: ${currentField.compactType} (: to cycle)", Style(fg = Some(Color.DarkGray)))
    }

    wizardState.error.foreach { err =>
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"Error: $err", Style(fg = Some(Color.Red)))
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedFieldIndex - 1)
      ui.setSelectedFieldIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, wizardState.fields.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedFieldIndex + 1)
      ui.setSelectedFieldIndex(newIdx)
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[a] Add  [d] Delete  [Tab] Next step", Style(fg = Some(Color.DarkGray)))
  }

  private def renderAddProjections(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Step 3: Add Aligned Sources", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Map this domain type to entities in your data sources.", Style(fg = Some(Color.White)))
    y = (y + 2).toShort

    if (wizardState.alignedSources.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No aligned sources defined yet.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "(You can add them later)", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    } else {
      wizardState.alignedSources.zipWithIndex.foreach { case (proj, idx) =>
        if (y < area.y + height - 5) {
          val isSelected = idx == ui.selectedAlignedSourceIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.Cyan), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }
          safeSetString(ctx.buffer, x, y, maxX, s"$prefix${proj.sourceName}:${proj.entityPath}", style)

          val rowArea = Rect(x, y, width.toShort, 1)
          ctx.onClick(rowArea) {
            ui.setSelectedAlignedSourceIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    if (wizardState.sourcePickerOpen) {
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Select source:", Style(fg = Some(Color.Green)))
      y = (y + 1).toShort
      val sources = getAvailableSources(props.globalState)
      sources.zipWithIndex.foreach { case (source, idx) =>
        if (y < area.y + height - 3) {
          val isSelected = idx == ui.sourcePickerIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.Cyan), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }
          safeSetString(ctx.buffer, x, y, maxX, s"$prefix${source.value}", style)

          val rowArea = Rect(x, y, width.toShort, 1)
          ctx.onClick(rowArea) {
            ui.setSourcePickerIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    } else if (wizardState.addingAlignedSource) {
      val currentProj = wizardState.alignedSources.lastOption.getOrElse(AlignedSourceEditState.empty(""))
      val modeStr = currentProj.mode match {
        case typr.bridge.CompatibilityMode.Superset => "superset"
        case typr.bridge.CompatibilityMode.Exact    => "exact"
        case typr.bridge.CompatibilityMode.Subset   => "subset"
      }
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"Adding for ${currentProj.sourceName}:", Style(fg = Some(Color.Green)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"  Entity: ${currentProj.entityPath}\u2588", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, "Mode:   ", Style(fg = Some(Color.Cyan)))
      safeSetString(ctx.buffer, (x + 10).toShort, y, maxX, modeStr, Style(fg = Some(Color.Yellow)))
      safeSetString(ctx.buffer, (x + 10 + modeStr.length + 1).toShort, y, maxX, "(Tab to cycle)", Style(fg = Some(Color.DarkGray)))
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedAlignedSourceIndex - 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, wizardState.alignedSources.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedAlignedSourceIndex + 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }

    wizardState.error.foreach { err =>
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"Error: $err", Style(fg = Some(Color.Red)))
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[a] Add  [d] Delete  [Tab] Next step", Style(fg = Some(Color.DarkGray)))
  }

  private def renderAlignFields(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Step 4: Review Alignment", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Verify field alignment for each source.", Style(fg = Some(Color.White)))
    y = (y + 2).toShort

    if (wizardState.alignedSources.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "No aligned sources to align.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, "Press Tab to continue.", Style(fg = Some(Color.DarkGray)))
    } else {
      wizardState.alignedSources.zipWithIndex.foreach { case (proj, idx) =>
        if (y < area.y + height - 5) {
          val isSelected = idx == ui.selectedAlignmentSourceIndex
          val rowBg = if (isSelected) Some(Color.Blue) else None
          val style = Style(fg = Some(Color.White), bg = rowBg)
          val prefix = if (isSelected) "> " else "  "

          val alignmentState = wizardState.alignments.getOrElse(proj.key, typr.cli.tui.FieldAlignmentState.initial(proj.key))
          val (statusIcon, statusColor) = alignmentState.validationResult match {
            case typr.bridge.AlignmentStatus.Compatible      => ("\u2713", Color.Green)
            case typr.bridge.AlignmentStatus.Warning(_)      => ("!", Color.Yellow)
            case typr.bridge.AlignmentStatus.Incompatible(_) => ("\u2717", Color.Red)
            case typr.bridge.AlignmentStatus.NotValidated    => ("\u25CB", Color.Gray)
          }

          if (isSelected) {
            safeSetString(ctx.buffer, x, y, maxX, " " * width, Style(bg = Some(Color.Blue)))
          }
          safeSetString(ctx.buffer, x, y, maxX, prefix, style)
          safeSetString(ctx.buffer, (x + 2).toShort, y, maxX, statusIcon, Style(fg = Some(statusColor), bg = rowBg))
          safeSetString(ctx.buffer, (x + 4).toShort, y, maxX, s"${proj.sourceName}:${proj.entityPath}", style)

          val rowArea = Rect(x, y, width.toShort, 1)
          ctx.onClick(rowArea) {
            ui.setSelectedAlignmentSourceIndex(idx)
          }
          y = (y + 1).toShort
        }
      }
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedAlignmentSourceIndex - 1)
      ui.setSelectedAlignmentSourceIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, wizardState.alignedSources.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedAlignmentSourceIndex + 1)
      ui.setSelectedAlignmentSourceIndex(newIdx)
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Up/Down] Select  [Tab] Next step", Style(fg = Some(Color.DarkGray)))
  }

  private def renderReview(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val wizardState = props.wizardState
    var y: Short = area.y.toShort
    val x: Short = area.x.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    safeSetString(ctx.buffer, x, y, maxX, "Step 5: Review & Save", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    val ready = wizardState.name.nonEmpty && wizardState.fields.nonEmpty
    if (ready) {
      safeSetString(ctx.buffer, x, y, maxX, "Ready to create domain type!", Style(fg = Some(Color.Green)))
    } else {
      safeSetString(ctx.buffer, x, y, maxX, "Missing required information", Style(fg = Some(Color.Red)))
    }
    y = (y + 2).toShort

    safeSetString(ctx.buffer, x, y, maxX, s"Name: ${wizardState.name}", Style(fg = Some(Color.Cyan)))
    y = (y + 2).toShort

    safeSetString(ctx.buffer, x, y, maxX, "Fields:", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort
    wizardState.fields.take(5).foreach { f =>
      safeSetString(ctx.buffer, x, y, maxX, s"  ${f.name}: ${f.compactType}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
    }
    if (wizardState.fields.size > 5) {
      safeSetString(ctx.buffer, x, y, maxX, s"  ... +${wizardState.fields.size - 5} more", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Aligned Sources:", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort
    if (wizardState.alignedSources.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "  (none)", Style(fg = Some(Color.DarkGray)))
    } else {
      wizardState.alignedSources.take(3).foreach { p =>
        safeSetString(ctx.buffer, x, y, maxX, s"  ${p.sourceName}:${p.entityPath}", Style(fg = Some(Color.White)))
        y = (y + 1).toShort
      }
    }

    wizardState.error.foreach { err =>
      y = (y + 2).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"Error: $err", Style(fg = Some(Color.Red)))
    }

    val hintY = (area.y + height - 1).toShort
    safeSetString(ctx.buffer, x, hintY, maxX, "[Enter] Create  [Esc] Cancel", Style(fg = Some(Color.DarkGray)))
  }

  private def renderSidebar(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val wizardState = props.wizardState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Steps", Style(fg = Some(Color.Cyan))))),
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

    safeSetString(ctx.buffer, x, y, maxX, "Progress", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    val steps = List(
      ("Select", CompositeWizardStep.SelectSuggestion),
      ("1. Name", CompositeWizardStep.EnterName),
      ("2. Fields", CompositeWizardStep.DefineFields),
      ("3. Sources", CompositeWizardStep.AddProjections),
      ("4. Align", CompositeWizardStep.AlignFields),
      ("5. Review", CompositeWizardStep.Review)
    )

    val currentStepIdx = steps.indexWhere(_._2 == wizardState.step)

    steps.zipWithIndex.foreach { case ((label, step), idx) =>
      val isCurrent = step == wizardState.step
      val isCompleted = idx < currentStepIdx
      val (icon, color) =
        if (isCurrent) ("\u25C9", Color.Cyan)
        else if (isCompleted) ("\u2713", Color.Green)
        else ("\u25CB", Color.DarkGray)
      safeSetString(ctx.buffer, x, y, maxX, s"$icon $label", Style(fg = Some(color)))
      y = (y + 1).toShort
    }

    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "Navigation", Style(fg = Some(Color.Yellow)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Tab] Next step", Style(fg = Some(Color.DarkGray)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Shift+Tab] Previous", Style(fg = Some(Color.DarkGray)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, x, y, maxX, "[Esc] Cancel", Style(fg = Some(Color.DarkGray)))
  }

  private def getAvailableSources(globalState: GlobalState): List[SourceName] = {
    globalState.sourceStatuses
      .collect { case (name, _: SourceStatus.Ready) => name }
      .toList
      .sortBy(_.value)
  }

  /** Safe string writing that truncates to prevent buffer overflows */
  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    if (availableWidth > 0 && x >= 0 && y >= 0 && y < buffer.area.height) {
      val truncated = text.take(availableWidth)
      buffer.setString(x, y, truncated, style)
    }
  }
}
