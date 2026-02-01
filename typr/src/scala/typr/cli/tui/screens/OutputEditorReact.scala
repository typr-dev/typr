package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.OutputEditorField
import typr.cli.tui.OutputEditorState
import typr.cli.tui.RelationMatcher
import typr.cli.tui.RelationMatcherMode

/** React-based OutputEditor with form components */
object OutputEditorReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      outputName: String,
      editorState: OutputEditorState
  )

  def component(props: Props): Component = {
    Component.named("OutputEditorReact") { (ctx, area) =>
      val es = props.editorState
      val sources = props.globalState.config.sources.getOrElse(Map.empty)
      val hoverPos = props.globalState.hoverPosition

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(
          Constraint.Length(3),
          Constraint.Min(10),
          Constraint.Length(2)
        )
      ).split(area)

      // Header with back button
      BackButton(s"Edit Output: ${props.outputName}")(props.callbacks.goBack()).render(ctx, chunks(0))

      // Main content area
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

      val labelWidth = 16
      var y = innerArea.y.toInt

      // Sources field (MultiSelect)
      val sourceOptions = sources.keys.toList.sorted.map(s => (s, s))
      FormMultiSelectField(
        label = "Sources",
        options = sourceOptions,
        selected = es.selectedSources,
        allSelected = es.allSourcesMode,
        hasAllOption = true,
        isFocused = es.selectedField == OutputEditorField.Sources,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.Sources)),
        onToggle = () =>
          props.callbacks.updateOutputEditor(s =>
            s.copy(
              sourcesPopupOpen = true,
              sourcesPopupCursorIndex = 0,
              sourcesPopupOriginal = Some((s.selectedSources, s.allSourcesMode))
            )
          )
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // Path field (Text)
      FormTextField(
        label = "Path",
        value = es.path,
        isFocused = es.selectedField == OutputEditorField.Path,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.Path)),
        hoverPosition = hoverPos
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // Package field (Text)
      FormTextField(
        label = "Package",
        value = es.pkg,
        isFocused = es.selectedField == OutputEditorField.Package,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.Package)),
        hoverPosition = hoverPos
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // Language field (Select)
      FormSelectField(
        label = "Language",
        options = es.languageSelect.options.toList,
        value = es.languageSelect.selectedValue,
        isFocused = es.selectedField == OutputEditorField.Language,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.Language)),
        onChange = (v: String) =>
          props.callbacks.updateOutputEditor { s =>
            val newSelect = s.languageSelect.copy(selectedIndex = s.languageSelect.options.indexWhere(_._1 == v))
            s.copy(languageSelect = newSelect, language = Some(v), modified = true)
          },
        hoverPosition = hoverPos
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // DB Library field (Select)
      FormSelectField(
        label = "DB Library",
        options = es.dbLibSelect.options.toList,
        value = es.dbLibSelect.selectedValue,
        isFocused = es.selectedField == OutputEditorField.DbLib,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.DbLib)),
        onChange = (v: String) =>
          props.callbacks.updateOutputEditor { s =>
            val newSelect = s.dbLibSelect.copy(selectedIndex = s.dbLibSelect.options.indexWhere(_._1 == v))
            s.copy(dbLibSelect = newSelect, dbLib = Some(v), modified = true)
          },
        hoverPosition = hoverPos
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // JSON Library field (Select)
      FormSelectField(
        label = "JSON Library",
        options = es.jsonLibSelect.options.toList,
        value = es.jsonLibSelect.selectedValue,
        isFocused = es.selectedField == OutputEditorField.JsonLib,
        labelWidth = labelWidth,
        onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.JsonLib)),
        onChange = (v: String) =>
          props.callbacks.updateOutputEditor { s =>
            val newSelect = s.jsonLibSelect.copy(selectedIndex = s.jsonLibSelect.options.indexWhere(_._1 == v))
            s.copy(jsonLibSelect = newSelect, jsonLib = Some(v), modified = true)
          },
        hoverPosition = hoverPos
      ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
      y += 1

      // Scala-specific fields
      if (es.isScala) {
        // Scala Dialect (Select)
        FormSelectField(
          label = "Scala Dialect",
          options = es.dialectSelect.options.toList,
          value = es.dialectSelect.selectedValue,
          isFocused = es.selectedField == OutputEditorField.ScalaDialect,
          labelWidth = labelWidth,
          onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.ScalaDialect)),
          onChange = (v: String) =>
            props.callbacks.updateOutputEditor { s =>
              val newSelect = s.dialectSelect.copy(selectedIndex = s.dialectSelect.options.indexWhere(_._1 == v))
              s.copy(dialectSelect = newSelect, scalaDialect = Some(v), modified = true)
            },
          hoverPosition = hoverPos
        ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
        y += 1

        // Scala DSL (Select)
        FormSelectField(
          label = "Scala DSL",
          options = es.dslSelect.options.toList,
          value = es.dslSelect.selectedValue,
          isFocused = es.selectedField == OutputEditorField.ScalaDsl,
          labelWidth = labelWidth,
          onFocus = () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.ScalaDsl)),
          onChange = (v: String) =>
            props.callbacks.updateOutputEditor { s =>
              val newSelect = s.dslSelect.copy(selectedIndex = s.dslSelect.options.indexWhere(_._1 == v))
              s.copy(dslSelect = newSelect, scalaDsl = Some(v), modified = true)
            },
          hoverPosition = hoverPos
        ).render(ctx, Rect(innerArea.x, y.toShort, innerArea.width, 1))
        y += 1
      }

      // Matcher fields section header
      y += 1
      ctx.buffer.setString(innerArea.x, y.toShort, "Feature Matchers", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
      y += 1

      // Matcher fields (using Select for mode)
      val matcherModes = List(
        (RelationMatcherMode.All, "All"),
        (RelationMatcherMode.None, "None"),
        (RelationMatcherMode.Custom, "Custom")
      )

      renderMatcherField(
        ctx,
        innerArea.x.toShort,
        y.toShort,
        innerArea.width.toInt,
        labelWidth,
        "PK Types",
        es.primaryKeyTypes,
        es.selectedField == OutputEditorField.PrimaryKeyTypes,
        () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.PrimaryKeyTypes)),
        (mode: RelationMatcherMode) => props.callbacks.updateOutputEditor(s => s.copy(primaryKeyTypes = s.primaryKeyTypes.copy(mode = mode), modified = true)),
        hoverPos
      )
      y += 1

      renderMatcherField(
        ctx,
        innerArea.x.toShort,
        y.toShort,
        innerArea.width.toInt,
        labelWidth,
        "Mock Repos",
        es.mockRepos,
        es.selectedField == OutputEditorField.MockRepos,
        () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.MockRepos)),
        (mode: RelationMatcherMode) => props.callbacks.updateOutputEditor(s => s.copy(mockRepos = s.mockRepos.copy(mode = mode), modified = true)),
        hoverPos
      )
      y += 1

      renderMatcherField(
        ctx,
        innerArea.x.toShort,
        y.toShort,
        innerArea.width.toInt,
        labelWidth,
        "Test Inserts",
        es.testInserts,
        es.selectedField == OutputEditorField.TestInserts,
        () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.TestInserts)),
        (mode: RelationMatcherMode) => props.callbacks.updateOutputEditor(s => s.copy(testInserts = s.testInserts.copy(mode = mode), modified = true)),
        hoverPos
      )
      y += 1

      renderMatcherField(
        ctx,
        innerArea.x.toShort,
        y.toShort,
        innerArea.width.toInt,
        labelWidth,
        "Field Values",
        es.fieldValues,
        es.selectedField == OutputEditorField.FieldValues,
        () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.FieldValues)),
        (mode: RelationMatcherMode) => props.callbacks.updateOutputEditor(s => s.copy(fieldValues = s.fieldValues.copy(mode = mode), modified = true)),
        hoverPos
      )
      y += 1

      renderMatcherField(
        ctx,
        innerArea.x.toShort,
        y.toShort,
        innerArea.width.toInt,
        labelWidth,
        "Readonly",
        es.readonly,
        es.selectedField == OutputEditorField.Readonly,
        () => props.callbacks.updateOutputEditor(_.copy(selectedField = OutputEditorField.Readonly)),
        (mode: RelationMatcherMode) => props.callbacks.updateOutputEditor(s => s.copy(readonly = s.readonly.copy(mode = mode), modified = true)),
        hoverPos
      )

      // Render sources popup if open
      if (es.sourcesPopupOpen) {
        val popupX = (innerArea.x + labelWidth).toShort
        val popupY = (innerArea.y + 1).toShort
        FormMultiSelectPopup(
          options = sourceOptions,
          selected = es.selectedSources,
          allSelected = es.allSourcesMode,
          hasAllOption = true,
          cursorIndex = es.sourcesPopupCursorIndex,
          popupX = popupX,
          popupY = popupY,
          onSelect = (sourceName: String) =>
            props.callbacks.updateOutputEditor { s =>
              val newSelected = if (s.selectedSources.contains(sourceName)) s.selectedSources - sourceName else s.selectedSources + sourceName
              s.copy(selectedSources = newSelected, allSourcesMode = false, modified = true)
            },
          onToggleAll = () =>
            props.callbacks.updateOutputEditor { s =>
              s.copy(allSourcesMode = !s.allSourcesMode, selectedSources = if (!s.allSourcesMode) Set.empty else s.selectedSources, modified = true)
            },
          onCursorMove = (idx: Int) => props.callbacks.updateOutputEditor(_.copy(sourcesPopupCursorIndex = idx)),
          onClose = () => props.callbacks.updateOutputEditor(_.copy(sourcesPopupOpen = false, sourcesPopupOriginal = None))
        ).render(ctx, area)
      }

      // Hints
      val hint = if (es.sourcesPopupOpen) {
        "[Space] Toggle  [Enter] Close  [Esc] Cancel"
      } else {
        "[Tab/Arrow] Navigate  [Enter] Save  [Esc] Cancel"
      }
      Hint(hint).render(ctx, chunks(2))
    }
  }

  private def renderMatcherField(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      labelWidth: Int,
      label: String,
      matcher: RelationMatcher,
      isFocused: Boolean,
      onFocus: () => Unit,
      onModeChange: RelationMatcherMode => Unit,
      hoverPosition: Option[(Int, Int)]
  ): Unit = {
    val matcherModes = List(
      (RelationMatcherMode.All, "All"),
      (RelationMatcherMode.None, "None"),
      (RelationMatcherMode.Custom, "Custom")
    )

    FormSelectField(
      label = label,
      options = matcherModes,
      value = matcher.mode,
      isFocused = isFocused,
      labelWidth = labelWidth,
      onFocus = onFocus,
      onChange = onModeChange,
      hoverPosition = hoverPosition
    ).render(ctx, Rect(x, y, width.toShort, 1))
  }
}
