package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.CompositeWizardState
import typr.cli.tui.CompositeWizardStep
import typr.cli.tui.FieldEditState
import typr.cli.tui.AlignedSourceEditState
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object CompositeWizard {

  def handleKey(state: TuiState, wizard: AppScreen.CompositeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state

    if (wizardState.addingField) {
      handleAddFieldKey(state, wizard, keyCode)
    } else if (wizardState.addingAlignedSource) {
      handleAddAlignedSourceKey(state, wizard, keyCode)
    } else if (wizardState.sourcePickerOpen) {
      handleSourcePickerKey(state, wizard, keyCode)
    } else {
      handleMainKey(state, wizard, keyCode)
    }
  }

  private def handleMainKey(state: TuiState, wizard: AppScreen.CompositeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state

    keyCode match {
      case _: KeyCode.Tab =>
        if (wizardState.canProceed) {
          val newState = wizardState.copy(step = wizardState.nextStep, error = None)
          state.copy(currentScreen = wizard.copy(state = newState))
        } else {
          val errorMsg = wizardState.step match {
            case CompositeWizardStep.EnterName    => "Name is required"
            case CompositeWizardStep.DefineFields => "At least one field is required"
            case _                                => "Cannot proceed"
          }
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some(errorMsg))))
        }

      case _: KeyCode.BackTab =>
        val newState = wizardState.copy(step = wizardState.previousStep, error = None)
        state.copy(currentScreen = wizard.copy(state = newState))

      case _: KeyCode.Up =>
        wizardState.step match {
          case CompositeWizardStep.SelectSuggestion =>
            val maxIdx = math.max(0, wizardState.suggestions.size - 1)
            val newIdx = math.max(0, wizardState.selectedSuggestionIndex - 1)
            val newOffset = if (newIdx < wizardState.suggestionScrollOffset) newIdx else wizardState.suggestionScrollOffset
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedSuggestionIndex = newIdx, suggestionScrollOffset = newOffset)))
          case CompositeWizardStep.DefineFields =>
            val newIdx = math.max(0, wizardState.selectedFieldIndex - 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedFieldIndex = newIdx)))
          case CompositeWizardStep.AddProjections =>
            val newIdx = math.max(0, wizardState.selectedAlignedSourceIndex - 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedAlignedSourceIndex = newIdx)))
          case CompositeWizardStep.AlignFields =>
            val newIdx = math.max(0, wizardState.selectedAlignmentSourceIndex - 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedAlignmentSourceIndex = newIdx)))
          case _ => state
        }

      case _: KeyCode.Down =>
        wizardState.step match {
          case CompositeWizardStep.SelectSuggestion =>
            val maxIdx = math.max(0, wizardState.suggestions.size - 1)
            val newIdx = math.min(maxIdx, wizardState.selectedSuggestionIndex + 1)
            val visibleRows = 10
            val newOffset = if (newIdx >= wizardState.suggestionScrollOffset + visibleRows) newIdx - visibleRows + 1 else wizardState.suggestionScrollOffset
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedSuggestionIndex = newIdx, suggestionScrollOffset = newOffset)))
          case CompositeWizardStep.DefineFields =>
            val maxIdx = math.max(0, wizardState.fields.size - 1)
            val newIdx = math.min(maxIdx, wizardState.selectedFieldIndex + 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedFieldIndex = newIdx)))
          case CompositeWizardStep.AddProjections =>
            val maxIdx = math.max(0, wizardState.alignedSources.size - 1)
            val newIdx = math.min(maxIdx, wizardState.selectedAlignedSourceIndex + 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedAlignedSourceIndex = newIdx)))
          case CompositeWizardStep.AlignFields =>
            val maxIdx = math.max(0, wizardState.alignedSources.size - 1)
            val newIdx = math.min(maxIdx, wizardState.selectedAlignmentSourceIndex + 1)
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(selectedAlignmentSourceIndex = newIdx)))
          case _ => state
        }

      case _: KeyCode.Enter =>
        wizardState.step match {
          case CompositeWizardStep.SelectSuggestion =>
            wizardState.currentSuggestion match {
              case Some(suggestion) =>
                val fields = suggestion.fields.map { f =>
                  FieldEditState(
                    name = f.name,
                    typeName = f.typeName,
                    nullable = f.nullable,
                    array = false,
                    description = "",
                    editing = false
                  )
                }
                val alignedSources = suggestion.projections.map { proj =>
                  AlignedSourceEditState(
                    sourceName = proj.sourceName,
                    entityPath = proj.entityPath,
                    mode = typr.bridge.CompatibilityMode.Superset,
                    mappings = Map.empty,
                    exclude = Set.empty,
                    includeExtra = Nil,
                    readonly = false,
                    editing = false,
                    entityPickerOpen = false,
                    entityPickerIndex = 0
                  )
                }
                state.copy(currentScreen =
                  wizard.copy(state =
                    wizardState.copy(
                      step = CompositeWizardStep.EnterName,
                      name = suggestion.name,
                      fields = fields,
                      alignedSources = alignedSources,
                      error = None
                    )
                  )
                )
              case None =>
                state.copy(currentScreen = wizard.copy(state = wizardState.copy(step = CompositeWizardStep.EnterName, error = None)))
            }
          case CompositeWizardStep.EnterName =>
            if (wizardState.name.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("Name is required"))))
            } else {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(step = CompositeWizardStep.DefineFields, error = None)))
            }

          case CompositeWizardStep.DefineFields =>
            if (wizardState.fields.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("At least one field is required"))))
            } else {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(step = CompositeWizardStep.AddProjections, error = None)))
            }

          case CompositeWizardStep.AddProjections =>
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(step = CompositeWizardStep.AlignFields, error = None)))

          case CompositeWizardStep.AlignFields =>
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(step = CompositeWizardStep.Review, error = None)))

          case CompositeWizardStep.Review =>
            saveCompositeType(state, wizard)
        }

      case c: KeyCode.Char if c.c() == 'a' =>
        wizardState.step match {
          case CompositeWizardStep.DefineFields =>
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(addingField = true, error = None)))
          case CompositeWizardStep.AddProjections =>
            val sources = getAvailableSources(state)
            if (sources.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("No sources available"))))
            } else {
              state.copy(currentScreen = wizard.copy(state = wizardState.copy(sourcePickerOpen = true, sourcePickerIndex = 0, error = None)))
            }
          case _ => state
        }

      case c: KeyCode.Char if c.c() == 'd' =>
        wizardState.step match {
          case CompositeWizardStep.DefineFields if wizardState.fields.nonEmpty =>
            val newFields = wizardState.fields.zipWithIndex.filterNot(_._2 == wizardState.selectedFieldIndex).map(_._1)
            val newIdx = math.min(wizardState.selectedFieldIndex, math.max(0, newFields.size - 1))
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields, selectedFieldIndex = newIdx)))
          case CompositeWizardStep.AddProjections if wizardState.alignedSources.nonEmpty =>
            val newSources = wizardState.alignedSources.zipWithIndex.filterNot(_._2 == wizardState.selectedAlignedSourceIndex).map(_._1)
            val newIdx = math.min(wizardState.selectedAlignedSourceIndex, math.max(0, newSources.size - 1))
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources, selectedAlignedSourceIndex = newIdx)))
          case _ => state
        }

      case _: KeyCode.Backspace =>
        wizardState.step match {
          case CompositeWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(name = wizardState.name.dropRight(1))))
          case _ => state
        }

      case c: KeyCode.Char =>
        wizardState.step match {
          case CompositeWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = wizardState.copy(name = wizardState.name + c.c())))
          case _ => state
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case _ => state
    }
  }

  private def handleAddFieldKey(state: TuiState, wizard: AppScreen.CompositeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state
    val fields = wizardState.fields
    val currentField = fields.lastOption.getOrElse(FieldEditState.empty)

    keyCode match {
      case _: KeyCode.Esc =>
        val newFields = if (currentField.name.isEmpty) fields.dropRight(1) else fields
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields, addingField = false)))

      case _: KeyCode.Enter =>
        if (currentField.name.isEmpty) {
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("Field name is required"))))
        } else {
          val updatedField = currentField.copy(editing = false)
          val newFields = fields.dropRight(1) :+ updatedField
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields, addingField = false, error = None)))
        }

      case _: KeyCode.Tab =>
        state // Could cycle through field options

      case _: KeyCode.Backspace =>
        val updatedField = currentField.copy(name = currentField.name.dropRight(1))
        val newFields = fields.dropRight(1) :+ updatedField
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields)))

      case c: KeyCode.Char =>
        val ch = c.c()
        if (ch == '?') {
          val updatedField = currentField.copy(nullable = !currentField.nullable)
          val newFields = fields.dropRight(1) :+ updatedField
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields)))
        } else if (ch == '[') {
          val updatedField = currentField.copy(array = !currentField.array)
          val newFields = fields.dropRight(1) :+ updatedField
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields)))
        } else if (ch == ':') {
          // Switch to type input mode (simplified - we just toggle type)
          val types = List("String", "Int", "Long", "Double", "Boolean", "UUID", "Instant")
          val currentIdx = types.indexOf(currentField.typeName)
          val nextIdx = (currentIdx + 1) % types.length
          val updatedField = currentField.copy(typeName = types(nextIdx))
          val newFields = fields.dropRight(1) :+ updatedField
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields)))
        } else {
          val updatedField = currentField.copy(name = currentField.name + ch)
          val newFields = fields.dropRight(1) :+ updatedField
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(fields = newFields)))
        }

      case _ => state
    }
  }

  private def handleAddAlignedSourceKey(state: TuiState, wizard: AppScreen.CompositeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state
    val alignedSources = wizardState.alignedSources
    val currentSource = alignedSources.lastOption.getOrElse(AlignedSourceEditState.empty(""))

    keyCode match {
      case _: KeyCode.Esc =>
        val newSources = if (currentSource.entityPath.isEmpty) alignedSources.dropRight(1) else alignedSources
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources, addingAlignedSource = false)))

      case _: KeyCode.Enter =>
        if (currentSource.entityPath.isEmpty) {
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("Entity path is required"))))
        } else {
          val updatedSource = currentSource.copy(editing = false)
          val newSources = alignedSources.dropRight(1) :+ updatedSource
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources, addingAlignedSource = false, error = None)))
        }

      case _: KeyCode.Tab =>
        // Cycle through alignment modes
        val modes = List(
          typr.bridge.CompatibilityMode.Superset,
          typr.bridge.CompatibilityMode.Exact,
          typr.bridge.CompatibilityMode.Subset
        )
        val currentIdx = modes.indexOf(currentSource.mode)
        val nextIdx = (currentIdx + 1) % modes.length
        val updatedSource = currentSource.copy(mode = modes(nextIdx))
        val newSources = alignedSources.dropRight(1) :+ updatedSource
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources)))

      case _: KeyCode.Backspace =>
        val updatedSource = currentSource.copy(entityPath = currentSource.entityPath.dropRight(1))
        val newSources = alignedSources.dropRight(1) :+ updatedSource
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources)))

      case c: KeyCode.Char =>
        val updatedSource = currentSource.copy(entityPath = currentSource.entityPath + c.c())
        val newSources = alignedSources.dropRight(1) :+ updatedSource
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(alignedSources = newSources)))

      case _ => state
    }
  }

  private def handleSourcePickerKey(state: TuiState, wizard: AppScreen.CompositeWizard, keyCode: KeyCode): TuiState = {
    val wizardState = wizard.state
    val sources = getAvailableSources(state)

    keyCode match {
      case _: KeyCode.Esc =>
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(sourcePickerOpen = false)))

      case _: KeyCode.Up =>
        val newIdx = math.max(0, wizardState.sourcePickerIndex - 1)
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(sourcePickerIndex = newIdx)))

      case _: KeyCode.Down =>
        val maxIdx = math.max(0, sources.size - 1)
        val newIdx = math.min(maxIdx, wizardState.sourcePickerIndex + 1)
        state.copy(currentScreen = wizard.copy(state = wizardState.copy(sourcePickerIndex = newIdx)))

      case _: KeyCode.Enter =>
        if (sources.nonEmpty && wizardState.sourcePickerIndex < sources.size) {
          val selectedSource = sources(wizardState.sourcePickerIndex)
          val newSource = AlignedSourceEditState.empty(selectedSource.value)
          val newSources = wizardState.alignedSources :+ newSource
          state.copy(currentScreen =
            wizard.copy(state =
              wizardState.copy(
                alignedSources = newSources,
                sourcePickerOpen = false,
                addingAlignedSource = true
              )
            )
          )
        } else {
          state.copy(currentScreen = wizard.copy(state = wizardState.copy(sourcePickerOpen = false)))
        }

      case _ => state
    }
  }

  private def saveCompositeType(state: TuiState, wizard: AppScreen.CompositeWizard): TuiState = {
    val wizardState = wizard.state
    val name = wizardState.name

    if (name.isEmpty) {
      return state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("Name is required"))))
    }

    val types = state.config.types.getOrElse(Map.empty)
    if (types.contains(name)) {
      return state.copy(currentScreen = wizard.copy(state = wizardState.copy(error = Some("Type name already exists"))))
    }

    val domainType = typr.config.generated.DomainType(
      description = if (wizardState.description.isEmpty) None else Some(wizardState.description),
      fields = wizardState.fields.map { f =>
        f.name -> typr.config.generated.FieldSpecObject(
          array = Some(f.array),
          default = None,
          description = if (f.description.isEmpty) None else Some(f.description),
          nullable = Some(f.nullable),
          `type` = f.typeName
        )
      }.toMap,
      generate = Some(
        typr.config.generated.DomainGenerateOptions(
          builder = None,
          canonical = None,
          copy = Some(true),
          domainType = Some(wizardState.generateDomainType),
          interface = Some(wizardState.generateInterface),
          mappers = Some(wizardState.generateMappers)
        )
      ),
      alignedSources =
        if (wizardState.alignedSources.isEmpty) None
        else
          Some(wizardState.alignedSources.map { a =>
            a.key -> typr.config.generated.AlignedSource(
              entity = Some(a.entityPath),
              exclude = if (a.exclude.isEmpty) None else Some(a.exclude.toList),
              include_extra = if (a.includeExtra.isEmpty) None else Some(a.includeExtra),
              mappings = if (a.mappings.isEmpty) None else Some(a.mappings),
              mode = Some(a.mode match {
                case typr.bridge.CompatibilityMode.Exact    => "exact"
                case typr.bridge.CompatibilityMode.Superset => "superset"
                case typr.bridge.CompatibilityMode.Subset   => "subset"
              }),
              readonly = if (a.readonly) Some(true) else None
            )
          }.toMap),
      primary = None,
      projections = None
    )

    val newTypes = types + (name -> domainType)
    val newConfig = state.config.copy(types = Some(newTypes))
    val updatedState = state.copy(
      config = newConfig,
      currentScreen = AppScreen.TypeList(selectedIndex = 0, typeKindFilter = None),
      hasUnsavedChanges = true,
      statusMessage = Some((s"Domain type '$name' created", Color.Green))
    )
    Navigator.goBack(updatedState)
  }

  private def getAvailableSources(state: TuiState): List[SourceName] = {
    state.sourceStatuses
      .collect { case (name, _: SourceStatus.Ready) =>
        name
      }
      .toList
      .sortBy(_.value)
  }

  def render(f: Frame, state: TuiState, wizard: AppScreen.CompositeWizard): Unit = {
    val wizardState = wizard.state

    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(60), Constraint.Percentage(40))
    ).split(f.size)

    renderMainPanel(f, chunks(0), wizardState, state)
    renderSidebar(f, chunks(1), wizardState)
  }

  private def renderMainPanel(f: Frame, area: Rect, wizardState: CompositeWizardState, tuiState: TuiState): Unit = {
    val lines: List[Spans] = wizardState.step match {
      case CompositeWizardStep.SelectSuggestion => renderSelectSuggestionStep(wizardState, area.height.toInt)
      case CompositeWizardStep.EnterName        => renderEnterNameStep(wizardState)
      case CompositeWizardStep.DefineFields     => renderDefineFieldsStep(wizardState)
      case CompositeWizardStep.AddProjections =>
        renderAddProjectionsStep(wizardState, tuiState)
      case CompositeWizardStep.AlignFields => renderAlignFieldsStep(wizardState)
      case CompositeWizardStep.Review      => renderReviewStep(wizardState)
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Create Domain Type", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def renderSelectSuggestionStep(wizardState: CompositeWizardState, areaHeight: Int): List[Spans] = {
    val header = List(
      Spans.from(Span.styled("Select from Database Tables", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Choose a table to create a domain type from,")),
      Spans.from(Span.nostyle("or press Tab to start from scratch.")),
      Spans.nostyle("")
    )

    val suggestions = wizardState.suggestions
    val visibleRows = math.max(1, areaHeight - 10)
    val startIdx = wizardState.suggestionScrollOffset
    val endIdx = math.min(suggestions.size, startIdx + visibleRows)
    val visibleSuggestions = suggestions.slice(startIdx, endIdx)

    val suggestionLines = if (suggestions.isEmpty) {
      List(
        Spans.from(Span.styled("No suggestions available.", Style(fg = Some(Color.DarkGray)))),
        Spans.from(Span.styled("Press Tab to create from scratch.", Style(fg = Some(Color.DarkGray))))
      )
    } else {
      val scrollIndicator = if (suggestions.size > visibleRows) {
        val above = startIdx
        val below = suggestions.size - endIdx
        val indicator =
          if (above > 0 && below > 0) s"($above more above, $below more below)"
          else if (above > 0) s"($above more above)"
          else if (below > 0) s"($below more below)"
          else ""
        if (indicator.nonEmpty) List(Spans.from(Span.styled(indicator, Style(fg = Some(Color.DarkGray)))))
        else Nil
      } else Nil

      visibleSuggestions.zipWithIndex.flatMap { case (suggestion, relIdx) =>
        val idx = startIdx + relIdx
        val isSelected = idx == wizardState.selectedSuggestionIndex
        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.White))
        val sourceCountStyle =
          if (suggestion.sourceCount > 1) Style(fg = Some(Color.Green), addModifier = Modifier.BOLD)
          else Style(fg = Some(Color.DarkGray))
        val reasonStyle = Style(fg = Some(Color.DarkGray))

        val sourceLabel = if (suggestion.sourceCount > 1) s"[${suggestion.sourceCount} sources]" else s"[1 source]"

        List(
          Spans.from(
            Span.styled(prefix, style),
            Span.styled(suggestion.name, style),
            Span.styled(s" $sourceLabel", sourceCountStyle)
          )
        ) ++ (if (isSelected) {
                val projectionSummary = suggestion.projections.map(p => s"${p.sourceName}:${p.entityPath}").mkString(", ")
                List(
                  Spans.from(
                    Span.styled("     ", Style.DEFAULT),
                    Span.styled(s"${suggestion.fields.size} common fields - ${suggestion.reason}", reasonStyle)
                  ),
                  Spans.from(
                    Span.styled("     → ", Style(fg = Some(Color.Cyan))),
                    Span.styled(projectionSummary, Style(fg = Some(Color.Cyan)))
                  )
                )
              } else Nil)
      } ++ scrollIndicator
    }

    val help = List(
      Spans.nostyle(""),
      Spans.from(Span.styled("[Up/Down] Navigate  [Enter] Select  [Tab] Skip to manual", Style(fg = Some(Color.DarkGray))))
    )

    header ++ suggestionLines ++ renderError(wizardState.error) ++ help
  }

  private def renderEnterNameStep(wizardState: CompositeWizardState): List[Spans] = {
    List(
      Spans.from(Span.styled("Step 1: Enter Type Name", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Enter a name for your domain type.")),
      Spans.from(Span.nostyle("This will be the canonical type that spans sources.")),
      Spans.nostyle(""),
      Spans.from(
        Span.styled("Name: ", Style(fg = Some(Color.Cyan))),
        Span.styled(wizardState.name + "█", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
      ),
      Spans.nostyle(""),
      Spans.from(Span.styled("Examples: Customer, Order, ProductInfo", Style(fg = Some(Color.DarkGray))))
    ) ++ renderError(wizardState.error)
  }

  private def renderDefineFieldsStep(wizardState: CompositeWizardState): List[Spans] = {
    val header = List(
      Spans.from(Span.styled("Step 2: Define Fields", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Define the canonical fields for this type.")),
      Spans.nostyle("")
    )

    val fieldLines = if (wizardState.fields.isEmpty) {
      List(
        Spans.from(Span.styled("No fields defined yet.", Style(fg = Some(Color.DarkGray)))),
        Spans.nostyle("")
      )
    } else {
      wizardState.fields.zipWithIndex.flatMap { case (field, idx) =>
        val isSelected = idx == wizardState.selectedFieldIndex
        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.White))
        List(
          Spans.from(
            Span.styled(prefix, style),
            Span.styled(field.name, style),
            Span.styled(s": ${field.compactType}", Style(fg = Some(Color.Cyan)))
          )
        )
      } :+ Spans.nostyle("")
    }

    val addingLine = if (wizardState.addingField) {
      val currentField = wizardState.fields.lastOption.getOrElse(FieldEditState.empty)
      List(
        Spans.from(Span.styled("Adding field:", Style(fg = Some(Color.Green)))),
        Spans.from(
          Span.styled("  Name: ", Style(fg = Some(Color.Cyan))),
          Span.styled(currentField.name + "█", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
        ),
        Spans.from(
          Span.styled("  Type: ", Style(fg = Some(Color.Cyan))),
          Span.styled(currentField.compactType, Style(fg = Some(Color.White))),
          Span.styled(" (press : to cycle)", Style(fg = Some(Color.DarkGray)))
        ),
        Spans.from(Span.styled("  Press ? to toggle nullable, [ to toggle array", Style(fg = Some(Color.DarkGray))))
      )
    } else Nil

    val help = List(
      Spans.from(Span.styled("[a] Add field  [d] Delete  [Tab] Next step", Style(fg = Some(Color.DarkGray))))
    )

    header ++ fieldLines ++ addingLine ++ renderError(wizardState.error) ++ help
  }

  private def renderAddProjectionsStep(wizardState: CompositeWizardState, tuiState: TuiState): List[Spans] = {
    val header = List(
      Spans.from(Span.styled("Step 3: Add Aligned Sources", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Map this domain type to entities in your data sources.")),
      Spans.from(Span.nostyle("Each alignment connects your canonical type to a table or API.")),
      Spans.nostyle("")
    )

    val sourceLines = if (wizardState.alignedSources.isEmpty) {
      List(
        Spans.from(Span.styled("No aligned sources defined yet.", Style(fg = Some(Color.DarkGray)))),
        Spans.from(Span.styled("(You can add them later)", Style(fg = Some(Color.DarkGray)))),
        Spans.nostyle("")
      )
    } else {
      wizardState.alignedSources.zipWithIndex.flatMap { case (src, idx) =>
        val isSelected = idx == wizardState.selectedAlignedSourceIndex
        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.White))
        List(
          Spans.from(
            Span.styled(prefix, style),
            Span.styled(src.sourceName, Style(fg = Some(Color.Cyan))),
            Span.styled(":", style),
            Span.styled(src.entityPath, style)
          )
        )
      } :+ Spans.nostyle("")
    }

    val pickerLines = if (wizardState.sourcePickerOpen) {
      val sources = getAvailableSources(tuiState)
      List(
        Spans.from(Span.styled("Select source:", Style(fg = Some(Color.Green))))
      ) ++ sources.zipWithIndex.map { case (source, idx) =>
        val isSelected = idx == wizardState.sourcePickerIndex
        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.Cyan))
        Spans.from(Span.styled(s"$prefix${source.value}", style))
      }
    } else if (wizardState.addingAlignedSource) {
      val currentSrc = wizardState.alignedSources.lastOption.getOrElse(AlignedSourceEditState.empty(""))
      val availableEntities = typr.cli.tui.util.ProjectionFieldExtractor.getAvailableEntities(tuiState, SourceName(currentSrc.sourceName))
      val matchingEntities = if (currentSrc.entityPath.isEmpty) {
        availableEntities.take(5)
      } else {
        availableEntities.filter(_.toLowerCase.contains(currentSrc.entityPath.toLowerCase)).take(5)
      }
      val suggestionLines = if (matchingEntities.nonEmpty) {
        Spans.from(Span.styled("  Matching entities:", Style(fg = Some(Color.DarkGray)))) ::
          matchingEntities.map(e => Spans.from(Span.styled(s"    $e", Style(fg = Some(Color.Cyan)))))
      } else if (currentSrc.entityPath.nonEmpty) {
        List(Spans.from(Span.styled("  No matching entities found", Style(fg = Some(Color.DarkGray)))))
      } else Nil

      val modeStr = currentSrc.mode match {
        case typr.bridge.CompatibilityMode.Superset => "superset"
        case typr.bridge.CompatibilityMode.Exact    => "exact"
        case typr.bridge.CompatibilityMode.Subset   => "subset"
      }
      List(
        Spans.from(Span.styled(s"Adding aligned source for ${currentSrc.sourceName}:", Style(fg = Some(Color.Green)))),
        Spans.from(
          Span.styled("  Entity: ", Style(fg = Some(Color.Cyan))),
          Span.styled(currentSrc.entityPath + "█", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
        ),
        Spans.from(
          Span.styled("  Mode:   ", Style(fg = Some(Color.Cyan))),
          Span.styled(modeStr, Style(fg = Some(Color.Yellow))),
          Span.styled(" (Tab to cycle)", Style(fg = Some(Color.DarkGray)))
        ),
        Spans.from(Span.styled("  Type entity path, Enter to confirm", Style(fg = Some(Color.DarkGray))))
      ) ++ suggestionLines
    } else Nil

    val help = List(
      Spans.from(Span.styled("[a] Add aligned source  [d] Delete  [Tab] Next step", Style(fg = Some(Color.DarkGray))))
    )

    header ++ sourceLines ++ pickerLines ++ renderError(wizardState.error) ++ help
  }

  private def renderAlignFieldsStep(wizardState: CompositeWizardState): List[Spans] = {
    val header = List(
      Spans.from(Span.styled("Step 4: Review Alignment", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Verify field alignment for each source.")),
      Spans.nostyle("")
    )

    if (wizardState.alignedSources.isEmpty) {
      header ++ List(
        Spans.from(Span.styled("No aligned sources to align.", Style(fg = Some(Color.DarkGray)))),
        Spans.from(Span.styled("Press Tab to continue.", Style(fg = Some(Color.DarkGray))))
      )
    } else {
      val sourceList = wizardState.alignedSources.zipWithIndex.flatMap { case (src, idx) =>
        val isSelected = idx == wizardState.selectedAlignmentSourceIndex
        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue)) else Style(fg = Some(Color.White))

        val alignmentState = wizardState.alignments.getOrElse(src.key, typr.cli.tui.FieldAlignmentState.initial(src.key))
        val statusColor = alignmentState.validationResult match {
          case typr.bridge.AlignmentStatus.Compatible      => Color.Green
          case typr.bridge.AlignmentStatus.Warning(_)      => Color.Yellow
          case typr.bridge.AlignmentStatus.Incompatible(_) => Color.Red
          case typr.bridge.AlignmentStatus.NotValidated    => Color.Gray
        }
        val statusIcon = alignmentState.validationResult match {
          case typr.bridge.AlignmentStatus.Compatible      => "✓"
          case typr.bridge.AlignmentStatus.Warning(_)      => "!"
          case typr.bridge.AlignmentStatus.Incompatible(_) => "✗"
          case typr.bridge.AlignmentStatus.NotValidated    => "○"
        }

        List(
          Spans.from(
            Span.styled(prefix, style),
            Span.styled(statusIcon, Style(fg = Some(statusColor))),
            Span.styled(s" ${src.sourceName}:${src.entityPath}", style)
          )
        )
      }

      val detailLines = wizardState.currentAlignmentSource match {
        case Some(src) =>
          val alignmentState = wizardState.alignments.getOrElse(src.key, typr.cli.tui.FieldAlignmentState.initial(src.key))
          val modeExplanation = src.mode match {
            case typr.bridge.CompatibilityMode.Superset =>
              "Source can have extra fields (e.g., audit columns)"
            case typr.bridge.CompatibilityMode.Subset =>
              "Source can have fewer fields (e.g., summary views)"
            case typr.bridge.CompatibilityMode.Exact =>
              "Source must match exactly (strict validation)"
          }
          List(
            Spans.nostyle(""),
            Spans.from(Span.styled("Alignment details:", Style(fg = Some(Color.Cyan)))),
            Spans.from(
              Span.styled(s"  Mode: ", Style(fg = Some(Color.White))),
              Span.styled(s"${src.mode}", Style(fg = Some(Color.Yellow)))
            ),
            Spans.from(Span.styled(s"        $modeExplanation", Style(fg = Some(Color.DarkGray))))
          ) ++ (alignmentState.validationResult match {
            case typr.bridge.AlignmentStatus.Warning(issues)      => issues.take(2).map(msg => Spans.from(Span.styled(s"  Warning: $msg", Style(fg = Some(Color.Yellow)))))
            case typr.bridge.AlignmentStatus.Incompatible(errors) => errors.take(2).map(msg => Spans.from(Span.styled(s"  Error: $msg", Style(fg = Some(Color.Red)))))
            case _                                                => Nil
          })
        case None => Nil
      }

      val modeHelp = List(
        Spans.nostyle(""),
        Spans.from(Span.styled("Alignment Modes:", Style(fg = Some(Color.Cyan)))),
        Spans.from(
          Span.styled("  superset", Style(fg = Some(Color.Yellow))),
          Span.styled(" - source has extra fields (default)", Style(fg = Some(Color.DarkGray)))
        ),
        Spans.from(
          Span.styled("  exact", Style(fg = Some(Color.Yellow))),
          Span.styled("    - fields must match exactly", Style(fg = Some(Color.DarkGray)))
        ),
        Spans.from(
          Span.styled("  subset", Style(fg = Some(Color.Yellow))),
          Span.styled("   - source has fewer fields", Style(fg = Some(Color.DarkGray)))
        )
      )

      val help = List(
        Spans.nostyle(""),
        Spans.from(Span.styled("[Up/Down] Select  [Tab] Next step", Style(fg = Some(Color.DarkGray))))
      )

      header ++ sourceList ++ detailLines ++ modeHelp ++ help
    }
  }

  private def renderReviewStep(wizardState: CompositeWizardState): List[Spans] = {
    val ready = wizardState.name.nonEmpty && wizardState.fields.nonEmpty

    List(
      Spans.from(Span.styled("Step 5: Review & Save", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      if (ready) Spans.from(Span.styled("Ready to create domain type!", Style(fg = Some(Color.Green))))
      else Spans.from(Span.styled("Missing required information", Style(fg = Some(Color.Red)))),
      Spans.nostyle(""),
      Spans.from(
        Span.styled("Name: ", Style(fg = Some(Color.Cyan))),
        Span.styled(wizardState.name, Style(fg = Some(Color.White)))
      ),
      Spans.nostyle(""),
      Spans.from(Span.styled("Fields:", Style(fg = Some(Color.Cyan))))
    ) ++ wizardState.fields.map { f =>
      Spans.from(Span.nostyle(s"  ${f.name}: ${f.compactType}"))
    } ++ List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Aligned Sources:", Style(fg = Some(Color.Cyan))))
    ) ++ (if (wizardState.alignedSources.isEmpty) {
            List(Spans.from(Span.styled("  (none)", Style(fg = Some(Color.DarkGray)))))
          } else {
            wizardState.alignedSources.map { a =>
              val modeStr = a.mode match {
                case typr.bridge.CompatibilityMode.Exact    => "exact"
                case typr.bridge.CompatibilityMode.Superset => "superset"
                case typr.bridge.CompatibilityMode.Subset   => "subset"
              }
              Spans.from(
                Span.nostyle(s"  ${a.sourceName}:${a.entityPath} "),
                Span.styled(s"($modeStr)", Style(fg = Some(Color.DarkGray)))
              )
            }
          }) ++ List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Press Enter to create, Esc to cancel", Style(fg = Some(Color.DarkGray))))
    ) ++ renderError(wizardState.error)
  }

  private def renderError(error: Option[String]): List[Spans] = error match {
    case Some(msg) =>
      List(
        Spans.nostyle(""),
        Spans.from(Span.styled(s"Error: $msg", Style(fg = Some(Color.Red))))
      )
    case None => Nil
  }

  private def renderSidebar(f: Frame, area: Rect, wizardState: CompositeWizardState): Unit = {
    val stepIndicators = List(
      ("Select", wizardState.step == CompositeWizardStep.SelectSuggestion),
      ("1. Name", wizardState.step == CompositeWizardStep.EnterName),
      ("2. Fields", wizardState.step == CompositeWizardStep.DefineFields),
      ("3. Sources", wizardState.step == CompositeWizardStep.AddProjections),
      ("4. Align", wizardState.step == CompositeWizardStep.AlignFields),
      ("5. Review", wizardState.step == CompositeWizardStep.Review)
    )

    val currentStepNum = wizardState.step match {
      case CompositeWizardStep.SelectSuggestion => 0
      case CompositeWizardStep.EnterName        => 1
      case CompositeWizardStep.DefineFields     => 2
      case CompositeWizardStep.AddProjections   => 3
      case CompositeWizardStep.AlignFields      => 4
      case CompositeWizardStep.Review           => 5
    }

    val lines = List(
      Spans.from(Span.styled("Progress", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle("")
    ) ++ stepIndicators.zipWithIndex.map { case ((label, isCurrent), idx) =>
      val isCompleted = idx < currentStepNum
      val (icon, color) =
        if (isCurrent) ("◉", Color.Cyan)
        else if (isCompleted) ("✓", Color.Green)
        else ("○", Color.DarkGray)
      Spans.from(Span.styled(s"$icon $label", Style(fg = Some(color))))
    } ++ List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Navigation", Style(fg = Some(Color.Yellow)))),
      Spans.from(Span.styled("[Tab] Next step", Style(fg = Some(Color.DarkGray)))),
      Spans.from(Span.styled("[Shift+Tab] Previous", Style(fg = Some(Color.DarkGray)))),
      Spans.from(Span.styled("[Esc] Cancel", Style(fg = Some(Color.DarkGray))))
    )

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
}
