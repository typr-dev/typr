package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.MetaDbStatus
import typr.cli.tui.OutputEditorField
import typr.cli.tui.OutputEditorState
import typr.cli.tui.RelationInfo
import typr.cli.tui.RelationMatcher
import typr.cli.tui.RelationMatcherMode
import typr.cli.tui.RelationType
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.MetaDbFetcher

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

object OutputEditor {
  private val fetchers = new ConcurrentHashMap[String, MetaDbFetcher]()

  def handleKey(state: TuiState, editor: AppScreen.OutputEditor, keyCode: KeyCode): TuiState = {
    val es = editor.state

    // If sources popup is open, handle sources popup keys
    if (es.sourcesPopupOpen) {
      handleSourcesPopupKey(state, editor, es, keyCode)
    } else {
      // If matcher popup is open, handle popup keys
      es.matcherPopupField match {
        case Some(popupField) =>
          handlePopupKey(state, editor, es, popupField, keyCode)
        case None =>
          handleEditorKey(state, editor, es, keyCode)
      }
    }
  }

  private def handleSourcesPopupKey(
      state: TuiState,
      editor: AppScreen.OutputEditor,
      es: OutputEditorState,
      keyCode: KeyCode
  ): TuiState = {
    val availableSources = state.config.sources.getOrElse(Map.empty).keys.toList.sorted
    val totalItems = availableSources.size + 1 // +1 for "All sources" option

    keyCode match {
      case _: KeyCode.Esc =>
        // Cancel: restore original selection
        val restoredState = es.sourcesPopupOriginal match {
          case Some((origSources, origAllMode)) =>
            es.copy(
              selectedSources = origSources,
              allSourcesMode = origAllMode,
              sourcesPopupOpen = false,
              sourcesPopupOriginal = None
            )
          case None =>
            es.copy(sourcesPopupOpen = false, sourcesPopupOriginal = None)
        }
        state.copy(currentScreen = editor.copy(state = restoredState))

      case _: KeyCode.Enter =>
        // Accept: close popup and keep changes
        val newState = es.copy(sourcesPopupOpen = false, sourcesPopupOriginal = None, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Up =>
        val newIndex = math.max(0, es.sourcesPopupCursorIndex - 1)
        val newState = es.copy(sourcesPopupCursorIndex = newIndex)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Down =>
        val newIndex = math.min(totalItems - 1, es.sourcesPopupCursorIndex + 1)
        val newState = es.copy(sourcesPopupCursorIndex = newIndex)
        state.copy(currentScreen = editor.copy(state = newState))

      case c: KeyCode.Char if c.c() == ' ' =>
        val cursorIdx = es.sourcesPopupCursorIndex
        if (cursorIdx == 0) {
          // Toggle "All sources" mode
          val newState = es.copy(
            allSourcesMode = !es.allSourcesMode,
            selectedSources = if (!es.allSourcesMode) Set.empty else es.selectedSources
          )
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          // Toggle specific source
          val sourceIdx = cursorIdx - 1
          availableSources.lift(sourceIdx) match {
            case Some(sourceName) =>
              val newSelected =
                if (es.selectedSources.contains(sourceName)) es.selectedSources - sourceName
                else es.selectedSources + sourceName
              val newState = es.copy(
                selectedSources = newSelected,
                allSourcesMode = false // Selecting specific sources disables "all" mode
              )
              state.copy(currentScreen = editor.copy(state = newState))
            case None => state
          }
        }

      case _ => state
    }
  }

  private def handlePopupKey(
      state: TuiState,
      editor: AppScreen.OutputEditor,
      es: OutputEditorState,
      popupField: OutputEditorField,
      keyCode: KeyCode
  ): TuiState = {
    val matcher = getMatcherForField(es, popupField)
    val allRelations = getAvailableRelations(es)
    val schemas = allRelations.flatMap(_.schema).distinct.sorted
    val relationsInView = getRelationsForPicker(matcher, allRelations)

    keyCode match {
      case _: KeyCode.Esc =>
        // Cancel: restore original matcher and close popup
        val restoredState = es.matcherPopupOriginal match {
          case Some(original) => restoreMatcherForField(es, popupField, original)
          case None           => es
        }
        val newState = restoredState.copy(matcherPopupField = None, matcherPopupOriginal = None)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Enter =>
        // Accept: close popup and keep changes
        val newState = es.copy(matcherPopupField = None, matcherPopupOriginal = None)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Left =>
        val newState = updateMatcherForField(es, popupField, _.prevMode)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Right =>
        val newState = updateMatcherForField(es, popupField, _.nextMode)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Tab =>
        if (matcher.mode == RelationMatcherMode.Custom) {
          val newState = updateMatcherForField(es, popupField, m => m.copy(pickerState = m.pickerState.togglePanel))
          state.copy(currentScreen = editor.copy(state = newState))
        } else state

      case _: KeyCode.Up =>
        if (matcher.mode == RelationMatcherMode.Custom) {
          val newState = updateMatcherForField(
            es,
            popupField,
            m =>
              if (m.pickerState.inSchemaPanel) m.copy(pickerState = m.pickerState.prevSchema)
              else m.copy(pickerState = m.pickerState.prevRelation)
          )
          state.copy(currentScreen = editor.copy(state = newState))
        } else state

      case _: KeyCode.Down =>
        if (matcher.mode == RelationMatcherMode.Custom) {
          val newState = updateMatcherForField(
            es,
            popupField,
            m =>
              if (m.pickerState.inSchemaPanel) m.copy(pickerState = m.pickerState.nextSchema(schemas.size))
              else m.copy(pickerState = m.pickerState.nextRelation(relationsInView.size))
          )
          state.copy(currentScreen = editor.copy(state = newState))
        } else state

      case c: KeyCode.Char if c.c() == ' ' =>
        if (matcher.mode == RelationMatcherMode.Custom) {
          val newState = updateMatcherForField(
            es,
            popupField,
            m =>
              if (m.pickerState.inSchemaPanel) {
                schemas.lift(m.pickerState.schemaIndex).map(m.toggleSchema).getOrElse(m)
              } else {
                relationsInView.lift(m.pickerState.relationIndex).map(rel => m.toggleRelation(rel.fullName)).getOrElse(m)
              }
          )
          state.copy(currentScreen = editor.copy(state = newState))
        } else state

      case c: KeyCode.Char if c.c() == 'x' || c.c() == 'X' =>
        if (matcher.mode == RelationMatcherMode.Custom && matcher.pickerState.inRelationPanel) {
          val newState = updateMatcherForField(es, popupField, m => relationsInView.lift(m.pickerState.relationIndex).map(rel => m.toggleExclude(rel.fullName)).getOrElse(m))
          state.copy(currentScreen = editor.copy(state = newState))
        } else state

      case _ => state
    }
  }

  private def restoreMatcherForField(
      es: OutputEditorState,
      field: OutputEditorField,
      original: RelationMatcher
  ): OutputEditorState = field match {
    case OutputEditorField.PrimaryKeyTypes => es.copy(primaryKeyTypes = original)
    case OutputEditorField.MockRepos       => es.copy(mockRepos = original)
    case OutputEditorField.TestInserts     => es.copy(testInserts = original)
    case OutputEditorField.FieldValues     => es.copy(fieldValues = original)
    case OutputEditorField.Readonly        => es.copy(readonly = original)
    case _                                 => es
  }

  private def getAvailableRelations(es: OutputEditorState): List[RelationInfo] = {
    es.metaDbStatus.values
      .flatMap {
        case MetaDbStatus.Loaded(relations, _, _) => relations
        case _                                    => Nil
      }
      .toList
      .distinct
      .sortBy(r => (r.schema.getOrElse(""), r.name))
  }

  private def getRelationsForPicker(matcher: RelationMatcher, allRelations: List[RelationInfo]): List[RelationInfo] = {
    if (matcher.selectedSchemas.isEmpty) {
      allRelations
    } else {
      allRelations.filter(r => r.schema.exists(matcher.selectedSchemas.contains) || matcher.selectedRelations.contains(r.fullName))
    }
  }

  private def handleEditorKey(
      state: TuiState,
      editor: AppScreen.OutputEditor,
      es: OutputEditorState,
      keyCode: KeyCode
  ): TuiState = {
    keyCode match {
      case _: KeyCode.Esc =>
        fetchers.clear()
        Navigator.goBack(state)

      case _: KeyCode.Tab | _: KeyCode.Down =>
        val newField = es.nextField
        val showPreview = isMatcherField(newField)
        val newState = es.copy(
          selectedField = newField,
          matcherPreviewField = if (showPreview) Some(newField) else None
        )
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.BackTab | _: KeyCode.Up =>
        val newField = es.prevField
        val showPreview = isMatcherField(newField)
        val newState = es.copy(
          selectedField = newField,
          matcherPreviewField = if (showPreview) Some(newField) else None
        )
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Enter =>
        es.selectedField match {
          case OutputEditorField.Language =>
            val newLang = es.languageSelect.selectedValue
            val newState = es.copy(language = Some(newLang), modified = true)
            state.copy(currentScreen = editor.copy(state = newState))

          case OutputEditorField.DbLib =>
            val newDbLib = es.dbLibSelect.selectedValue
            val newState = es.copy(dbLib = Some(newDbLib), modified = true)
            state.copy(currentScreen = editor.copy(state = newState))

          case OutputEditorField.JsonLib =>
            val newJsonLib = es.jsonLibSelect.selectedValue
            val newState = es.copy(jsonLib = Some(newJsonLib), modified = true)
            state.copy(currentScreen = editor.copy(state = newState))

          case OutputEditorField.ScalaDialect =>
            val newDialect = es.dialectSelect.selectedValue
            val newState = es.copy(scalaDialect = Some(newDialect), modified = true)
            state.copy(currentScreen = editor.copy(state = newState))

          case OutputEditorField.ScalaDsl =>
            val newDsl = es.dslSelect.selectedValue
            val newState = es.copy(scalaDsl = Some(newDsl), modified = true)
            state.copy(currentScreen = editor.copy(state = newState))

          case f if isMatcherField(f) =>
            // Open matcher popup - store original for cancel
            val original = getMatcherForField(es, f)
            val newState = es.copy(matcherPopupField = Some(f), matcherPopupOriginal = Some(original))
            state.copy(currentScreen = editor.copy(state = newState))

          case OutputEditorField.Sources =>
            // Open sources popup - store original for cancel
            val newState = es.copy(
              sourcesPopupOpen = true,
              sourcesPopupCursorIndex = 0,
              sourcesPopupOriginal = Some((es.selectedSources, es.allSourcesMode))
            )
            state.copy(currentScreen = editor.copy(state = newState))

          case _ =>
            saveOutput(state, editor)
        }

      case _: KeyCode.Left =>
        handleSelectLeft(state, editor, es)

      case _: KeyCode.Right =>
        handleSelectRight(state, editor, es)

      case c: KeyCode.Char =>
        val newState = es.selectedField match {
          case OutputEditorField.Path    => es.copy(path = es.path + c.c(), modified = true)
          case OutputEditorField.Package => es.copy(pkg = es.pkg + c.c(), modified = true)
          case _                         => es
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Backspace =>
        val newState = es.selectedField match {
          case OutputEditorField.Path    => es.copy(path = es.path.dropRight(1), modified = true)
          case OutputEditorField.Package => es.copy(pkg = es.pkg.dropRight(1), modified = true)
          case _                         => es
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case _ => state
    }
  }

  private def getMatcherForField(es: OutputEditorState, field: OutputEditorField): RelationMatcher = field match {
    case OutputEditorField.PrimaryKeyTypes => es.primaryKeyTypes
    case OutputEditorField.MockRepos       => es.mockRepos
    case OutputEditorField.TestInserts     => es.testInserts
    case OutputEditorField.FieldValues     => es.fieldValues
    case OutputEditorField.Readonly        => es.readonly
    case _                                 => RelationMatcher.all
  }

  private def updateMatcherForField(
      es: OutputEditorState,
      field: OutputEditorField,
      f: RelationMatcher => RelationMatcher
  ): OutputEditorState = field match {
    case OutputEditorField.PrimaryKeyTypes => es.copy(primaryKeyTypes = f(es.primaryKeyTypes), modified = true)
    case OutputEditorField.MockRepos       => es.copy(mockRepos = f(es.mockRepos), modified = true)
    case OutputEditorField.TestInserts     => es.copy(testInserts = f(es.testInserts), modified = true)
    case OutputEditorField.FieldValues     => es.copy(fieldValues = f(es.fieldValues), modified = true)
    case OutputEditorField.Readonly        => es.copy(readonly = f(es.readonly), modified = true)
    case _                                 => es
  }

  private def handleSelectLeft(state: TuiState, editor: AppScreen.OutputEditor, es: OutputEditorState): TuiState = {
    es.selectedField match {
      case OutputEditorField.Language =>
        val newSelect = es.languageSelect.prev
        val newState = es.copy(languageSelect = newSelect, language = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.DbLib =>
        val newSelect = es.dbLibSelect.prev
        val newState = es.copy(dbLibSelect = newSelect, dbLib = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.JsonLib =>
        val newSelect = es.jsonLibSelect.prev
        val newState = es.copy(jsonLibSelect = newSelect, jsonLib = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.ScalaDialect =>
        val newSelect = es.dialectSelect.prev
        val newState = es.copy(dialectSelect = newSelect, scalaDialect = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.ScalaDsl =>
        val newSelect = es.dslSelect.prev
        val newState = es.copy(dslSelect = newSelect, scalaDsl = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.PrimaryKeyTypes =>
        val newState = es.copy(primaryKeyTypes = es.primaryKeyTypes.prevMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.MockRepos =>
        val newState = es.copy(mockRepos = es.mockRepos.prevMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.TestInserts =>
        val newState = es.copy(testInserts = es.testInserts.prevMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.FieldValues =>
        val newState = es.copy(fieldValues = es.fieldValues.prevMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.Readonly =>
        val newState = es.copy(readonly = es.readonly.prevMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case _ => state
    }
  }

  private def handleSelectRight(state: TuiState, editor: AppScreen.OutputEditor, es: OutputEditorState): TuiState = {
    es.selectedField match {
      case OutputEditorField.Language =>
        val newSelect = es.languageSelect.next
        val newState = es.copy(languageSelect = newSelect, language = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.DbLib =>
        val newSelect = es.dbLibSelect.next
        val newState = es.copy(dbLibSelect = newSelect, dbLib = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.JsonLib =>
        val newSelect = es.jsonLibSelect.next
        val newState = es.copy(jsonLibSelect = newSelect, jsonLib = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.ScalaDialect =>
        val newSelect = es.dialectSelect.next
        val newState = es.copy(dialectSelect = newSelect, scalaDialect = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.ScalaDsl =>
        val newSelect = es.dslSelect.next
        val newState = es.copy(dslSelect = newSelect, scalaDsl = Some(newSelect.selectedValue), modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.PrimaryKeyTypes =>
        val newState = es.copy(primaryKeyTypes = es.primaryKeyTypes.nextMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.MockRepos =>
        val newState = es.copy(mockRepos = es.mockRepos.nextMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.TestInserts =>
        val newState = es.copy(testInserts = es.testInserts.nextMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.FieldValues =>
        val newState = es.copy(fieldValues = es.fieldValues.nextMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case OutputEditorField.Readonly =>
        val newState = es.copy(readonly = es.readonly.nextMode, modified = true)
        state.copy(currentScreen = editor.copy(state = newState))
      case _ => state
    }
  }

  private def getCurrentMatcher(es: OutputEditorState): Option[RelationMatcher] = es.selectedField match {
    case OutputEditorField.PrimaryKeyTypes => Some(es.primaryKeyTypes)
    case OutputEditorField.MockRepos       => Some(es.mockRepos)
    case OutputEditorField.TestInserts     => Some(es.testInserts)
    case OutputEditorField.FieldValues     => Some(es.fieldValues)
    case OutputEditorField.Readonly        => Some(es.readonly)
    case _                                 => None
  }

  private def updateCurrentMatcher(es: OutputEditorState, f: RelationMatcher => RelationMatcher): OutputEditorState = {
    es.selectedField match {
      case OutputEditorField.PrimaryKeyTypes => es.copy(primaryKeyTypes = f(es.primaryKeyTypes), modified = true)
      case OutputEditorField.MockRepos       => es.copy(mockRepos = f(es.mockRepos), modified = true)
      case OutputEditorField.TestInserts     => es.copy(testInserts = f(es.testInserts), modified = true)
      case OutputEditorField.FieldValues     => es.copy(fieldValues = f(es.fieldValues), modified = true)
      case OutputEditorField.Readonly        => es.copy(readonly = f(es.readonly), modified = true)
      case _                                 => es
    }
  }

  private def isMatcherField(field: OutputEditorField): Boolean = field match {
    case OutputEditorField.PrimaryKeyTypes | OutputEditorField.MockRepos | OutputEditorField.TestInserts | OutputEditorField.FieldValues | OutputEditorField.Readonly =>
      true
    case _ => false
  }

  def tick(state: TuiState, editor: AppScreen.OutputEditor): TuiState = {
    val es = editor.state
    val sources = state.config.sources.getOrElse(Map.empty)

    val matchedSourceNames =
      if (es.allSourcesMode) sources.keys.toList
      else es.selectedSources.toList.filter(sources.contains)

    var newMetaDbStatus = es.metaDbStatus
    var changed = false

    matchedSourceNames.foreach { sourceName =>
      if (!es.metaDbStatus.contains(sourceName) && !fetchers.containsKey(sourceName)) {
        sources.get(sourceName).foreach { json =>
          val fetcher = new MetaDbFetcher()
          fetchers.put(sourceName, fetcher)
          fetcher.fetchSource(sourceName, json)
          newMetaDbStatus = newMetaDbStatus + (sourceName -> MetaDbStatus.Fetching)
          changed = true
        }
      }
    }

    fetchers.asScala.foreach { case (name, fetcher) =>
      val result = fetcher.getResult
      if (es.metaDbStatus.get(name) != Some(result)) {
        newMetaDbStatus = newMetaDbStatus + (name -> result)
        changed = true
        result match {
          case MetaDbStatus.Loaded(_, _, _) | MetaDbStatus.Failed(_) =>
            fetchers.remove(name)
          case _ =>
        }
      }
    }

    if (changed) {
      val newState = es.copy(metaDbStatus = newMetaDbStatus)
      state.copy(currentScreen = editor.copy(state = newState))
    } else {
      state
    }
  }

  private def saveOutput(state: TuiState, editor: AppScreen.OutputEditor): TuiState = {
    val es = editor.state
    val outputName = editor.outputName
    val newOutput = es.toOutput

    val newOutputs = state.config.outputs.getOrElse(Map.empty) + (outputName -> newOutput)
    val newConfig = state.config.copy(outputs = Some(newOutputs))

    fetchers.clear()
    val updatedState = state.copy(
      config = newConfig,
      hasUnsavedChanges = true,
      currentScreen = AppScreen.OutputList(selectedIndex = 1),
      statusMessage = Some((s"Updated output: $outputName", Color.Green))
    )
    Navigator.goBack(updatedState)
  }

  def render(f: Frame, state: TuiState, editor: AppScreen.OutputEditor): Unit = {
    val es = editor.state
    val showPreview = es.matcherPreviewField.isDefined

    val mainChunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Length(3),
        Constraint.Min(10),
        Constraint.Length(3)
      )
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, mainChunks(0), s"Edit Output: ${editor.outputName}", isBackHovered)

    renderTitle(f, mainChunks(1), editor)

    if (showPreview) {
      val contentChunks = Layout(
        direction = Direction.Horizontal,
        constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
      ).split(mainChunks(2))
      renderFields(f, contentChunks(0), es)
      renderMatcherPreview(f, contentChunks(1), state, es)
    } else {
      renderFields(f, mainChunks(2), es)
    }

    val hint = if (isMatcherField(es.selectedField)) {
      "[Tab/Down] Next  [Up] Prev  [Enter] Edit matcher  [Esc] Cancel"
    } else {
      "[Tab/Down] Next  [Up] Prev  [</>] Change selection  [Enter] Save  [Esc] Cancel"
    }
    val hintPara = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(hintPara, mainChunks(3))

    // Render popup overlay if matcher popup is open
    es.matcherPopupField.foreach { popupField =>
      renderMatcherPopup(f, state, es, popupField)
    }

    // Render popup overlay if sources popup is open
    if (es.sourcesPopupOpen) {
      renderSourcesPopup(f, state, es)
    }
  }

  private def renderTitle(f: Frame, area: Rect, editor: AppScreen.OutputEditor): Unit = {
    val es = editor.state

    val titleBlock = BlockWidget(
      title = Some(
        Spans.from(Span.styled(s"Edit Output: ${editor.outputName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))
      ),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(titleBlock, area)

    val modifiedIndicator = if (es.modified) " [modified]" else ""
    val titleText = Spans.from(
      Span.styled(es.language.getOrElse("java"), Style(fg = Some(Color.Yellow))),
      Span.styled(s" -> ${es.path}", Style(fg = Some(Color.Gray))),
      Span.styled(modifiedIndicator, Style(fg = Some(Color.Red)))
    )
    f.buffer.setSpans(area.x + 2, area.y + 1, titleText, area.width.toInt - 4)
  }

  private def renderFields(f: Frame, area: Rect, es: OutputEditorState): Unit = {
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
        field match {
          case OutputEditorField.PrimaryKeyTypes =>
            renderMatcherField(buf, innerArea, y, "PK Types", es.primaryKeyTypes, isSelected)
          case OutputEditorField.MockRepos =>
            renderMatcherField(buf, innerArea, y, "Mock Repos", es.mockRepos, isSelected)
          case OutputEditorField.TestInserts =>
            renderMatcherField(buf, innerArea, y, "Test Inserts", es.testInserts, isSelected)
          case OutputEditorField.FieldValues =>
            renderMatcherField(buf, innerArea, y, "Field Values", es.fieldValues, isSelected)
          case OutputEditorField.Readonly =>
            renderMatcherField(buf, innerArea, y, "Readonly", es.readonly, isSelected)
          case OutputEditorField.Sources =>
            renderSourcesField(buf, innerArea, y, es, isSelected)

          case _ =>
            val (label, value, isSelect, isTextInput) = field match {
              case OutputEditorField.Path         => ("Path", es.path, false, true)
              case OutputEditorField.Package      => ("Package", es.pkg, false, true)
              case OutputEditorField.Language     => ("Language", es.languageSelect.selectedLabel, true, false)
              case OutputEditorField.DbLib        => ("DB Library", es.dbLibSelect.selectedLabel, true, false)
              case OutputEditorField.JsonLib      => ("JSON Library", es.jsonLibSelect.selectedLabel, true, false)
              case OutputEditorField.ScalaDialect => ("Scala Dialect", es.dialectSelect.selectedLabel, true, false)
              case OutputEditorField.ScalaDsl     => ("Scala DSL", es.dslSelect.selectedLabel, true, false)
              case _                              => ("", "", false, false)
            }

            val labelStyle =
              if (isSelected) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))
            val valueStyle =
              if (isSelected) Style(fg = Some(Color.White), addModifier = Modifier.BOLD) else Style(fg = Some(Color.White))
            val cursor = if (isSelected && isTextInput) "_" else ""
            val selectHint = if (isSelect && isSelected) " [</>]" else ""

            val line = Spans.from(
              Span.styled(f"$label%-14s", labelStyle),
              Span.styled(": ", Style(fg = Some(Color.DarkGray))),
              Span.styled(value + cursor, valueStyle),
              Span.styled(selectHint, Style(fg = Some(Color.DarkGray)))
            )
            buf.setSpans(innerArea.x, y, line, innerArea.width.toInt)
        }
      }
    }
  }

  private def renderSourcesField(buf: Buffer, area: Rect, y: Int, es: OutputEditorState, isSelected: Boolean): Unit = {
    val label = "Sources"
    val labelStyle =
      if (isSelected) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))

    // Build summary text
    val summaryText =
      if (es.allSourcesMode) "All"
      else if (es.selectedSources.isEmpty) "(none selected)"
      else if (es.selectedSources.size <= 2) es.selectedSources.toList.sorted.mkString(", ")
      else s"${es.selectedSources.size} selected"

    val summaryColor = if (es.allSourcesMode) Color.Green else if (es.selectedSources.isEmpty) Color.Yellow else Color.White
    val summaryStyle =
      if (isSelected) Style(fg = Some(summaryColor), addModifier = Modifier.BOLD)
      else Style(fg = Some(summaryColor))

    val selectHint = if (isSelected) " [Enter]" else ""

    val line = Spans.from(
      Span.styled(f"$label%-14s", labelStyle),
      Span.styled(": ", Style(fg = Some(Color.DarkGray))),
      Span.styled(s"[$summaryText]", summaryStyle),
      Span.styled(selectHint, Style(fg = Some(Color.DarkGray)))
    )
    buf.setSpans(area.x, y, line, area.width.toInt)
  }

  private def renderMatcherField(buf: Buffer, area: Rect, y: Int, label: String, matcher: RelationMatcher, isSelected: Boolean): Unit = {
    val labelStyle =
      if (isSelected) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))

    val modeLabel = RelationMatcherMode.label(matcher.mode)
    val modeColor = matcher.mode match {
      case RelationMatcherMode.All    => Color.Green
      case RelationMatcherMode.None   => Color.Red
      case RelationMatcherMode.Custom => Color.Yellow
    }

    val modeStyle =
      if (isSelected) Style(fg = Some(modeColor), addModifier = Modifier.BOLD) else Style(fg = Some(modeColor))

    val patternDisplay = matcher.mode match {
      case RelationMatcherMode.All  => ""
      case RelationMatcherMode.None => ""
      case RelationMatcherMode.Custom =>
        " " + matcher.summaryString
    }

    val selectHint = if (isSelected) " [</>]" else ""
    val cursor = if (isSelected && matcher.mode == RelationMatcherMode.Custom) "_" else ""

    val line = Spans.from(
      Span.styled(f"$label%-14s", labelStyle),
      Span.styled(": ", Style(fg = Some(Color.DarkGray))),
      Span.styled(s"[$modeLabel]", modeStyle),
      Span.styled(patternDisplay + cursor, Style(fg = Some(Color.White))),
      Span.styled(selectHint, Style(fg = Some(Color.DarkGray)))
    )
    buf.setSpans(area.x, y, line, area.width.toInt)
  }

  private def renderMatcherPreview(f: Frame, area: Rect, state: TuiState, es: OutputEditorState): Unit = {
    val previewField = es.matcherPreviewField.getOrElse(OutputEditorField.PrimaryKeyTypes)
    val matcher = previewField match {
      case OutputEditorField.PrimaryKeyTypes => es.primaryKeyTypes
      case OutputEditorField.MockRepos       => es.mockRepos
      case OutputEditorField.TestInserts     => es.testInserts
      case OutputEditorField.FieldValues     => es.fieldValues
      case OutputEditorField.Readonly        => es.readonly
      case _                                 => RelationMatcher.all
    }

    val fieldLabel = previewField match {
      case OutputEditorField.PrimaryKeyTypes => "Primary Key Types"
      case OutputEditorField.MockRepos       => "Mock Repos"
      case OutputEditorField.TestInserts     => "Test Inserts"
      case OutputEditorField.FieldValues     => "Field Values"
      case OutputEditorField.Readonly        => "Readonly"
      case _                                 => "Matcher"
    }

    val allRelations = es.metaDbStatus.values
      .flatMap {
        case MetaDbStatus.Loaded(relations, _, _) => relations
        case _                                    => Nil
      }
      .toList
      .distinct
      .sortBy(r => (r.schema.getOrElse(""), r.name))

    val availableSchemas = allRelations.flatMap(_.schema).distinct.sorted

    val anyFetching = es.metaDbStatus.values.exists(_ == MetaDbStatus.Fetching)
    val anyFailed = es.metaDbStatus.values.exists(_.isInstanceOf[MetaDbStatus.Failed])

    val (matchedRelations, statusMsg) =
      if (allRelations.isEmpty && anyFetching) {
        (Nil, Some("Loading metadata..."))
      } else if (allRelations.isEmpty && anyFailed) {
        (Nil, Some("Failed to load metadata"))
      } else if (allRelations.isEmpty) {
        (Nil, Some("No sources matched"))
      } else {
        matcher.mode match {
          case RelationMatcherMode.None =>
            (Nil, None)
          case RelationMatcherMode.All =>
            (allRelations, None)
          case RelationMatcherMode.Custom =>
            val matched = OutputEditorState.matchPattern(matcher, allRelations)
            (matched, None)
        }
      }

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"$fieldLabel Preview", Style(fg = Some(Color.Magenta))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(area.x + 2, area.y + 1, area.width - 4, area.height - 2)
    val buf = f.buffer

    var y = innerArea.y

    val modeText = RelationMatcherMode.label(matcher.mode)
    val modeColor = matcher.mode match {
      case RelationMatcherMode.All    => Color.Green
      case RelationMatcherMode.None   => Color.Red
      case RelationMatcherMode.Custom => Color.Yellow
    }
    val modeLine = Spans.from(
      Span.styled("Mode: ", Style(fg = Some(Color.Gray))),
      Span.styled(modeText, Style(fg = Some(modeColor), addModifier = Modifier.BOLD)),
      Span.styled("  [</> to change]", Style(fg = Some(Color.DarkGray)))
    )
    buf.setSpans(innerArea.x, y, modeLine, innerArea.width.toInt)
    y += 1

    if (matcher.mode == RelationMatcherMode.Custom) {
      y += 1

      // Show selection summary
      if (matcher.selectedSchemas.nonEmpty) {
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Schemas: ", Style(fg = Some(Color.Green))),
            Span.styled(matcher.selectedSchemas.toList.sorted.take(3).mkString(", "), Style(fg = Some(Color.White))),
            Span.styled(if (matcher.selectedSchemas.size > 3) s" +${matcher.selectedSchemas.size - 3}" else "", Style(fg = Some(Color.DarkGray)))
          ),
          innerArea.width.toInt
        )
        y += 1
      }

      if (matcher.selectedRelations.nonEmpty) {
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Tables: ", Style(fg = Some(Color.Green))),
            Span.styled(matcher.selectedRelations.toList.sorted.take(3).mkString(", "), Style(fg = Some(Color.White))),
            Span.styled(if (matcher.selectedRelations.size > 3) s" +${matcher.selectedRelations.size - 3}" else "", Style(fg = Some(Color.DarkGray)))
          ),
          innerArea.width.toInt
        )
        y += 1
      }

      if (matcher.excludedRelations.nonEmpty) {
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Excluded: ", Style(fg = Some(Color.Red))),
            Span.styled(matcher.excludedRelations.toList.sorted.take(3).mkString(", "), Style(fg = Some(Color.White))),
            Span.styled(if (matcher.excludedRelations.size > 3) s" +${matcher.excludedRelations.size - 3}" else "", Style(fg = Some(Color.DarkGray)))
          ),
          innerArea.width.toInt
        )
        y += 1
      }

      if (matcher.selectedSchemas.isEmpty && matcher.selectedRelations.isEmpty) {
        buf.setString(innerArea.x, y, "(all tables - press Enter to customize)", Style(fg = Some(Color.DarkGray)))
        y += 1
      }

      y += 1
      if (availableSchemas.nonEmpty) {
        val schemasStr = availableSchemas.take(5).mkString(", ") + (if (availableSchemas.size > 5) "..." else "")
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Available schemas: ", Style(fg = Some(Color.Cyan))),
            Span.styled(schemasStr, Style(fg = Some(Color.White)))
          ),
          innerArea.width.toInt
        )
        y += 1
      }
    }

    y += 1
    val tables = matchedRelations.count(_.relationType == RelationType.Table)
    val views = matchedRelations.count(_.relationType == RelationType.View)
    val total = allRelations.size

    val summaryLine = statusMsg match {
      case Some(msg) =>
        Spans.from(Span.styled(msg, Style(fg = Some(Color.Yellow))))
      case None =>
        Spans.from(
          Span.styled(s"Result: ", Style(fg = Some(Color.Gray))),
          Span.styled(s"$tables tables, $views views", Style(fg = Some(Color.Green))),
          Span.styled(s" / $total total", Style(fg = Some(Color.DarkGray)))
        )
    }
    buf.setSpans(innerArea.x, y, summaryLine, innerArea.width.toInt)
    y += 1

    if (matchedRelations.nonEmpty && y < innerArea.y + innerArea.height.toInt - 1) {
      val remaining = innerArea.y + innerArea.height.toInt - y - 1
      matchedRelations.take(remaining).foreach { rel =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val typeIcon = rel.relationType match {
            case RelationType.Table => "T"
            case RelationType.View  => "V"
          }
          val typeColor = rel.relationType match {
            case RelationType.Table => Color.Blue
            case RelationType.View  => Color.Magenta
          }
          val line = Spans.from(
            Span.styled(s"  [$typeIcon] ", Style(fg = Some(typeColor))),
            Span.styled(rel.fullName, Style(fg = Some(Color.White)))
          )
          buf.setSpans(innerArea.x, y, line, innerArea.width.toInt)
          y += 1
        }
      }

      if (matchedRelations.size > remaining) {
        val more = matchedRelations.size - remaining
        buf.setString(innerArea.x, y, s"  ... and $more more", Style(fg = Some(Color.DarkGray)))
      }
    } else if (matcher.mode == RelationMatcherMode.None) {
      buf.setString(innerArea.x, y, "Feature disabled for all relations", Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderMatcherPopup(f: Frame, state: TuiState, es: OutputEditorState, popupField: OutputEditorField): Unit = {
    val matcher = getMatcherForField(es, popupField)

    val fieldLabel = popupField match {
      case OutputEditorField.PrimaryKeyTypes => "Primary Key Types"
      case OutputEditorField.MockRepos       => "Mock Repos"
      case OutputEditorField.TestInserts     => "Test Inserts"
      case OutputEditorField.FieldValues     => "Field Values"
      case OutputEditorField.Readonly        => "Readonly"
      case _                                 => "Matcher"
    }

    val fieldDescription = popupField match {
      case OutputEditorField.PrimaryKeyTypes => "Generate type-safe ID wrapper types"
      case OutputEditorField.MockRepos       => "Generate mock repository implementations"
      case OutputEditorField.TestInserts     => "Generate test data insertion helpers"
      case OutputEditorField.FieldValues     => "Generate field value constants"
      case OutputEditorField.Readonly        => "Generate read-only repositories"
      case _                                 => ""
    }

    // Calculate popup size (centered, 60x20)
    val popupWidth = math.min(60, f.size.width.toInt - 4)
    val popupHeight = math.min(22, f.size.height.toInt - 4)
    val popupX = (f.size.width.toInt - popupWidth) / 2
    val popupY = (f.size.height.toInt - popupHeight) / 2

    val popupArea = Rect(popupX, popupY, popupWidth, popupHeight)
    val buf = f.buffer

    // Clear popup area
    for (py <- popupY until popupY + popupHeight) {
      for (px <- popupX until popupX + popupWidth) {
        buf.get(px, py).setSymbol(" ").setStyle(Style(bg = Some(Color.Black)))
      }
    }

    // Draw popup border
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Edit: $fieldLabel", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Double,
      style = Style(bg = Some(Color.Black))
    )
    f.renderWidget(block, popupArea)

    val innerArea = Rect(popupX + 2, popupY + 1, popupWidth - 4, popupHeight - 2)
    var y = innerArea.y

    // Description
    buf.setString(innerArea.x, y, fieldDescription, Style(fg = Some(Color.Gray)))
    y += 2

    // Mode selector
    val modes = RelationMatcherMode.modes
    val modeStrings = modes.map { m =>
      val label = RelationMatcherMode.label(m)
      if (m == matcher.mode) s"[$label]" else s" $label "
    }
    val modeColor = matcher.mode match {
      case RelationMatcherMode.All    => Color.Green
      case RelationMatcherMode.None   => Color.Red
      case RelationMatcherMode.Custom => Color.Yellow
    }
    val modeLine = Spans.from(
      Span.styled("Mode: ", Style(fg = Some(Color.Gray))),
      Span.styled("<", Style(fg = Some(Color.DarkGray))),
      Span.styled(" ", Style.DEFAULT),
      Span.styled(modeStrings.mkString("  "), Style(fg = Some(modeColor), addModifier = Modifier.BOLD)),
      Span.styled(" ", Style.DEFAULT),
      Span.styled(">", Style(fg = Some(Color.DarkGray)))
    )
    buf.setSpans(innerArea.x, y, modeLine, innerArea.width.toInt)
    y += 2

    val allRelations = getAvailableRelations(es)
    val schemas = allRelations.flatMap(_.schema).distinct.sorted
    val relationsInView = getRelationsForPicker(matcher, allRelations)

    matcher.mode match {
      case RelationMatcherMode.All =>
        buf.setString(innerArea.x, y, "Feature enabled for ALL tables and views.", Style(fg = Some(Color.Green)))
        y += 2
        buf.setString(innerArea.x, y, "Use Custom mode to select specific tables.", Style(fg = Some(Color.DarkGray)))

      case RelationMatcherMode.None =>
        buf.setString(innerArea.x, y, "Feature DISABLED for all tables and views.", Style(fg = Some(Color.Red)))
        y += 2
        buf.setString(innerArea.x, y, "Use All or Custom mode to enable.", Style(fg = Some(Color.DarkGray)))

      case RelationMatcherMode.Custom =>
        val picker = matcher.pickerState
        val inSchemas = picker.inSchemaPanel
        val listHeight = popupHeight - 10

        // Schema list (left side)
        val schemaX = innerArea.x
        val schemaWidth = 20
        val schemaHeaderStyle = if (inSchemas) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))
        buf.setString(schemaX, y, "Schemas:", schemaHeaderStyle)

        if (schemas.isEmpty) {
          buf.setString(schemaX, y + 1, "(no schemas)", Style(fg = Some(Color.DarkGray)))
        } else {
          schemas.zipWithIndex.take(listHeight).foreach { case (schema, idx) =>
            val isCursor = inSchemas && idx == picker.schemaIndex
            val isSelected = matcher.isSchemaSelected(schema)
            val checkbox = if (isSelected) "[x]" else "[ ]"
            val prefix = if (isCursor) "> " else "  "
            val style =
              if (isCursor) Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
              else if (isSelected) Style(fg = Some(Color.Green))
              else Style(fg = Some(Color.White))
            val line = s"$prefix$checkbox $schema"
            buf.setString(schemaX, y + 1 + idx, line.take(schemaWidth), style)
          }
        }

        // Relation list (right side)
        val relX = innerArea.x + schemaWidth + 2
        val relWidth = innerArea.width.toInt - schemaWidth - 2
        val relHeaderStyle = if (!inSchemas) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Gray))
        buf.setString(relX, y, "Tables/Views:", relHeaderStyle)

        if (relationsInView.isEmpty) {
          val hint = if (schemas.nonEmpty && matcher.selectedSchemas.isEmpty) "(select a schema first)" else "(no relations)"
          buf.setString(relX, y + 1, hint, Style(fg = Some(Color.DarkGray)))
        } else {
          relationsInView.zipWithIndex.take(listHeight).foreach { case (rel, idx) =>
            val isCursor = !inSchemas && idx == picker.relationIndex
            val isIncluded = matcher.isRelationIncluded(rel.schema, rel.fullName)
            val isExcluded = matcher.isRelationExcluded(rel.fullName)
            val checkbox =
              if (isExcluded) "[!]"
              else if (isIncluded) "[x]"
              else "[ ]"
            val prefix = if (isCursor) "> " else "  "
            val typeIcon = rel.relationType match {
              case RelationType.Table => "T"
              case RelationType.View  => "V"
            }
            val style =
              if (isCursor) Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
              else if (isExcluded) Style(fg = Some(Color.Red))
              else if (isIncluded) Style(fg = Some(Color.Green))
              else Style(fg = Some(Color.White))
            val line = s"$prefix$checkbox [$typeIcon] ${rel.name}"
            buf.setString(relX, y + 1 + idx, line.take(relWidth), style)
          }
          if (relationsInView.size > listHeight) {
            buf.setString(relX, y + 1 + listHeight, s"  ... +${relationsInView.size - listHeight} more", Style(fg = Some(Color.DarkGray)))
          }
        }

        // Summary at bottom
        val summaryY = y + listHeight + 2
        val summary = matcher.summaryString
        buf.setSpans(
          innerArea.x,
          summaryY,
          Spans.from(
            Span.styled("Selection: ", Style(fg = Some(Color.Gray))),
            Span.styled(summary, Style(fg = Some(Color.Yellow)))
          ),
          innerArea.width.toInt
        )
    }

    // Footer hints
    val hintY = popupY + popupHeight - 2
    val hintText = matcher.mode match {
      case RelationMatcherMode.Custom =>
        "[</>] Mode  [Tab] Panel  [Space] Toggle  [x] Exclude  [Enter] Accept  [Esc] Cancel"
      case _ =>
        "[</>] Mode  [Enter] Accept  [Esc] Cancel"
    }
    buf.setString(popupX + 2, hintY, hintText, Style(fg = Some(Color.DarkGray)))
  }

  private def renderSourcesPopup(f: Frame, state: TuiState, es: OutputEditorState): Unit = {
    val availableSources = state.config.sources.getOrElse(Map.empty).keys.toList.sorted
    val totalItems = availableSources.size + 1 // +1 for "All sources" option

    // Calculate popup size (centered)
    val popupWidth = math.min(50, f.size.width.toInt - 4)
    val popupHeight = math.min(math.max(10, totalItems + 7), f.size.height.toInt - 4)
    val popupX = (f.size.width.toInt - popupWidth) / 2
    val popupY = (f.size.height.toInt - popupHeight) / 2

    val popupArea = Rect(popupX, popupY, popupWidth, popupHeight)
    val buf = f.buffer

    // Clear popup area
    for (py <- popupY until popupY + popupHeight) {
      for (px <- popupX until popupX + popupWidth) {
        buf.get(px, py).setSymbol(" ").setStyle(Style(bg = Some(Color.Black)))
      }
    }

    // Draw popup border
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Select Sources", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Double,
      style = Style(bg = Some(Color.Black))
    )
    f.renderWidget(block, popupArea)

    val innerArea = Rect(popupX + 2, popupY + 1, popupWidth - 4, popupHeight - 2)
    var y = innerArea.y

    // Description
    buf.setString(innerArea.x, y, "Select which sources to generate code from", Style(fg = Some(Color.Gray)))
    y += 2

    // "All sources" option
    val allCursor = es.sourcesPopupCursorIndex == 0
    val allCheckbox = if (es.allSourcesMode) "[x]" else "[ ]"
    val allPrefix = if (allCursor) "> " else "  "
    val allStyle =
      if (allCursor) Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
      else if (es.allSourcesMode) Style(fg = Some(Color.Green))
      else Style(fg = Some(Color.White))
    buf.setString(innerArea.x, y, s"$allPrefix$allCheckbox All sources", allStyle)
    y += 1

    // Separator
    buf.setString(innerArea.x, y, "─" * (innerArea.width.toInt - 2), Style(fg = Some(Color.DarkGray)))
    y += 1

    // Individual sources
    val listHeight = popupHeight - 8
    availableSources.zipWithIndex.take(listHeight).foreach { case (sourceName, idx) =>
      val itemIdx = idx + 1
      val isCursor = es.sourcesPopupCursorIndex == itemIdx
      val isSelected = es.allSourcesMode || es.selectedSources.contains(sourceName)
      val checkbox = if (isSelected) "[x]" else "[ ]"
      val prefix = if (isCursor) "> " else "  "
      val style =
        if (isCursor) Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
        else if (isSelected) Style(fg = Some(Color.Green))
        else Style(fg = Some(Color.White))

      val sourceType = state.config.sources
        .flatMap(_.get(sourceName))
        .flatMap(_.hcursor.get[String]("type").toOption)
        .getOrElse("?")
      val typeIcon = sourceType match {
        case "postgres"   => "P"
        case "mariadb"    => "M"
        case "mysql"      => "M"
        case "duckdb"     => "D"
        case "oracle"     => "O"
        case "sqlserver"  => "S"
        case "openapi"    => "A"
        case "jsonschema" => "J"
        case _            => "?"
      }

      buf.setString(innerArea.x, y, s"$prefix$checkbox [$typeIcon] $sourceName".take(innerArea.width.toInt), style)
      y += 1
    }

    if (availableSources.size > listHeight) {
      buf.setString(innerArea.x, y, s"  ... +${availableSources.size - listHeight} more", Style(fg = Some(Color.DarkGray)))
    }

    // Footer hints
    val hintY = popupY + popupHeight - 2
    val hintText = "[Up/Down] Navigate  [Space] Toggle  [Enter] Accept  [Esc] Cancel"
    buf.setString(popupX + 2, hintY, hintText.take(popupWidth - 4), Style(fg = Some(Color.DarkGray)))
  }
}
