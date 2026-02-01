package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import typr.cli.tui.AppScreen
import typr.cli.tui.CompositeEditorState
import typr.cli.tui.CompositeEditorTab
import typr.cli.tui.FieldEditState
import typr.cli.tui.TuiState
import typr.cli.tui.navigation.Navigator

/** Keyboard handler for CompositeEditor screen */
object CompositeEditor {

  def handleKey(state: TuiState, editor: AppScreen.CompositeEditor, keyCode: KeyCode): TuiState = {
    val editorState = editor.state

    keyCode match {
      case _: KeyCode.Tab =>
        val newTab = editorState.selectedTab match {
          case CompositeEditorTab.Fields         => CompositeEditorTab.AlignedSources
          case CompositeEditorTab.AlignedSources => CompositeEditorTab.Alignment
          case CompositeEditorTab.Alignment      => CompositeEditorTab.Options
          case CompositeEditorTab.Options        => CompositeEditorTab.Fields
        }
        val newState = editorState.copy(selectedTab = newTab)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.BackTab =>
        val newTab = editorState.selectedTab match {
          case CompositeEditorTab.Fields         => CompositeEditorTab.Options
          case CompositeEditorTab.AlignedSources => CompositeEditorTab.Fields
          case CompositeEditorTab.Alignment      => CompositeEditorTab.AlignedSources
          case CompositeEditorTab.Options        => CompositeEditorTab.Alignment
        }
        val newState = editorState.copy(selectedTab = newTab)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Up =>
        editorState.selectedTab match {
          case CompositeEditorTab.Fields =>
            val newIdx = math.max(0, editorState.selectedFieldIndex - 1)
            val newState = editorState.copy(selectedFieldIndex = newIdx)
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.AlignedSources | CompositeEditorTab.Alignment =>
            val newIdx = math.max(0, editorState.selectedAlignedSourceIndex - 1)
            val newState = editorState.copy(selectedAlignedSourceIndex = newIdx)
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.Options =>
            state
        }

      case _: KeyCode.Down =>
        editorState.selectedTab match {
          case CompositeEditorTab.Fields =>
            val maxIdx = math.max(0, editorState.fields.size - 1)
            val newIdx = math.min(maxIdx, editorState.selectedFieldIndex + 1)
            val newState = editorState.copy(selectedFieldIndex = newIdx)
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.AlignedSources | CompositeEditorTab.Alignment =>
            val maxIdx = math.max(0, editorState.alignedSources.size - 1)
            val newIdx = math.min(maxIdx, editorState.selectedAlignedSourceIndex + 1)
            val newState = editorState.copy(selectedAlignedSourceIndex = newIdx)
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.Options =>
            state
        }

      case c: KeyCode.Char if c.c() == 'a' =>
        editorState.selectedTab match {
          case CompositeEditorTab.Fields =>
            val newField = FieldEditState.empty
            val newFields = editorState.fields :+ newField
            val newState = editorState.copy(
              fields = newFields,
              selectedFieldIndex = newFields.size - 1,
              addingField = true,
              modified = true
            )
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.AlignedSources =>
            val newState = editorState.copy(addingAlignedSource = true)
            state.copy(currentScreen = editor.copy(state = newState))
          case _ => state
        }

      case c: KeyCode.Char if c.c() == 'd' =>
        editorState.selectedTab match {
          case CompositeEditorTab.Fields if editorState.fields.nonEmpty =>
            val newFields = editorState.fields.zipWithIndex.filterNot(_._2 == editorState.selectedFieldIndex).map(_._1)
            val newIdx = math.min(editorState.selectedFieldIndex, math.max(0, newFields.size - 1))
            val newState = editorState.copy(fields = newFields, selectedFieldIndex = newIdx, modified = true)
            state.copy(currentScreen = editor.copy(state = newState))
          case CompositeEditorTab.AlignedSources if editorState.alignedSources.nonEmpty =>
            val newSources = editorState.alignedSources.zipWithIndex.filterNot(_._2 == editorState.selectedAlignedSourceIndex).map(_._1)
            val newIdx = math.min(editorState.selectedAlignedSourceIndex, math.max(0, newSources.size - 1))
            val newState = editorState.copy(alignedSources = newSources, selectedAlignedSourceIndex = newIdx, modified = true)
            state.copy(currentScreen = editor.copy(state = newState))
          case _ => state
        }

      case _: KeyCode.Enter =>
        if (editorState.addingField) {
          val newState = editorState.copy(addingField = false)
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }

      case _: KeyCode.Backspace =>
        if (editorState.addingField && editorState.fields.nonEmpty) {
          val lastIdx = editorState.fields.size - 1
          val lastField = editorState.fields(lastIdx)
          if (lastField.name.nonEmpty) {
            val updatedField = lastField.copy(name = lastField.name.dropRight(1))
            val newFields = editorState.fields.updated(lastIdx, updatedField)
            val newState = editorState.copy(fields = newFields)
            state.copy(currentScreen = editor.copy(state = newState))
          } else {
            state
          }
        } else if (editorState.selectedTab == CompositeEditorTab.Options) {
          val newState = editorState.copy(name = editorState.name.dropRight(1), modified = true)
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }

      case c: KeyCode.Char =>
        val ch = c.c()
        if (editorState.addingField && editorState.fields.nonEmpty) {
          val lastIdx = editorState.fields.size - 1
          val lastField = editorState.fields(lastIdx)
          val updatedField = lastField.copy(name = lastField.name + ch)
          val newFields = editorState.fields.updated(lastIdx, updatedField)
          val newState = editorState.copy(fields = newFields, modified = true)
          state.copy(currentScreen = editor.copy(state = newState))
        } else if (editorState.selectedTab == CompositeEditorTab.Options) {
          val newState = editorState.copy(name = editorState.name + ch, modified = true)
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }

      case _: KeyCode.Esc =>
        if (editorState.addingField) {
          val newState = editorState.copy(addingField = false)
          state.copy(currentScreen = editor.copy(state = newState))
        } else if (editorState.modified) {
          val types = state.config.types.getOrElse(Map.empty)
          val oldName = editor.typeName
          val newName = editorState.name

          val domainType = typr.config.generated.DomainType(
            alignedSources =
              if (editorState.alignedSources.isEmpty) None
              else
                Some(editorState.alignedSources.map { a =>
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
            description = if (editorState.description.isEmpty) None else Some(editorState.description),
            fields = editorState.fields.map { f =>
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
                domainType = Some(true),
                interface = None,
                mappers = Some(true)
              )
            ),
            primary = editorState.primarySource.map(_.key),
            projections = None
          )

          val newTypes = if (oldName != newName) {
            (types - oldName) + (newName -> domainType)
          } else {
            types + (newName -> domainType)
          }

          val newConfig = state.config.copy(types = Some(newTypes))
          val updatedState = state.copy(
            config = newConfig,
            currentScreen = AppScreen.TypeList(selectedIndex = 0, typeKindFilter = None),
            hasUnsavedChanges = true,
            statusMessage = Some((s"Domain type '$newName' updated", Color.Green))
          )
          Navigator.goBack(updatedState)
        } else {
          Navigator.goBack(state)
        }

      case _ => state
    }
  }
}
