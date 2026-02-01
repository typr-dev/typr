package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.bridge.TypeSuggestion
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.SourceStatus
import typr.cli.tui.TypeWizardField
import typr.cli.tui.TypeWizardState
import typr.cli.tui.TypeWizardStep
import typr.cli.tui.navigation.Navigator

/** React-based TypeWizard with local UI state via useState */
object TypeWizardReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      wizardState: TypeWizardState
  )

  /** UI-only state managed locally via useState */
  case class UiState(
      focusedField: TypeWizardField,
      setFocusedField: TypeWizardField => Unit,
      selectedSuggestionIndex: Int,
      setSelectedSuggestionIndex: Int => Unit,
      suggestionScrollOffset: Int,
      setSuggestionScrollOffset: Int => Unit,
      error: Option[String],
      setError: Option[String] => Unit
  )

  def component(props: Props): Component = {
    Component.named("TypeWizardReact") { (ctx, area) =>
      // Initialize local UI state with useState
      val (focusedField, setFocusedField) = ctx.useState[TypeWizardField](TypeWizardField.Name)
      val (selectedSuggestionIndex, setSelectedSuggestionIndex) = ctx.useState(0)
      val (suggestionScrollOffset, setSuggestionScrollOffset) = ctx.useState(0)
      val (error, setError) = ctx.useState[Option[String]](None)

      val ui = UiState(
        focusedField = focusedField,
        setFocusedField = setFocusedField,
        selectedSuggestionIndex = selectedSuggestionIndex,
        setSelectedSuggestionIndex = setSelectedSuggestionIndex,
        suggestionScrollOffset = suggestionScrollOffset,
        setSuggestionScrollOffset = setSuggestionScrollOffset,
        error = error,
        setError = setError
      )

      val wizardState = props.wizardState

      if (wizardState.step == TypeWizardStep.SelectFromSuggestions && wizardState.suggestions.nonEmpty) {
        renderSuggestionsPicker(ctx, area, props, ui)
      } else {
        renderForm(ctx, area, props, ui)
      }
    }
  }

  private def renderSuggestionsPicker(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(area)

    renderSuggestionsPanel(ctx, chunks(0), wizardState, props.callbacks, ui)
    renderSuggestionPreview(ctx, chunks(1), wizardState, props.globalState, ui)
  }

  private def renderForm(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(55), Constraint.Percentage(45))
    ).split(area)

    renderFormPanel(ctx, chunks(0), props, ui)
    renderPreviewPanel(ctx, chunks(1), props)
  }

  private def renderFormPanel(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val wizardState = props.wizardState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Add Field Type", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerArea = Rect(
      x = (area.x + 2).toShort,
      y = (area.y + 2).toShort,
      width = math.max(10, area.width - 4).toShort,
      height = math.max(10, area.height - 4).toShort
    )

    var y: Short = innerArea.y.toShort
    val ix: Short = innerArea.x.toShort
    val iw: Int = innerArea.width.toInt
    val maxX: Int = ix + iw

    // Name field - large clickable area
    renderFormField(ctx, ix, y, iw, maxX, "Name", wizardState.name, "e.g. Email, CustomerId", ui.focusedField == TypeWizardField.Name, TypeWizardField.Name, ui)
    y = (y + 3).toShort

    // DB Columns field - large clickable area
    renderFormField(
      ctx,
      ix,
      y,
      iw,
      maxX,
      "DB Columns",
      wizardState.dbColumns,
      "e.g. email, *_email",
      ui.focusedField == TypeWizardField.DbColumns,
      TypeWizardField.DbColumns,
      ui
    )
    y = (y + 3).toShort

    // Primary Key field - large toggle button
    renderToggleField(ctx, ix, y, iw, maxX, "Primary Key", wizardState.primaryKey, ui.focusedField == TypeWizardField.PrimaryKey, ui)
    y = (y + 3).toShort

    // Model Names field - large clickable area
    renderFormField(
      ctx,
      ix,
      y,
      iw,
      maxX,
      "Model Names",
      wizardState.modelNames,
      "OpenAPI models (optional)",
      ui.focusedField == TypeWizardField.ModelNames,
      TypeWizardField.ModelNames,
      ui
    )
    y = (y + 4).toShort

    // Action buttons - large clickable areas
    val createFocused = ui.focusedField == TypeWizardField.CreateButton
    val cancelFocused = ui.focusedField == TypeWizardField.CancelButton

    renderLargeButton(ctx, ix, y, 22, "  Create Field Type  ", createFocused, true, TypeWizardField.CreateButton, handleSubmit(props, ui), ui)
    renderLargeButton(ctx, (ix + 20).toShort, y, 12, "  Cancel  ", cancelFocused, false, TypeWizardField.CancelButton, () => props.callbacks.goBack(), ui)
    y = (y + 3).toShort

    // Error message
    ui.error.foreach { err =>
      safeSetString(ctx.buffer, ix, y, maxX, s"Error: $err", Style(fg = Some(Color.Red)))
    }

    // Keyboard hints at bottom
    val hintY = math.min((area.y + area.height - 2).toInt, (innerArea.y + innerArea.height).toInt).toShort
    safeSetString(ctx.buffer, ix, hintY, maxX, "[Tab] Next field  [Enter] Submit  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderFormField(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      label: String,
      value: String,
      placeholder: String,
      isFocused: Boolean,
      field: TypeWizardField,
      ui: UiState
  ): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    safeSetString(ctx.buffer, x, y, maxX, label, labelStyle)

    val inputY = (y + 1).toShort
    val inputWidth = math.min(width - 2, 40)
    val inputStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue))
      else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    // Draw input background
    val bgStr = " " * inputWidth
    safeSetString(ctx.buffer, x, inputY, maxX, bgStr, inputStyle)

    // Draw value or placeholder
    val displayText = if (value.nonEmpty) {
      val cursor = if (isFocused) "\u2588" else ""
      (value + cursor).take(inputWidth - 1)
    } else if (isFocused) {
      "\u2588"
    } else {
      placeholder.take(inputWidth - 1)
    }
    val textStyle =
      if (value.isEmpty && !isFocused) Style(fg = Some(Color.Gray), bg = Some(Color.DarkGray))
      else inputStyle
    safeSetString(ctx.buffer, x, inputY, maxX, displayText, textStyle)

    // Clickable area covers both label and input
    val clickArea = Rect(x, y, width.toShort, 2)
    ctx.onClick(clickArea) {
      ui.setFocusedField(field)
      ui.setError(None)
    }
  }

  private def renderToggleField(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      label: String,
      value: Option[Boolean],
      isFocused: Boolean,
      ui: UiState
  ): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    safeSetString(ctx.buffer, x, y, maxX, label, labelStyle)

    val buttonY = (y + 1).toShort
    val valueStr = value match {
      case None        => "  Any  "
      case Some(true)  => "  Yes  "
      case Some(false) => "  No   "
    }

    val buttonStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    safeSetString(ctx.buffer, x, buttonY, maxX, valueStr, buttonStyle)

    // Large clickable area
    val clickArea = Rect(x, y, math.min(width, 20).toShort, 2)
    ctx.onClick(clickArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case wizard: AppScreen.TypeWizard =>
            val ws = wizard.state
            val newPk = ws.primaryKey match {
              case None        => Some(true)
              case Some(true)  => Some(false)
              case Some(false) => None
            }
            state.copy(currentScreen = wizard.copy(state = ws.copy(primaryKey = newPk)))
          case _ => state
        }
      }
      ui.setFocusedField(TypeWizardField.PrimaryKey)
      ui.setError(None)
    }
  }

  private def renderLargeButton(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      label: String,
      isFocused: Boolean,
      isPrimary: Boolean,
      field: TypeWizardField,
      onPress: () => Unit,
      ui: UiState
  ): Unit = {
    val (normalBg, focusBg) = if (isPrimary) (Color.Green, Color.Cyan) else (Color.Gray, Color.Blue)
    val style =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(focusBg), addModifier = Modifier.BOLD)
      else Style(fg = Some(Color.White), bg = Some(normalBg))

    ctx.buffer.setString(x, y, label, style)

    val clickArea = Rect(x, y, label.length.toShort, 1)
    ctx.onClick(clickArea) {
      ui.setFocusedField(field)
      ui.setError(None)
      onPress()
    }
  }

  private def handleSubmit(props: Props, ui: UiState): () => Unit = () => {
    val ws = props.wizardState
    if (ws.name.isEmpty) {
      ui.setError(Some("Name is required"))
    } else if (ws.dbColumns.isEmpty && ws.modelNames.isEmpty) {
      ui.setError(Some("At least one pattern is required"))
    } else {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case wizard: AppScreen.TypeWizard =>
            val types = state.config.types.getOrElse(Map.empty)
            if (types.contains(ws.name)) {
              // Can't set error from inside scheduleStateUpdate, but we check outside first
              state
            } else {
              val newTypes = types + (ws.name -> ws.toFieldType)
              val newConfig = state.config.copy(types = Some(newTypes))
              Navigator.goBack(
                state.copy(
                  config = newConfig,
                  hasUnsavedChanges = true,
                  statusMessage = Some((s"Field type '${ws.name}' created", Color.Green))
                )
              )
            }
          case _ => state
        }
      }
      // Check for duplicate name outside of scheduleStateUpdate
      val types = props.globalState.config.types.getOrElse(Map.empty)
      if (types.contains(ws.name)) {
        ui.setError(Some("Type name already exists"))
      }
    }
  }

  private def renderPreviewPanel(ctx: RenderContext, area: Rect, props: Props): Unit = {
    val pattern = props.wizardState.dbColumns

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Live Preview", Style(fg = Some(Color.Cyan))))),
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
    val ix: Short = innerArea.x.toShort
    val iw: Int = innerArea.width.toInt
    val ih: Int = innerArea.height.toInt
    val maxX: Int = ix + iw

    if (pattern.isEmpty) {
      safeSetString(ctx.buffer, ix, y, maxX, "Enter a pattern to see matches", Style(fg = Some(Color.DarkGray)))
      y = (y + 2).toShort
      safeSetString(ctx.buffer, ix, y, maxX, "Pattern examples:", Style(fg = Some(Color.Cyan)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, ix, y, maxX, "  email     - exact match", Style(fg = Some(Color.Gray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, ix, y, maxX, "  *_email   - ends with _email", Style(fg = Some(Color.Gray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, ix, y, maxX, "  email*    - starts with email", Style(fg = Some(Color.Gray)))
    } else {
      val patterns = pattern.split(",").map(_.trim).filter(_.nonEmpty).toList
      val matchingCols = findMatchingColumns(patterns, props.globalState)

      safeSetString(ctx.buffer, ix, y, maxX, s"Pattern: ${patterns.take(3).mkString(", ")}", Style(fg = Some(Color.Cyan)))
      y = (y + 2).toShort

      if (matchingCols.isEmpty) {
        safeSetString(ctx.buffer, ix, y, maxX, "No columns match", Style(fg = Some(Color.DarkGray)))
      } else {
        safeSetString(ctx.buffer, ix, y, maxX, s"Matches: ${matchingCols.size} columns", Style(fg = Some(Color.Green)))
        y = (y + 1).toShort

        matchingCols.take(8).foreach { case (source, table, col, dbType) =>
          if (y < innerArea.y + ih - 1) {
            val text = s"  $col"
            safeSetString(ctx.buffer, ix, y, maxX, text, Style(fg = Some(Color.White)))
            y = (y + 1).toShort
          }
        }

        if (matchingCols.size > 8) {
          safeSetString(ctx.buffer, ix, y, maxX, s"  ... +${matchingCols.size - 8} more", Style(fg = Some(Color.DarkGray)))
        }
      }
    }
  }

  private def renderSuggestionsPanel(ctx: RenderContext, area: Rect, wizardState: TypeWizardState, callbacks: GlobalCallbacks, ui: UiState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Type Suggestions", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
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
    val ix: Short = innerArea.x.toShort
    val iw: Int = innerArea.width.toInt
    val ih: Int = innerArea.height.toInt
    val maxX: Int = ix + iw

    safeSetString(ctx.buffer, ix, y, maxX, "Click to select, or Tab to skip", Style(fg = Some(Color.DarkGray)))
    y = (y + 2).toShort

    val rowHeight = 2
    val visibleRows = math.max(1, (ih - 4) / rowHeight)
    val startIdx = ui.suggestionScrollOffset
    val endIdx = math.min(wizardState.suggestions.size, startIdx + visibleRows)
    val visibleSuggestions = wizardState.suggestions.slice(startIdx, endIdx)

    visibleSuggestions.zipWithIndex.foreach { case (sugg, relIdx) =>
      if (y < innerArea.y + ih - 2) {
        val idx = startIdx + relIdx
        val isSelected = idx == ui.selectedSuggestionIndex

        val rowBg = if (isSelected) Some(Color.Blue) else None
        val nameStyle = Style(fg = Some(Color.Yellow), bg = rowBg, addModifier = Modifier.BOLD)
        val detailStyle = Style(fg = Some(Color.Gray), bg = rowBg)

        // Clear the row background
        if (isSelected) {
          val bgStr = " " * iw
          safeSetString(ctx.buffer, ix, y, maxX, bgStr, Style(bg = Some(Color.Blue)))
        }

        // Name on first line
        val prefix = if (isSelected) "> " else "  "
        safeSetString(ctx.buffer, ix, y, maxX, s"$prefix${sugg.name}", nameStyle)
        y = (y + 1).toShort

        // Details on second line
        if (y < innerArea.y + ih - 1) {
          safeSetString(ctx.buffer, ix, y, maxX, s"    ${sugg.frequency} columns matched", detailStyle)
        }

        // Clickable area covers both lines
        val rowArea = Rect(ix, (y - 1).toShort, iw.toShort, 2)
        ctx.onClick(rowArea) {
          Interactive.scheduleStateUpdate { state =>
            state.currentScreen match {
              case wizard: AppScreen.TypeWizard =>
                val newState = TypeWizardState.fromSuggestion(sugg, wizard.state.suggestions)
                state.copy(currentScreen = wizard.copy(state = newState))
              case _ => state
            }
          }
        }
        ctx.onHover(rowArea)(ui.setSelectedSuggestionIndex(idx))

        y = (y + 1).toShort
      }
    }

    // Show scrollbar if needed
    val hasScrollbar = wizardState.suggestions.size > visibleRows
    if (hasScrollbar) {
      val scrollbarX = (area.x + area.width - 2).toShort
      val scrollbarY = (area.y + 4).toShort
      val scrollbarHeight = math.max(5, area.height - 6)
      ScrollbarWithArrows(
        totalItems = wizardState.suggestions.size,
        visibleItems = visibleRows,
        scrollOffset = startIdx,
        trackColor = Color.DarkGray,
        thumbColor = Color.Gray,
        arrowColor = Color.Cyan,
        onScrollUp = Some(() => {
          val newOffset = math.max(0, ui.suggestionScrollOffset - 1)
          ui.setSuggestionScrollOffset(newOffset)
        }),
        onScrollDown = Some(() => {
          val maxOffset = math.max(0, wizardState.suggestions.size - visibleRows)
          val newOffset = math.min(maxOffset, ui.suggestionScrollOffset + 1)
          ui.setSuggestionScrollOffset(newOffset)
        })
      ).render(ctx, Rect(scrollbarX, scrollbarY, 1, scrollbarHeight.toShort))
    }

    // Scroll support
    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedSuggestionIndex - 1)
      ui.setSelectedSuggestionIndex(newIdx)
      if (newIdx < ui.suggestionScrollOffset) {
        ui.setSuggestionScrollOffset(newIdx)
      }
    }

    ctx.onScrollDown(area) {
      val maxIdx = math.max(0, wizardState.suggestions.size - 1)
      val newIdx = math.min(maxIdx, ui.selectedSuggestionIndex + 1)
      ui.setSelectedSuggestionIndex(newIdx)
      if (newIdx >= ui.suggestionScrollOffset + visibleRows) {
        ui.setSuggestionScrollOffset(newIdx - visibleRows + 1)
      }
    }

    // Hint at bottom
    val hintY = math.min((area.y + area.height - 2).toInt, (innerArea.y + ih).toInt).toShort
    safeSetString(ctx.buffer, ix, hintY, maxX, "[Scroll/Up/Down] Select  [Enter] Use", Style(fg = Some(Color.DarkGray)))
  }

  private def renderSuggestionPreview(ctx: RenderContext, area: Rect, wizardState: TypeWizardState, globalState: GlobalState, ui: UiState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Preview", Style(fg = Some(Color.Cyan))))),
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
    val ix: Short = innerArea.x.toShort
    val iw: Int = innerArea.width.toInt
    val ih: Int = innerArea.height.toInt
    val maxX: Int = ix + iw

    if (wizardState.suggestions.isEmpty || ui.selectedSuggestionIndex >= wizardState.suggestions.size) {
      safeSetString(ctx.buffer, ix, y, maxX, "No suggestion selected", Style(fg = Some(Color.DarkGray)))
      return
    }

    val suggestion = wizardState.suggestions(ui.selectedSuggestionIndex)

    safeSetString(ctx.buffer, ix, y, maxX, suggestion.name, Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    safeSetString(ctx.buffer, ix, y, maxX, s"Pattern: ${suggestion.dbColumnPatterns.take(2).mkString(", ")}", Style(fg = Some(Color.Cyan)))
    y = (y + 2).toShort

    if (suggestion.matchingColumns.nonEmpty) {
      safeSetString(ctx.buffer, ix, y, maxX, s"${suggestion.matchingColumns.size} columns matched:", Style(fg = Some(Color.Green)))
      y = (y + 1).toShort

      suggestion.matchingColumns.take(10).foreach { col =>
        if (y < innerArea.y + ih - 1) {
          safeSetString(ctx.buffer, ix, y, maxX, s"  ${col.column}", Style(fg = Some(Color.White)))
          y = (y + 1).toShort
        }
      }

      if (suggestion.matchingColumns.size > 10) {
        safeSetString(ctx.buffer, ix, y, maxX, s"  ... +${suggestion.matchingColumns.size - 10} more", Style(fg = Some(Color.DarkGray)))
      }
    }
  }

  /** Safe string writing that truncates to prevent buffer overflows */
  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    val bufferArea = buffer.area
    if (availableWidth > 0 && x >= 0 && y >= bufferArea.y && y < bufferArea.y + bufferArea.height) {
      val truncated = text.take(availableWidth)
      buffer.setString(x, y, truncated, style)
    }
  }

  private def findMatchingColumns(patterns: List[String], state: GlobalState): List[(String, String, String, typr.db.Type)] = {
    val results = scala.collection.mutable.ListBuffer[(String, String, String, typr.db.Type)]()

    state.sourceStatuses.foreach { case (sourceName, status) =>
      status match {
        case ready: SourceStatus.Ready =>
          ready.metaDb.relations.foreach { case (relName, lazyRel) =>
            val cols: List[typr.db.Col] = lazyRel.forceGet match {
              case t: typr.db.Table => t.cols.toList
              case v: typr.db.View  => v.cols.toList.map(_._1)
            }

            cols.foreach { col =>
              val colName = col.name.value
              if (patterns.exists(p => globMatches(p, colName))) {
                val tableName = relName.schema.map(_ + ".").getOrElse("") + relName.name
                results += ((sourceName.value, tableName, colName, col.tpe))
              }
            }
          }
        case _ => ()
      }
    }

    results.toList.sortBy(r => (r._1, r._2, r._3))
  }

  private def globMatches(pattern: String, value: String): Boolean = {
    val escaped = pattern.toLowerCase
      .replace(".", "\\.")
      .replace("**", "\u0000")
      .replace("*", ".*")
      .replace("\u0000", ".*")
      .replace("?", ".")
    s"^$escaped$$".r.matches(value.toLowerCase)
  }
}
