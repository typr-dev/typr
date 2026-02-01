package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.bridge.TypeSuggestion
import typr.cli.tui.AppScreen
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.TypeWizardField
import typr.cli.tui.TypeWizardState
import typr.cli.tui.TypeWizardStep
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object TypeWizard {

  def handleKey(state: TuiState, wizard: AppScreen.TypeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state

    // If on suggestions step, handle differently
    if (wizardState.step == TypeWizardStep.SelectFromSuggestions) {
      handleSuggestionsKey(state, wizard, keyCode)
    } else {
      handleFormKey(state, wizard, keyCode)
    }
  }

  private def handleSuggestionsKey(state: TuiState, wizard: AppScreen.TypeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state

    keyCode match {
      case _: KeyCode.Up =>
        val newIdx = math.max(0, wizardState.selectedSuggestionIndex - 1)
        val newOffset = if (newIdx < wizardState.suggestionScrollOffset) newIdx else wizardState.suggestionScrollOffset
        val newState = wizardState.copy(selectedSuggestionIndex = newIdx, suggestionScrollOffset = newOffset)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Down =>
        val maxIdx = math.max(0, wizardState.suggestions.size - 1)
        val newIdx = math.min(maxIdx, wizardState.selectedSuggestionIndex + 1)
        val visibleRows = 12
        val newOffset = if (newIdx >= wizardState.suggestionScrollOffset + visibleRows) newIdx - visibleRows + 1 else wizardState.suggestionScrollOffset
        val newState = wizardState.copy(selectedSuggestionIndex = newIdx, suggestionScrollOffset = newOffset)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Tab =>
        // Skip suggestions and go to form
        val newState = wizardState.copy(step = TypeWizardStep.EnterName, error = None)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Enter =>
        if (wizardState.suggestions.nonEmpty && wizardState.selectedSuggestionIndex < wizardState.suggestions.size) {
          val suggestion = wizardState.suggestions(wizardState.selectedSuggestionIndex)
          val newState = TypeWizardState.fromSuggestion(suggestion, wizardState.suggestions)
          state.copy(currentScreen = wizard.copy(state = newState))
        } else {
          val newState = wizardState.copy(step = TypeWizardStep.EnterName, error = None)
          state.copy(currentScreen = wizard.copy(state = newState))
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case _ => state
    }
  }

  private def handleFormKey(state: TuiState, wizard: AppScreen.TypeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state

    keyCode match {
      case _: KeyCode.Tab =>
        val newField = TypeWizardField.next(wizardState.focusedField)
        val newState = wizardState.copy(focusedField = newField, error = None)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.BackTab =>
        val newField = TypeWizardField.prev(wizardState.focusedField)
        val newState = wizardState.copy(focusedField = newField, error = None)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Enter =>
        wizardState.focusedField match {
          case TypeWizardField.CreateButton =>
            handleSubmit(state, wizard)
          case TypeWizardField.CancelButton =>
            Navigator.goBack(state)
          case TypeWizardField.PrimaryKey =>
            cyclePrimaryKey(state, wizard)
          case _ =>
            // Move to next field on Enter
            val newField = TypeWizardField.next(wizardState.focusedField)
            val newState = wizardState.copy(focusedField = newField, error = None)
            state.copy(currentScreen = wizard.copy(state = newState))
        }

      case _: KeyCode.Backspace =>
        val newState = wizardState.focusedField match {
          case TypeWizardField.Name =>
            wizardState.copy(name = wizardState.name.dropRight(1))
          case TypeWizardField.DbColumns =>
            wizardState.copy(dbColumns = wizardState.dbColumns.dropRight(1))
          case TypeWizardField.ModelNames =>
            wizardState.copy(modelNames = wizardState.modelNames.dropRight(1))
          case _ => wizardState
        }
        state.copy(currentScreen = wizard.copy(state = newState))

      case c: KeyCode.Char =>
        val ch = c.c()
        val newState = wizardState.focusedField match {
          case TypeWizardField.Name =>
            wizardState.copy(name = wizardState.name + ch)
          case TypeWizardField.DbColumns =>
            wizardState.copy(dbColumns = wizardState.dbColumns + ch)
          case TypeWizardField.ModelNames =>
            wizardState.copy(modelNames = wizardState.modelNames + ch)
          case TypeWizardField.PrimaryKey =>
            // Space toggles the value
            if (ch == ' ') cyclePrimaryKeyState(wizardState)
            else wizardState
          case _ => wizardState
        }
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Left | _: KeyCode.Right =>
        if (wizardState.focusedField == TypeWizardField.PrimaryKey) {
          cyclePrimaryKey(state, wizard)
        } else {
          state
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case _ => state
    }
  }

  private def cyclePrimaryKey(state: TuiState, wizard: AppScreen.TypeWizard): TuiState = {
    val newState = cyclePrimaryKeyState(wizard.state)
    state.copy(currentScreen = wizard.copy(state = newState))
  }

  private def cyclePrimaryKeyState(wizardState: TypeWizardState): TypeWizardState = {
    val next = wizardState.primaryKey match {
      case None        => Some(true)
      case Some(true)  => Some(false)
      case Some(false) => None
    }
    wizardState.copy(primaryKey = next)
  }

  private def handleSubmit(state: TuiState, wizard: AppScreen.TypeWizard): TuiState = {
    val wizardState = wizard.state

    if (wizardState.name.isEmpty) {
      val newState = wizardState.copy(error = Some("Name is required"))
      state.copy(currentScreen = wizard.copy(state = newState))
    } else if (wizardState.dbColumns.isEmpty && wizardState.modelNames.isEmpty) {
      val newState = wizardState.copy(error = Some("At least one pattern (DB or Model) is required"))
      state.copy(currentScreen = wizard.copy(state = newState))
    } else {
      val types = state.config.types.getOrElse(Map.empty)
      if (types.contains(wizardState.name)) {
        val newState = wizardState.copy(error = Some("Type name already exists"))
        state.copy(currentScreen = wizard.copy(state = newState))
      } else {
        val newTypes = types + (wizardState.name -> wizardState.toFieldType)
        val newConfig = state.config.copy(types = Some(newTypes))
        val updatedState = state.copy(
          config = newConfig,
          currentScreen = AppScreen.TypeList(selectedIndex = 0, typeKindFilter = None),
          hasUnsavedChanges = true,
          statusMessage = Some((s"Field type '${wizardState.name}' created", Color.Green))
        )
        Navigator.goBack(updatedState)
      }
    }
  }

  def render(f: Frame, state: TuiState, wizard: AppScreen.TypeWizard): Unit = {
    // Legacy render - will be replaced by TypeWizardReact
    val wizardState = wizard.state

    if (wizardState.step == TypeWizardStep.SelectFromSuggestions && wizardState.suggestions.nonEmpty) {
      val chunks = Layout(
        direction = Direction.Horizontal,
        margin = Margin(1),
        constraints = Array(Constraint.Percentage(40), Constraint.Percentage(40), Constraint.Percentage(20))
      ).split(f.size)

      renderSuggestions(f, chunks(0), wizardState, state)
      renderSuggestionPreview(f, chunks(1), wizardState, state)
      renderProgress(f, chunks(2), wizardState)
    } else {
      val chunks = Layout(
        direction = Direction.Horizontal,
        margin = Margin(1),
        constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
      ).split(f.size)

      renderForm(f, chunks(0), wizardState)
      renderPatternPreview(f, chunks(1), wizardState.dbColumns, state)
    }
  }

  private def renderForm(f: Frame, area: Rect, wizardState: TypeWizardState): Unit = {
    val lines = scala.collection.mutable.ListBuffer[Spans]()

    lines += Spans.from(Span.styled("Add Field Type", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)))
    lines += Spans.nostyle("")

    // Name field
    val nameStyle = if (wizardState.focusedField == TypeWizardField.Name) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White))
    }
    val nameCursor = if (wizardState.focusedField == TypeWizardField.Name) "█" else ""
    lines += Spans.from(
      Span.styled("Name: ", Style(fg = Some(Color.Cyan))),
      Span.styled(wizardState.name + nameCursor, nameStyle)
    )
    lines += Spans.nostyle("")

    // DB Columns field
    val dbStyle = if (wizardState.focusedField == TypeWizardField.DbColumns) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White))
    }
    val dbCursor = if (wizardState.focusedField == TypeWizardField.DbColumns) "█" else ""
    lines += Spans.from(
      Span.styled("DB Columns: ", Style(fg = Some(Color.Cyan))),
      Span.styled(wizardState.dbColumns + dbCursor, dbStyle)
    )
    lines += Spans.from(Span.styled("  (comma-separated patterns, e.g. email, *_email)", Style(fg = Some(Color.DarkGray))))
    lines += Spans.nostyle("")

    // Primary Key field
    val pkStyle = if (wizardState.focusedField == TypeWizardField.PrimaryKey) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White))
    }
    val pkValue = wizardState.primaryKey match {
      case None        => "(any)"
      case Some(true)  => "Yes"
      case Some(false) => "No"
    }
    lines += Spans.from(
      Span.styled("Primary Key: ", Style(fg = Some(Color.Cyan))),
      Span.styled(s"[$pkValue]", pkStyle)
    )
    lines += Spans.nostyle("")

    // Model Names field
    val modelStyle = if (wizardState.focusedField == TypeWizardField.ModelNames) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White))
    }
    val modelCursor = if (wizardState.focusedField == TypeWizardField.ModelNames) "█" else ""
    lines += Spans.from(
      Span.styled("Model Names: ", Style(fg = Some(Color.Cyan))),
      Span.styled(wizardState.modelNames + modelCursor, modelStyle)
    )
    lines += Spans.from(Span.styled("  (for OpenAPI/JSON Schema matching, optional)", Style(fg = Some(Color.DarkGray))))
    lines += Spans.nostyle("")
    lines += Spans.nostyle("")

    // Buttons
    val createStyle = if (wizardState.focusedField == TypeWizardField.CreateButton) {
      Style(fg = Some(Color.White), bg = Some(Color.Green), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
    }
    val cancelStyle = if (wizardState.focusedField == TypeWizardField.CancelButton) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
    }
    lines += Spans.from(
      Span.styled(" Create Field Type ", createStyle),
      Span.nostyle("  "),
      Span.styled(" Cancel ", cancelStyle)
    )
    lines += Spans.nostyle("")

    // Error message
    wizardState.error.foreach { err =>
      lines += Spans.from(Span.styled(s"⚠ $err", Style(fg = Some(Color.Red))))
    }

    lines += Spans.nostyle("")
    lines += Spans.from(Span.styled("[Tab] Next  [Enter] Submit/Toggle  [Esc] Cancel", Style(fg = Some(Color.DarkGray))))

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Field Type Definition", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines.toList*)
    )
    f.renderWidget(para, area)
  }

  private def renderSuggestions(f: Frame, area: Rect, wizardState: TypeWizardState, tuiState: TuiState): Unit = {
    val lines = scala.collection.mutable.ListBuffer[Spans]()

    lines += Spans.from(Span.styled("Select a Suggestion", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)))
    lines += Spans.nostyle("")
    lines += Spans.from(Span.nostyle("Based on column analysis across your sources:"))
    lines += Spans.nostyle("")

    if (wizardState.suggestions.isEmpty) {
      lines += Spans.from(Span.styled("No suggestions available.", Style(fg = Some(Color.DarkGray))))
      lines += Spans.nostyle("")
      lines += Spans.from(Span.nostyle("Press Tab or Enter to create manually."))
    } else {
      val visibleRows = 12
      val startIdx = wizardState.suggestionScrollOffset
      val endIdx = math.min(wizardState.suggestions.size, startIdx + visibleRows)
      val visibleSuggestions = wizardState.suggestions.slice(startIdx, endIdx)

      visibleSuggestions.zipWithIndex.foreach { case (sugg, relIdx) =>
        val idx = startIdx + relIdx
        val isSelected = idx == wizardState.selectedSuggestionIndex
        val prefix = if (isSelected) "> " else "  "
        val nameStyle = if (isSelected) {
          Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD, bg = Some(Color.Blue))
        } else {
          Style(fg = Some(Color.White))
        }
        val detailStyle = if (isSelected) {
          Style(fg = Some(Color.Cyan), bg = Some(Color.Blue))
        } else {
          Style(fg = Some(Color.DarkGray))
        }

        lines += Spans.from(
          Span.styled(prefix, if (isSelected) Style(bg = Some(Color.Blue)) else Style()),
          Span.styled(sugg.name, nameStyle),
          Span.styled(s" (${sugg.frequency} cols, ${sugg.sources.size} sources)", detailStyle)
        )
      }

      if (wizardState.suggestions.size > visibleRows) {
        val above = startIdx
        val below = wizardState.suggestions.size - endIdx
        val indicator =
          if (above > 0 && below > 0) s"($above more above, $below more below)"
          else if (above > 0) s"($above more above)"
          else if (below > 0) s"($below more below)"
          else ""
        if (indicator.nonEmpty) lines += Spans.from(Span.styled(indicator, Style(fg = Some(Color.DarkGray))))
      }

      lines += Spans.nostyle("")
      lines += Spans.from(Span.styled("[Up/Down] Select  [Enter] Use  [Tab] Skip", Style(fg = Some(Color.DarkGray))))
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Suggestions", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines.toList*)
    )
    f.renderWidget(para, area)
  }

  private def renderProgress(f: Frame, area: Rect, wizardState: TypeWizardState): Unit = {
    val hasSuggestions = wizardState.suggestions.nonEmpty
    val steps = (if (hasSuggestions) List(("0. Suggestions", TypeWizardStep.SelectFromSuggestions)) else Nil) ++ List(
      ("1. Form", TypeWizardStep.EnterName)
    )

    val stepIndex = steps.indexWhere(_._2 == wizardState.step)

    val lines = List(
      Spans.from(Span.styled("Progress", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle("")
    ) ++ steps.zipWithIndex.map { case ((name, step), idx) =>
      val isCurrent = step == wizardState.step || (wizardState.step != TypeWizardStep.SelectFromSuggestions && step == TypeWizardStep.EnterName)
      val isCompleted = idx < stepIndex
      val style = if (isCurrent) {
        Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
      } else if (isCompleted) {
        Style(fg = Some(Color.Green))
      } else {
        Style(fg = Some(Color.DarkGray))
      }
      val prefix = if (isCurrent) "> " else if (isCompleted) "✓ " else "  "
      Spans.from(Span.styled(prefix + name, style))
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Steps", Style(fg = Some(Color.Cyan))))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def renderSuggestionPreview(f: Frame, area: Rect, wizardState: TypeWizardState, tuiState: TuiState): Unit = {
    val lines: List[Spans] = if (wizardState.suggestions.isEmpty || wizardState.selectedSuggestionIndex >= wizardState.suggestions.size) {
      List(
        Spans.from(Span.styled("No suggestion selected", Style(fg = Some(Color.DarkGray))))
      )
    } else {
      val suggestion = wizardState.suggestions(wizardState.selectedSuggestionIndex)

      val headerLines = List(
        Spans.from(Span.styled(s"Preview: ${suggestion.name}", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
        Spans.nostyle(""),
        Spans.from(
          Span.styled("Pattern: ", Style(fg = Some(Color.Cyan))),
          Span.styled(suggestion.dbColumnPatterns.mkString(", "), Style(fg = Some(Color.White)))
        ),
        Spans.nostyle("")
      )

      val compatibleLines = if (suggestion.matchingColumns.nonEmpty) {
        List(
          Spans.from(
            Span.styled("Compatible: ", Style(fg = Some(Color.Green))),
            Span.styled(s"${suggestion.matchingColumns.size} columns", Style(fg = Some(Color.White)))
          )
        ) ++ suggestion.matchingColumns.take(8).map { col =>
          val typeName = col.dbType.getClass.getSimpleName.stripSuffix("$")
          Spans.from(
            Span.styled(s"  ${col.source}.${col.table}.${col.column}", Style(fg = Some(Color.Gray))),
            Span.styled(s" ($typeName)", Style(fg = Some(Color.DarkGray)))
          )
        } ++ (if (suggestion.matchingColumns.size > 8) {
                List(Spans.from(Span.styled(s"  ... and ${suggestion.matchingColumns.size - 8} more", Style(fg = Some(Color.DarkGray)))))
              } else Nil)
      } else Nil

      headerLines ++ compatibleLines
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Matched Columns", Style(fg = Some(Color.Cyan))))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def renderPatternPreview(f: Frame, area: Rect, pattern: String, tuiState: TuiState): Unit = {
    val lines: List[Spans] = if (pattern.isEmpty) {
      List(
        Spans.from(Span.styled("Live Preview", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
        Spans.nostyle(""),
        Spans.from(Span.styled("Start typing a pattern to see matches.", Style(fg = Some(Color.DarkGray)))),
        Spans.nostyle(""),
        Spans.from(Span.styled("Pattern examples:", Style(fg = Some(Color.Cyan)))),
        Spans.from(Span.styled("  email       - exact match", Style(fg = Some(Color.Gray)))),
        Spans.from(Span.styled("  *_email     - ends with _email", Style(fg = Some(Color.Gray)))),
        Spans.from(Span.styled("  email*      - starts with email", Style(fg = Some(Color.Gray)))),
        Spans.from(Span.styled("  *email*     - contains email", Style(fg = Some(Color.Gray))))
      )
    } else {
      val patterns = pattern.split(",").map(_.trim).filter(_.nonEmpty).toList
      val matchingCols = findMatchingColumns(patterns, tuiState)

      val headerLines = List(
        Spans.from(Span.styled("Live Preview", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
        Spans.nostyle(""),
        Spans.from(
          Span.styled("Pattern: ", Style(fg = Some(Color.Cyan))),
          Span.styled(patterns.mkString(", "), Style(fg = Some(Color.White)))
        ),
        Spans.nostyle("")
      )

      if (matchingCols.isEmpty) {
        headerLines ++ List(
          Spans.from(Span.styled("No columns match this pattern.", Style(fg = Some(Color.DarkGray)))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Try a different pattern like:", Style(fg = Some(Color.DarkGray)))),
          Spans.from(Span.styled("  *name*, *_id, first*", Style(fg = Some(Color.Gray))))
        )
      } else {
        val countLine = List(
          Spans.from(
            Span.styled("Matches: ", Style(fg = Some(Color.Green))),
            Span.styled(s"${matchingCols.size} columns", Style(fg = Some(Color.White)))
          )
        )

        val columnLines = matchingCols.take(12).map { case (source, table, col, dbType) =>
          val typeName = dbType.getClass.getSimpleName.stripSuffix("$")
          Spans.from(
            Span.styled(s"  $source.$table.", Style(fg = Some(Color.Gray))),
            Span.styled(col, Style(fg = Some(Color.Green))),
            Span.styled(s" ($typeName)", Style(fg = Some(Color.DarkGray)))
          )
        }

        val moreLines = if (matchingCols.size > 12) {
          List(Spans.from(Span.styled(s"  ... and ${matchingCols.size - 12} more", Style(fg = Some(Color.DarkGray)))))
        } else Nil

        headerLines ++ countLine ++ columnLines ++ moreLines
      }
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Live Preview", Style(fg = Some(Color.Cyan))))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def findMatchingColumns(patterns: List[String], state: TuiState): List[(String, String, String, typr.db.Type)] = {
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
