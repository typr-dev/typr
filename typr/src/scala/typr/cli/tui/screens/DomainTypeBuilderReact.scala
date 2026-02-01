package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.BuilderSection
import typr.cli.tui.DomainTypeBuilderState
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.MappingStatus
import typr.cli.tui.PrimaryFieldState
import typr.cli.tui.AlignedSourceBuilderState
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.BuilderStep
import typr.Nullability

/** React-based single-screen Domain Type Builder.
  *
  * UI state (selection, scroll, modals) is managed locally via useState. Data state (name, fields, sources) is passed in via props and updated via callbacks.
  */
object DomainTypeBuilderReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      builderState: DomainTypeBuilderState
  )

  /** Local UI state managed via useState - not persisted globally */
  case class UiState(
      selectedSuggestionIndex: Int,
      setSelectedSuggestionIndex: Int => Unit,
      suggestionScrollOffset: Int,
      setSuggestionScrollOffset: Int => Unit,
      focusedSection: BuilderSection,
      setFocusedSection: BuilderSection => Unit,
      selectedFieldIndex: Int,
      setSelectedFieldIndex: Int => Unit,
      selectedAlignedSourceIndex: Int,
      setSelectedAlignedSourceIndex: Int => Unit,
      addingAlignedSource: Boolean,
      setAddingAlignedSource: Boolean => Unit,
      sourcePickerOpen: Boolean,
      setSourcePickerOpen: Boolean => Unit,
      sourcePickerIndex: Int,
      setSourcePickerIndex: Int => Unit,
      entityPickerOpen: Boolean,
      setEntityPickerOpen: Boolean => Unit,
      entityPickerIndex: Int,
      setEntityPickerIndex: Int => Unit,
      primaryEntityPickerOpen: Boolean,
      setPrimaryEntityPickerOpen: Boolean => Unit,
      primaryEntityPickerIndex: Int,
      setPrimaryEntityPickerIndex: Int => Unit
  )

  def component(props: Props): Component = {
    Component.named("DomainTypeBuilder") { (ctx, area) =>
      // Initialize all UI state via useState
      val (selectedSuggestionIndex, setSelectedSuggestionIndex) = ctx.useState(0)
      val (suggestionScrollOffset, setSuggestionScrollOffset) = ctx.useState(0)
      val (focusedSection, setFocusedSection) = ctx.useState[BuilderSection](BuilderSection.Name)
      val (selectedFieldIndex, setSelectedFieldIndex) = ctx.useState(0)
      val (selectedAlignedSourceIndex, setSelectedAlignedSourceIndex) = ctx.useState(0)
      val (addingAlignedSource, setAddingAlignedSource) = ctx.useState(false)
      val (sourcePickerOpen, setSourcePickerOpen) = ctx.useState(false)
      val (sourcePickerIndex, setSourcePickerIndex) = ctx.useState(0)
      val (entityPickerOpen, setEntityPickerOpen) = ctx.useState(false)
      val (entityPickerIndex, setEntityPickerIndex) = ctx.useState(0)
      val (primaryEntityPickerOpen, setPrimaryEntityPickerOpen) = ctx.useState(false)
      val (primaryEntityPickerIndex, setPrimaryEntityPickerIndex) = ctx.useState(0)

      val ui = UiState(
        selectedSuggestionIndex = selectedSuggestionIndex,
        setSelectedSuggestionIndex = setSelectedSuggestionIndex,
        suggestionScrollOffset = suggestionScrollOffset,
        setSuggestionScrollOffset = setSuggestionScrollOffset,
        focusedSection = focusedSection,
        setFocusedSection = setFocusedSection,
        selectedFieldIndex = selectedFieldIndex,
        setSelectedFieldIndex = setSelectedFieldIndex,
        selectedAlignedSourceIndex = selectedAlignedSourceIndex,
        setSelectedAlignedSourceIndex = setSelectedAlignedSourceIndex,
        addingAlignedSource = addingAlignedSource,
        setAddingAlignedSource = setAddingAlignedSource,
        sourcePickerOpen = sourcePickerOpen,
        setSourcePickerOpen = setSourcePickerOpen,
        sourcePickerIndex = sourcePickerIndex,
        setSourcePickerIndex = setSourcePickerIndex,
        entityPickerOpen = entityPickerOpen,
        setEntityPickerOpen = setEntityPickerOpen,
        entityPickerIndex = entityPickerIndex,
        setEntityPickerIndex = setEntityPickerIndex,
        primaryEntityPickerOpen = primaryEntityPickerOpen,
        setPrimaryEntityPickerOpen = setPrimaryEntityPickerOpen,
        primaryEntityPickerIndex = primaryEntityPickerIndex,
        setPrimaryEntityPickerIndex = setPrimaryEntityPickerIndex
      )

      props.builderState.step match {
        case BuilderStep.SelectSuggestion =>
          renderSuggestionScreen(ctx, area, props, ui)
        case BuilderStep.Builder =>
          renderBuilderScreen(ctx, area, props, ui)
      }
    }
  }

  private def renderSuggestionScreen(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val vertChunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(10))
    ).split(area)

    BackButton("Create Domain Type")(props.callbacks.goBack()).render(ctx, vertChunks(0))

    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(60), Constraint.Percentage(40))
    ).split(vertChunks(1))

    renderSuggestionList(ctx, chunks(0), props, ui)
    renderSuggestionSidebar(ctx, chunks(1), props, ui)
  }

  private def renderSuggestionList(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Create Domain Type", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    var innerY = (y + 2).toShort
    val innerMaxX = maxX - 2
    val innerWidth = width - 4
    val innerHeight = height - 4

    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Select from Database Tables", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    innerY = (innerY + 1).toShort
    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Choose a table, or press Tab to start from scratch.", Style(fg = Some(Color.Gray)))
    innerY = (innerY + 2).toShort

    val suggestions = builderState.suggestions
    if (suggestions.isEmpty) {
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "No suggestions available.", Style(fg = Some(Color.DarkGray)))
      innerY = (innerY + 1).toShort
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Press Tab to create from scratch.", Style(fg = Some(Color.DarkGray)))
    } else {
      val visibleCards = math.max(1, (innerHeight - 4) / (CardHeight + 1))
      val startIdx = ui.suggestionScrollOffset
      val endIdx = math.min(suggestions.size, startIdx + visibleCards)
      val visibleSuggestions = suggestions.slice(startIdx, endIdx)
      val hasScrollbar = suggestions.size > visibleCards
      val cardWidth = if (hasScrollbar) innerWidth - 2 else innerWidth

      visibleSuggestions.zipWithIndex.foreach { case (suggestion, relIdx) =>
        val idx = startIdx + relIdx
        if (innerY + CardHeight <= y + height - 2) {
          val sourceLabel = if (suggestion.sourceCount > 1) s"${suggestion.sourceCount} sources" else "1 source"
          val subtitle = s"${suggestion.fields.size} fields \u2022 $sourceLabel \u2022 ${suggestion.reason}"

          Card(
            icon = Some("\u25c8"),
            title = suggestion.name,
            subtitle = subtitle,
            iconColor = Some(Color.Magenta),
            maxWidth = math.min(cardWidth, 55),
            hoverPosition = props.globalState.hoverPosition
          )(selectSuggestion(props, idx)).render(ctx, Rect(innerX, innerY, cardWidth.toShort, CardHeight.toShort))

          innerY = (innerY + CardHeight + 1).toShort
        }
      }

      // Show scrollbar if needed
      if (hasScrollbar) {
        val scrollbarX = (x + width - 3).toShort
        val scrollbarY = (y + 4).toShort
        val scrollbarHeight = height - 7
        ScrollbarWithArrows(
          totalItems = suggestions.size,
          visibleItems = visibleCards,
          scrollOffset = startIdx,
          trackColor = Color.DarkGray,
          thumbColor = Color.Gray,
          arrowColor = Color.Cyan,
          onScrollUp = Some(() => {
            val newOffset = math.max(0, ui.suggestionScrollOffset - 1)
            ui.setSuggestionScrollOffset(newOffset)
          }),
          onScrollDown = Some(() => {
            val maxOffset = math.max(0, suggestions.size - visibleCards)
            val newOffset = math.min(maxOffset, ui.suggestionScrollOffset + 1)
            ui.setSuggestionScrollOffset(newOffset)
          })
        ).render(ctx, Rect(scrollbarX, scrollbarY, 1, scrollbarHeight.toShort))
      }

      ctx.onScrollUp(area) {
        val newIdx = math.max(0, ui.selectedSuggestionIndex - 1)
        ui.setSelectedSuggestionIndex(newIdx)
        if (newIdx < ui.suggestionScrollOffset) {
          ui.setSuggestionScrollOffset(newIdx)
        }
      }

      ctx.onScrollDown(area) {
        val maxIdx = math.max(0, suggestions.size - 1)
        val newIdx = math.min(maxIdx, ui.selectedSuggestionIndex + 1)
        ui.setSelectedSuggestionIndex(newIdx)
        if (newIdx >= ui.suggestionScrollOffset + visibleCards) {
          ui.setSuggestionScrollOffset(newIdx - visibleCards + 1)
        }
      }
    }

    val hintY = (y + height - 2).toShort
    safeSetString(ctx.buffer, innerX, hintY, innerMaxX, "[Up/Down] Navigate  [Enter] Select  [Tab] Skip  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def selectSuggestion(props: Props, idx: Int): Unit = {
    Interactive.scheduleStateUpdate { state =>
      state.currentScreen match {
        case builder: AppScreen.DomainTypeBuilder =>
          val suggestion = builder.state.suggestions.lift(idx)
          suggestion match {
            case Some(s) =>
              val newState = DomainTypeBuilderState.fromSuggestion(s)
              state.copy(currentScreen = builder.copy(state = newState))
            case None => state
          }
        case _ => state
      }
    }
  }

  private def renderSuggestionSidebar(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Preview", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    var innerY = (y + 2).toShort
    val innerMaxX = maxX - 2

    builderState.suggestions.lift(ui.selectedSuggestionIndex) match {
      case Some(suggestion) =>
        safeSetString(ctx.buffer, innerX, innerY, innerMaxX, suggestion.name, Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
        innerY = (innerY + 2).toShort

        safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Sources:", Style(fg = Some(Color.Cyan)))
        innerY = (innerY + 1).toShort
        suggestion.projections.take(3).foreach { proj =>
          safeSetString(ctx.buffer, innerX, innerY, innerMaxX, s"  ${proj.sourceName}:${proj.entityPath}", Style(fg = Some(Color.White)))
          innerY = (innerY + 1).toShort
        }
        if (suggestion.projections.size > 3) {
          safeSetString(ctx.buffer, innerX, innerY, innerMaxX, s"  ... +${suggestion.projections.size - 3} more", Style(fg = Some(Color.DarkGray)))
          innerY = (innerY + 1).toShort
        }

        innerY = (innerY + 1).toShort
        safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Fields:", Style(fg = Some(Color.Cyan)))
        innerY = (innerY + 1).toShort
        suggestion.fields.take(6).foreach { field =>
          val nullStr = if (field.nullable) "?" else ""
          val pkStr = if (field.isPrimaryKey) " PK" else ""
          safeSetString(ctx.buffer, innerX, innerY, innerMaxX, s"  ${field.name}: ${field.typeName}$nullStr$pkStr", Style(fg = Some(Color.White)))
          innerY = (innerY + 1).toShort
        }
        if (suggestion.fields.size > 6) {
          safeSetString(ctx.buffer, innerX, innerY, innerMaxX, s"  ... +${suggestion.fields.size - 6} more", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "No suggestion selected", Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderBuilderScreen(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Length(3),
        Constraint.Length(5),
        Constraint.Min(10),
        Constraint.Length(3)
      )
    ).split(area)

    BackButton("Domain Type Builder")(props.callbacks.goBack()).render(ctx, chunks(0))
    renderHeader(ctx, chunks(1), props, ui)
    renderPrimarySourceSection(ctx, chunks(2), props, ui)
    renderMainContent(ctx, chunks(3), props, ui)
    renderFooter(ctx, chunks(4), props, ui)
  }

  private def renderHeader(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Domain Type Builder", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    val innerY = (y + 1).toShort
    val innerMaxX = maxX - 2

    val isFocused = ui.focusedSection == BuilderSection.Name
    val nameStyle = if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.White))

    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Name: ", Style(fg = Some(Color.Cyan)))
    val inputWidth = math.min(30, innerMaxX - innerX - 7)
    val nameDisplay = if (isFocused) s"${builderState.name}\u2588" else builderState.name.take(inputWidth)
    val paddedName = nameDisplay.padTo(inputWidth, ' ')
    safeSetString(ctx.buffer, (innerX + 6).toShort, innerY, innerMaxX, paddedName, nameStyle)

    val nameArea = Rect((innerX + 6).toShort, innerY, inputWidth.toShort, 1)
    ctx.onClick(nameArea) {
      ui.setFocusedSection(BuilderSection.Name)
    }
  }

  private def renderPrimarySourceSection(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Primary Source", Style(fg = Some(Color.Yellow))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    var innerY = (y + 1).toShort
    val innerMaxX = maxX - 2

    val isFocused = ui.focusedSection == BuilderSection.PrimarySource

    val sourceLabel = builderState.primarySourceName.getOrElse("[Select Source]")
    val sourceStyle = if (isFocused && !ui.primaryEntityPickerOpen) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.Cyan))
    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Source: ", Style(fg = Some(Color.Gray)))
    safeSetString(ctx.buffer, (innerX + 8).toShort, innerY, innerMaxX, s"[$sourceLabel]", sourceStyle)

    val sourceArea = Rect((innerX + 8).toShort, innerY, (sourceLabel.length + 2).toShort, 1)
    ctx.onClick(sourceArea) {
      ui.setFocusedSection(BuilderSection.PrimarySource)
      ui.setSourcePickerOpen(true)
    }

    innerY = (innerY + 1).toShort
    val entityLabel = builderState.primaryEntityPath.getOrElse("[Select Entity]")
    val entityStyle = if (isFocused && ui.primaryEntityPickerOpen) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.Cyan))
    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Entity: ", Style(fg = Some(Color.Gray)))
    safeSetString(ctx.buffer, (innerX + 8).toShort, innerY, innerMaxX, s"[$entityLabel]", entityStyle)

    val entityArea = Rect((innerX + 8).toShort, innerY, (entityLabel.length + 2).toShort, 1)
    ctx.onClick(entityArea) {
      if (builderState.primarySourceName.isDefined) {
        ui.setFocusedSection(BuilderSection.PrimarySource)
        ui.setPrimaryEntityPickerOpen(true)
      }
    }

    if (ui.sourcePickerOpen) {
      renderSourcePicker(ctx, Rect(innerX, (innerY + 1).toShort, (width - 4).toShort, 5), props, ui)
    } else if (ui.primaryEntityPickerOpen) {
      renderEntityPicker(ctx, Rect(innerX, (innerY + 1).toShort, (width - 4).toShort, 5), props, ui, isPrimary = true)
    }
  }

  private def renderMainContent(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(45), Constraint.Percentage(55))
    ).split(area)

    renderPrimaryFieldsCard(ctx, chunks(0), props, ui)
    renderAlignedSourcesCard(ctx, chunks(1), props, ui)
  }

  private def renderPrimaryFieldsCard(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    val isFocused = ui.focusedSection == BuilderSection.PrimaryFields
    val borderColor = if (isFocused) Color.Cyan else Color.DarkGray

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Primary Fields", Style(fg = Some(Color.Yellow))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(borderColor))
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    var innerY = (y + 1).toShort
    val innerMaxX = maxX - 2
    val maxVisibleFields = height - 3

    if (builderState.primaryFields.isEmpty) {
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Select a primary source", Style(fg = Some(Color.DarkGray)))
      innerY = (innerY + 1).toShort
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "to load fields", Style(fg = Some(Color.DarkGray)))
    } else {
      val scrollOffset = math.max(0, ui.selectedFieldIndex - maxVisibleFields + 2)
      val visibleFields = builderState.primaryFields.slice(scrollOffset, scrollOffset + maxVisibleFields)

      visibleFields.zipWithIndex.foreach { case (field, relIdx) =>
        if (innerY < y + height - 1) {
          val idx = scrollOffset + relIdx
          val isSelected = isFocused && idx == ui.selectedFieldIndex
          val rowBg = if (isSelected) Some(Color.Cyan) else None
          val rowFg = if (isSelected) Some(Color.Black) else Some(Color.White)
          val rowStyle = Style(fg = rowFg, bg = rowBg)

          val checkbox = if (field.included) "[x]" else "[ ]"
          val checkboxColor = if (isSelected) Color.Black else if (field.included) Color.Green else Color.Gray
          val checkboxStyle = Style(fg = Some(checkboxColor), bg = rowBg)

          val rowText = s"$checkbox ${field.name}".padTo(width - 2, ' ')
          safeSetString(ctx.buffer, innerX, innerY, innerMaxX, rowText, rowStyle)
          safeSetString(ctx.buffer, innerX, innerY, (innerX + 3).toInt, checkbox, checkboxStyle)

          val typeStr = s": ${field.canonicalType}${if (field.nullable) "?" else ""}"
          val typeColor = if (isSelected) Color.DarkGray else Color.DarkGray
          val typeX = math.min(innerX + 4 + field.name.length, innerMaxX - typeStr.length - 2).toShort
          safeSetString(ctx.buffer, typeX, innerY, innerMaxX, typeStr, Style(fg = Some(typeColor), bg = rowBg))

          val rowArea = Rect(innerX, innerY, (innerMaxX - innerX).toShort, 1)
          ctx.onClick(rowArea) {
            ui.setFocusedSection(BuilderSection.PrimaryFields)
            ui.setSelectedFieldIndex(idx)
            // Toggle field inclusion - this updates data so needs scheduleStateUpdate
            Interactive.scheduleStateUpdate { state =>
              state.currentScreen match {
                case builder: AppScreen.DomainTypeBuilder =>
                  val newFields = builder.state.primaryFields.updated(idx, field.copy(included = !field.included))
                  state.copy(currentScreen = builder.copy(state = builder.state.copy(primaryFields = newFields)))
                case _ => state
              }
            }
          }

          innerY = (innerY + 1).toShort
        }
      }

      ctx.onScrollUp(area) {
        ui.setFocusedSection(BuilderSection.PrimaryFields)
        val newIdx = math.max(0, ui.selectedFieldIndex - 1)
        ui.setSelectedFieldIndex(newIdx)
      }

      ctx.onScrollDown(area) {
        ui.setFocusedSection(BuilderSection.PrimaryFields)
        val maxIdx = math.max(0, builderState.primaryFields.size - 1)
        val newIdx = math.min(maxIdx, ui.selectedFieldIndex + 1)
        ui.setSelectedFieldIndex(newIdx)
      }
    }
  }

  private def renderAlignedSourcesCard(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val height: Int = area.height.toInt
    val maxX: Int = x + width

    val isFocused = ui.focusedSection == BuilderSection.AlignedSources
    val borderColor = if (isFocused) Color.Cyan else Color.DarkGray

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Aligned Sources", Style(fg = Some(Color.Yellow))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(borderColor))
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (x + 2).toShort
    var innerY = (y + 1).toShort
    val innerMaxX = maxX - 2

    val addButtonStyle = Style(fg = Some(Color.Green), addModifier = Modifier.BOLD)
    safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "[+ Add Aligned Source]", addButtonStyle)
    val addArea = Rect(innerX, innerY, 23, 1)
    ctx.onClick(addArea) {
      ui.setFocusedSection(BuilderSection.AlignedSources)
      ui.setAddingAlignedSource(true)
      ui.setSourcePickerOpen(true)
    }
    innerY = (innerY + 2).toShort

    if (ui.addingAlignedSource && ui.sourcePickerOpen) {
      renderSourcePicker(ctx, Rect(innerX, innerY, (width - 4).toShort, 5), props, ui)
    } else if (ui.addingAlignedSource && ui.entityPickerOpen) {
      renderEntityPicker(ctx, Rect(innerX, innerY, (width - 4).toShort, 5), props, ui, isPrimary = false)
    } else if (builderState.alignedSources.isEmpty) {
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "No aligned sources yet.", Style(fg = Some(Color.DarkGray)))
      innerY = (innerY + 1).toShort
      safeSetString(ctx.buffer, innerX, innerY, innerMaxX, "Click above to add one.", Style(fg = Some(Color.DarkGray)))
    } else {
      builderState.alignedSources.zipWithIndex.foreach { case (alignedSource, idx) =>
        if (innerY < y + height - 4) {
          renderAlignedSourceCard(ctx, Rect(innerX, innerY, (innerMaxX - innerX).toShort, 4), props, ui, alignedSource, idx)
          innerY = (innerY + 5).toShort
        }
      }
    }

    ctx.onScrollUp(area) {
      ui.setFocusedSection(BuilderSection.AlignedSources)
      val newIdx = math.max(0, ui.selectedAlignedSourceIndex - 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      ui.setFocusedSection(BuilderSection.AlignedSources)
      val maxIdx = math.max(0, builderState.alignedSources.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedAlignedSourceIndex + 1)
      ui.setSelectedAlignedSourceIndex(newIdx)
    }
  }

  private def renderAlignedSourceCard(
      ctx: RenderContext,
      area: Rect,
      props: Props,
      ui: UiState,
      alignedSource: AlignedSourceBuilderState,
      idx: Int
  ): Unit = {
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    val isSelected = ui.focusedSection == BuilderSection.AlignedSources && idx == ui.selectedAlignedSourceIndex
    val borderColor = if (isSelected) Color.Magenta else Color.DarkGray

    val cardWidth = math.min(width, 50)
    val topBorder = "\u256d" + "\u2500" * (cardWidth - 2) + "\u256e"
    val bottomBorder = "\u2570" + "\u2500" * (cardWidth - 2) + "\u256f"
    val emptyLine = "\u2502" + " " * (cardWidth - 2) + "\u2502"

    val borderStyle = Style(fg = Some(borderColor))
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 3).toShort, maxX, bottomBorder, borderStyle)

    // Draw X button for removal at top-right corner (click handler registered after card)
    val xButtonX = (x + cardWidth - 4).toShort
    val xButtonY = y
    val xButtonStyle = Style(fg = Some(Color.Red), addModifier = Modifier.BOLD)
    safeSetString(ctx.buffer, xButtonX, xButtonY, maxX, "[X]", xButtonStyle)

    val modeLabel = alignedSource.mode match {
      case typr.bridge.CompatibilityMode.Superset => "superset"
      case typr.bridge.CompatibilityMode.Exact    => "exact"
      case typr.bridge.CompatibilityMode.Subset   => "subset"
    }

    val titleStyle = Style(fg = Some(Color.Magenta), addModifier = Modifier.BOLD)
    val modeStyle = Style(fg = Some(Color.Yellow))
    val sourceInfo = s"${alignedSource.sourceName}:${alignedSource.entityPath}"
    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, sourceInfo.take(cardWidth - 16), titleStyle)
    safeSetString(ctx.buffer, (x + cardWidth - modeLabel.length - 3).toShort, (y + 1).toShort, maxX, s"[$modeLabel]", modeStyle)

    val mappingCount = alignedSource.fieldMappings.size
    val matchedCount = alignedSource.fieldMappings.count(_.status == MappingStatus.Matched)
    val statusStr = if (mappingCount > 0) s"$matchedCount/$mappingCount fields aligned" else "Not validated"
    val statusStyle = Style(fg = Some(Color.Gray))
    safeSetString(ctx.buffer, (x + 2).toShort, (y + 2).toShort, maxX, statusStr, statusStyle)

    val cardArea = Rect(x, y, cardWidth.toShort, 4)
    ctx.onClick(cardArea) {
      ui.setFocusedSection(BuilderSection.AlignedSources)
      ui.setSelectedAlignedSourceIndex(idx)
      // Toggle expanded state - this is data so needs scheduleStateUpdate
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case builder: AppScreen.DomainTypeBuilder =>
            val newSources = builder.state.alignedSources.updated(idx, alignedSource.copy(expanded = !alignedSource.expanded))
            state.copy(currentScreen = builder.copy(state = builder.state.copy(alignedSources = newSources)))
          case _ => state
        }
      }
    }

    // X button click handler - registered AFTER card handler so it takes priority
    val xButtonArea = Rect(xButtonX, xButtonY, 3, 1)
    ctx.onClick(xButtonArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case builder: AppScreen.DomainTypeBuilder =>
            val newSources = builder.state.alignedSources.patch(idx, Nil, 1)
            state.copy(currentScreen = builder.copy(state = builder.state.copy(alignedSources = newSources)))
          case _ => state
        }
      }
    }
  }

  private def renderSourcePicker(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val sources = getAvailableSources(props.globalState)
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val height: Int = math.min(area.height.toInt, sources.size + 3)
    val maxX: Int = x + width

    val pickerArea = Rect(x, y, width.toShort, height.toShort)
    ctx.frame.renderWidget(ClearWidget, pickerArea)

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Select Source", Style(fg = Some(Color.Yellow))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(Color.Cyan))
    )
    ctx.frame.renderWidget(block, pickerArea)

    var currentY = (y + 1).toShort
    val innerX = (x + 1).toShort
    val innerMaxX = maxX - 1

    sources.zipWithIndex.foreach { case (source, idx) =>
      if (currentY < y + height - 1) {
        val isSelected = idx == ui.sourcePickerIndex
        val rowStyle = if (isSelected) {
          Style(fg = Some(Color.Black), bg = Some(Color.Cyan), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }
        val prefix = if (isSelected) "> " else "  "
        val rowText = s"$prefix${source.value}".padTo(width - 2, ' ')
        safeSetString(ctx.buffer, innerX, currentY, innerMaxX, rowText, rowStyle)

        val rowArea = Rect(innerX, currentY, (width - 2).toShort, 1)
        ctx.onClick(rowArea) {
          ui.setSourcePickerIndex(idx)
          if (ui.addingAlignedSource) {
            ui.setSourcePickerOpen(false)
            ui.setEntityPickerOpen(true)
          } else {
            // Update primary source - this is data so needs scheduleStateUpdate
            Interactive.scheduleStateUpdate { state =>
              state.currentScreen match {
                case builder: AppScreen.DomainTypeBuilder =>
                  state.copy(currentScreen =
                    builder.copy(state =
                      builder.state.copy(
                        primarySourceName = Some(source.value),
                        primaryEntityPath = None,
                        primaryFields = Nil
                      )
                    )
                  )
                case _ => state
              }
            }
            ui.setSourcePickerOpen(false)
            ui.setPrimaryEntityPickerOpen(true)
          }
        }
        currentY = (currentY + 1).toShort
      }
    }
  }

  private def renderEntityPicker(ctx: RenderContext, area: Rect, props: Props, ui: UiState, isPrimary: Boolean): Unit = {
    val builderState = props.builderState
    val sourceName = if (isPrimary) {
      builderState.primarySourceName
    } else {
      getAvailableSources(props.globalState).lift(ui.sourcePickerIndex).map(_.value)
    }

    val entities = sourceName.map(s => getEntitiesFromSource(props.globalState, SourceName(s))).getOrElse(Nil)

    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val height: Int = math.min(area.height.toInt, math.min(entities.size + 3, 12))
    val maxX: Int = x + width

    val pickerArea = Rect(x, y, width.toShort, height.toShort)
    ctx.frame.renderWidget(ClearWidget, pickerArea)

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Select Entity", Style(fg = Some(Color.Yellow))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(Color.Cyan))
    )
    ctx.frame.renderWidget(block, pickerArea)

    var currentY = (y + 1).toShort
    val innerX = (x + 1).toShort
    val innerMaxX = maxX - 1
    val pickerIndex = if (isPrimary) ui.primaryEntityPickerIndex else ui.entityPickerIndex

    val visibleEntities = entities.slice(0, height - 2)
    visibleEntities.zipWithIndex.foreach { case (entity, idx) =>
      if (currentY < y + height - 1) {
        val isSelected = idx == pickerIndex
        val rowStyle = if (isSelected) {
          Style(fg = Some(Color.Black), bg = Some(Color.Cyan), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }
        val prefix = if (isSelected) "> " else "  "
        val rowText = s"$prefix$entity".take(width - 3).padTo(width - 2, ' ')
        safeSetString(ctx.buffer, innerX, currentY, innerMaxX, rowText, rowStyle)

        val rowArea = Rect(innerX, currentY, (width - 2).toShort, 1)
        ctx.onClick(rowArea) {
          if (isPrimary) {
            ui.setPrimaryEntityPickerIndex(idx)
            ui.setPrimaryEntityPickerOpen(false)
            // Load fields and update data
            Interactive.scheduleStateUpdate { state =>
              state.currentScreen match {
                case builder: AppScreen.DomainTypeBuilder =>
                  val newFields = loadFieldsFromSource(props.globalState, SourceName(sourceName.get), Some(entity))
                  val newName = if (builder.state.name.isEmpty) entity.split("\\.").last else builder.state.name
                  state.copy(currentScreen =
                    builder.copy(state =
                      builder.state.copy(
                        primaryEntityPath = Some(entity),
                        primaryFields = newFields,
                        name = newName
                      )
                    )
                  )
                case _ => state
              }
            }
          } else {
            ui.setEntityPickerIndex(idx)
            ui.setAddingAlignedSource(false)
            ui.setEntityPickerOpen(false)
            // Add aligned source - data update
            Interactive.scheduleStateUpdate { state =>
              state.currentScreen match {
                case builder: AppScreen.DomainTypeBuilder =>
                  val newAlignedSource = AlignedSourceBuilderState.empty(sourceName.get, entity)
                  state.copy(currentScreen =
                    builder.copy(state =
                      builder.state.copy(
                        alignedSources = builder.state.alignedSources :+ newAlignedSource
                      )
                    )
                  )
                case _ => state
              }
            }
          }
        }
        currentY = (currentY + 1).toShort
      }
    }
  }

  private def renderFooter(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val builderState = props.builderState
    val x: Short = area.x.toShort
    val y: Short = area.y.toShort
    val width: Int = area.width.toInt
    val maxX: Int = x + width

    val hintY = (y + 1).toShort
    val canSave = builderState.canSave

    val saveLabel = " Save "
    val cancelLabel = " Cancel "

    // Position buttons at right side
    val cancelX = math.max(x + 1, maxX - cancelLabel.length - 1).toShort
    val saveX = math.max(x + 1, cancelX - saveLabel.length - 1).toShort

    // Render hint on the left if there's room
    val hintMaxX = saveX - 2
    if (hintMaxX > x + 10) {
      val hint = "[Tab] Section  [Space] Toggle  [Enter] Save  [Esc] Back"
      safeSetString(ctx.buffer, (x + 1).toShort, hintY, hintMaxX, hint, Style(fg = Some(Color.DarkGray)))
    }

    // Render save button
    val hoverPos = props.globalState.hoverPosition
    val saveStyle = if (canSave) Style(fg = Some(Color.Green), addModifier = Modifier.BOLD) else Style(fg = Some(Color.DarkGray))
    val saveHoverStyle = if (canSave) Some(Style(fg = Some(Color.Black), bg = Some(Color.Green), addModifier = Modifier.BOLD)) else None

    Button(
      label = saveLabel,
      style = saveStyle,
      hoverStyle = saveHoverStyle,
      hoverPosition = hoverPos
    )(if (canSave) saveDomainType(props) else ()).render(ctx, Rect(saveX, hintY, saveLabel.length.toShort, 1))

    // Render cancel button
    val cancelStyle = Style(fg = Some(Color.Red))
    val cancelHoverStyle = Some(Style(fg = Some(Color.Black), bg = Some(Color.Red), addModifier = Modifier.BOLD))

    Button(
      label = cancelLabel,
      style = cancelStyle,
      hoverStyle = cancelHoverStyle,
      hoverPosition = hoverPos
    )(props.callbacks.goBack()).render(ctx, Rect(cancelX, hintY, cancelLabel.length.toShort, 1))
  }

  private def getAvailableSources(globalState: GlobalState): List[SourceName] = {
    globalState.sourceStatuses
      .collect { case (name, _: SourceStatus.Ready) => name }
      .toList
      .sortBy(_.value)
  }

  private def getEntitiesFromSource(globalState: GlobalState, sourceName: SourceName): List[String] = {
    globalState.sourceStatuses.get(sourceName) match {
      case Some(SourceStatus.Ready(metaDb, _, _, _, _, _)) =>
        metaDb.relations.keys.toList.map { relName =>
          relName.schema.map(s => s"$s.${relName.name}").getOrElse(relName.name)
        }.sorted
      case _ => Nil
    }
  }

  private def loadFieldsFromSource(globalState: GlobalState, sourceName: SourceName, entityPath: Option[String]): List[PrimaryFieldState] = {
    (globalState.sourceStatuses.get(sourceName), entityPath) match {
      case (Some(SourceStatus.Ready(metaDb, _, _, _, _, _)), Some(path)) =>
        val (schema, name) = path.split("\\.").toList match {
          case s :: n :: Nil => (Some(s), n)
          case n :: Nil      => (None, n)
          case _             => (None, path)
        }
        val relName = typr.db.RelationName(schema, name)
        metaDb.relations.get(relName).map(_.forceGet).toList.flatMap {
          case table: typr.db.Table =>
            table.cols.toList.map { col =>
              val colName = col.name.value
              val dbTypeStr = dbTypeToString(col.tpe)
              val isNullable = col.nullability != Nullability.NoNulls
              PrimaryFieldState(
                name = colName,
                dbType = dbTypeStr,
                nullable = isNullable,
                included = true,
                canonicalName = colName,
                canonicalType = mapDbTypeToJvmType(dbTypeStr),
                comment = ""
              )
            }
          case view: typr.db.View =>
            view.cols.toList.map { case (col, _) =>
              val colName = col.name.value
              val dbTypeStr = dbTypeToString(col.tpe)
              val isNullable = col.nullability != Nullability.NoNulls
              PrimaryFieldState(
                name = colName,
                dbType = dbTypeStr,
                nullable = isNullable,
                included = true,
                canonicalName = colName,
                canonicalType = mapDbTypeToJvmType(dbTypeStr),
                comment = ""
              )
            }
        }
      case _ => Nil
    }
  }

  private def dbTypeToString(tpe: typr.db.Type): String = {
    tpe.toString.split("\\.").last.replaceAll("\\(.*\\)", "")
  }

  private def mapDbTypeToJvmType(dbType: String): String = {
    val normalized = dbType.toUpperCase.replaceAll("\\(.*\\)", "").trim
    normalized match {
      case "INT4" | "INTEGER" | "INT" | "SERIAL"                    => "Int"
      case "INT8" | "BIGINT" | "BIGSERIAL"                          => "Long"
      case "INT2" | "SMALLINT" | "TINYINT"                          => "Short"
      case "FLOAT4" | "REAL" | "FLOAT"                              => "Float"
      case "FLOAT8" | "DOUBLE PRECISION" | "DOUBLE"                 => "Double"
      case "BOOL" | "BOOLEAN"                                       => "Boolean"
      case "NUMERIC" | "DECIMAL" | "DEC"                            => "BigDecimal"
      case "DATE"                                                   => "LocalDate"
      case "TIME" | "TIME WITHOUT TIME ZONE"                        => "LocalTime"
      case "TIMESTAMP" | "TIMESTAMP WITHOUT TIME ZONE" | "DATETIME" => "LocalDateTime"
      case "TIMESTAMPTZ" | "TIMESTAMP WITH TIME ZONE"               => "OffsetDateTime"
      case "UUID"                                                   => "UUID"
      case "BYTEA" | "BLOB" | "BINARY"                              => "ByteArray"
      case "JSON" | "JSONB"                                         => "Json"
      case _                                                        => "String"
    }
  }

  private def saveDomainType(props: Props): Unit = {
    Interactive.scheduleStateUpdate { state =>
      state.currentScreen match {
        case builder: AppScreen.DomainTypeBuilder =>
          val builderState = builder.state
          val domainType = builderState.toDomainTypeDefinition

          val domainTypeConfig = typr.config.generated.DomainType(
            fields = domainType.fields.map { f =>
              f.name -> typr.config.generated.FieldSpecObject(
                `type` = f.typeName,
                nullable = Some(f.nullable),
                array = Some(f.array),
                description = f.description,
                default = None
              )
            }.toMap,
            alignedSources =
              if (domainType.alignedSources.isEmpty) None
              else
                Some(
                  domainType.alignedSources.map { case (key, as) =>
                    key -> typr.config.generated.AlignedSource(
                      entity = Some(as.entityPath),
                      mode = Some(typr.bridge.CompatibilityMode.label(as.mode)),
                      mappings = if (as.mappings.isEmpty) None else Some(as.mappings),
                      exclude = if (as.exclude.isEmpty) None else Some(as.exclude.toList),
                      include_extra = if (as.includeExtra.isEmpty) None else Some(as.includeExtra),
                      readonly = if (as.readonly) Some(true) else None
                    )
                  }
                ),
            projections = None,
            primary = domainType.primary.map(_.key),
            description = domainType.description,
            generate = None
          )

          val newTypes = state.config.types.getOrElse(Map.empty) + (builderState.name -> domainTypeConfig)
          val newConfig = state.config.copy(types = Some(newTypes))
          props.callbacks.goBack()
          state.copy(config = newConfig, hasUnsavedChanges = true)
        case _ => state
      }
    }
  }

  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    val bufferArea = buffer.area
    if (availableWidth > 0 && x >= 0 && y >= bufferArea.y && y < bufferArea.y + bufferArea.height) {
      val truncated = text.take(availableWidth)
      buffer.setString(x, y, truncated, style)
    }
  }
}
