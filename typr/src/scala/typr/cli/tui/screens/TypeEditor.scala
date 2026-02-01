package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.TypeEditorField
import typr.cli.tui.TypeEditorState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.TypePreviewFetcher
import typr.bridge.ColumnGrouper
import typr.bridge.ColumnMember
import typr.bridge.TypeSuggester

import java.util.concurrent.atomic.AtomicReference

object TypeEditor {
  private val fetcher = new AtomicReference[Option[TypePreviewFetcher]](None)
  private val currentTypeName = new AtomicReference[Option[String]](None)

  def handleKey(state: TuiState, editor: AppScreen.TypeEditor, keyCode: KeyCode): TuiState = {
    val editorState = editor.state

    keyCode match {
      case _: KeyCode.Tab =>
        val newState = editorState.copy(selectedField = editorState.nextField)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.BackTab =>
        val newState = editorState.copy(selectedField = editorState.prevField)
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Enter =>
        editorState.selectedField match {
          case TypeEditorField.PrimaryKey =>
            val newPk = editorState.primaryKey match {
              case None        => Some(true)
              case Some(true)  => Some(false)
              case Some(false) => None
            }
            val newState = editorState.copy(primaryKey = newPk, modified = true)
            state.copy(currentScreen = editor.copy(state = newState))
          case _ =>
            state
        }

      case _: KeyCode.Backspace =>
        val newState = editorState.selectedField match {
          case TypeEditorField.Name =>
            editorState.copy(name = editorState.name.dropRight(1), modified = true)
          case TypeEditorField.DbColumns =>
            editorState.copy(dbColumns = editorState.dbColumns.dropRight(1), modified = true)
          case TypeEditorField.DbSchemas =>
            editorState.copy(dbSchemas = editorState.dbSchemas.dropRight(1), modified = true)
          case TypeEditorField.DbTables =>
            editorState.copy(dbTables = editorState.dbTables.dropRight(1), modified = true)
          case TypeEditorField.DbTypes =>
            editorState.copy(dbTypes = editorState.dbTypes.dropRight(1), modified = true)
          case TypeEditorField.PrimaryKey =>
            editorState
          case TypeEditorField.ModelNames =>
            editorState.copy(modelNames = editorState.modelNames.dropRight(1), modified = true)
          case TypeEditorField.ModelSchemas =>
            editorState.copy(modelSchemas = editorState.modelSchemas.dropRight(1), modified = true)
          case TypeEditorField.ModelFormats =>
            editorState.copy(modelFormats = editorState.modelFormats.dropRight(1), modified = true)
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case c: KeyCode.Char =>
        val ch = c.c()
        val newState = editorState.selectedField match {
          case TypeEditorField.Name =>
            editorState.copy(name = editorState.name + ch, modified = true)
          case TypeEditorField.DbColumns =>
            editorState.copy(dbColumns = editorState.dbColumns + ch, modified = true)
          case TypeEditorField.DbSchemas =>
            editorState.copy(dbSchemas = editorState.dbSchemas + ch, modified = true)
          case TypeEditorField.DbTables =>
            editorState.copy(dbTables = editorState.dbTables + ch, modified = true)
          case TypeEditorField.DbTypes =>
            editorState.copy(dbTypes = editorState.dbTypes + ch, modified = true)
          case TypeEditorField.PrimaryKey =>
            editorState
          case TypeEditorField.ModelNames =>
            editorState.copy(modelNames = editorState.modelNames + ch, modified = true)
          case TypeEditorField.ModelSchemas =>
            editorState.copy(modelSchemas = editorState.modelSchemas + ch, modified = true)
          case TypeEditorField.ModelFormats =>
            editorState.copy(modelFormats = editorState.modelFormats + ch, modified = true)
        }
        state.copy(currentScreen = editor.copy(state = newState))

      case _: KeyCode.Esc =>
        if (editorState.modified) {
          val types = state.config.types.getOrElse(Map.empty)
          val oldName = editor.typeName
          val newName = editorState.name

          val newTypes = if (oldName != newName) {
            (types - oldName) + (newName -> editorState.toFieldType)
          } else {
            types + (newName -> editorState.toFieldType)
          }

          val newConfig = state.config.copy(types = Some(newTypes))
          resetFetcher()
          val updatedState = state.copy(
            config = newConfig,
            currentScreen = AppScreen.TypeList(selectedIndex = 0, typeKindFilter = None),
            hasUnsavedChanges = true,
            statusMessage = Some((s"Field type '$newName' updated", Color.Green))
          )
          Navigator.goBack(updatedState)
        } else {
          resetFetcher()
          Navigator.goBack(state)
        }

      case f: KeyCode.F if f.num() == 5 =>
        startPreview(state, editor)

      case _ => state
    }
  }

  def tick(state: TuiState, editor: AppScreen.TypeEditor): TuiState = {
    fetcher.get() match {
      case Some(f) =>
        val results = f.getResults
        if (results != editor.state.previewStatus) {
          val newState = editor.state.copy(previewStatus = results)
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }
      case None =>
        state
    }
  }

  def startPreview(state: TuiState, editor: AppScreen.TypeEditor): TuiState = {
    val editorState = editor.state
    val sources = state.config.sources.getOrElse(Map.empty)

    if (sources.isEmpty) {
      return state
    }

    val typeDef = editorState.toFieldType
    val typeName = editorState.name

    val f = new TypePreviewFetcher()
    fetcher.set(Some(f))
    currentTypeName.set(Some(typeName))

    f.fetchPreview(typeName, typeDef, sources)

    val newState = editorState.copy(previewStatus = Map.empty)
    state.copy(currentScreen = editor.copy(state = newState))
  }

  def resetFetcher(): Unit = {
    fetcher.get().foreach(_.reset())
    fetcher.set(None)
    currentTypeName.set(None)
  }

  def render(f: Frame, state: TuiState, editor: AppScreen.TypeEditor): Unit = {
    val vertChunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(10))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, vertChunks(0), s"Edit Field Type: ${editor.typeName}", isBackHovered)

    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(60), Constraint.Percentage(40))
    ).split(vertChunks(1))

    renderForm(f, chunks(0), editor.state)
    renderHelp(f, chunks(1), editor.state, state)
  }

  private def renderForm(f: Frame, area: Rect, editorState: TypeEditorState): Unit = {
    val lines = List(
      renderField("Name", editorState.name, editorState.selectedField == TypeEditorField.Name),
      Spans.nostyle(""),
      Spans.from(Span.styled("── Database Match ──", Style(fg = Some(Color.Cyan)))),
      renderField("Columns", editorState.dbColumns, editorState.selectedField == TypeEditorField.DbColumns),
      renderField("Schemas", editorState.dbSchemas, editorState.selectedField == TypeEditorField.DbSchemas),
      renderField("Tables", editorState.dbTables, editorState.selectedField == TypeEditorField.DbTables),
      renderField("DB Types", editorState.dbTypes, editorState.selectedField == TypeEditorField.DbTypes),
      renderToggleField("Primary Key", editorState.primaryKey, editorState.selectedField == TypeEditorField.PrimaryKey),
      Spans.nostyle(""),
      Spans.from(Span.styled("── Model Match (OpenAPI) ──", Style(fg = Some(Color.Cyan)))),
      renderField("Names", editorState.modelNames, editorState.selectedField == TypeEditorField.ModelNames),
      renderField("Schemas", editorState.modelSchemas, editorState.selectedField == TypeEditorField.ModelSchemas),
      renderField("Formats", editorState.modelFormats, editorState.selectedField == TypeEditorField.ModelFormats)
    )

    val title = if (editorState.modified) {
      Spans.from(
        Span.styled(s"Edit Field Type: ${editorState.name}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
        Span.styled(" *", Style(fg = Some(Color.Yellow)))
      )
    } else {
      Spans.from(Span.styled(s"Edit Field Type: ${editorState.name}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))
    }

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(title),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def renderField(label: String, value: String, selected: Boolean): Spans = {
    val labelStyle = Style(fg = Some(Color.Gray))
    val valueStyle = if (selected) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue))
    } else {
      Style(fg = Some(Color.White))
    }
    val cursor = if (selected) "█" else ""
    val displayValue = if (value.isEmpty && !selected) "(empty)" else value + cursor

    Spans.from(
      Span.styled(f"$label%-12s: ", labelStyle),
      Span.styled(displayValue, if (value.isEmpty && !selected) Style(fg = Some(Color.DarkGray)) else valueStyle)
    )
  }

  private def renderToggleField(label: String, value: Option[Boolean], selected: Boolean): Spans = {
    val labelStyle = Style(fg = Some(Color.Gray))
    val displayValue = value match {
      case None        => "(any)"
      case Some(true)  => "true"
      case Some(false) => "false"
    }
    val valueStyle = if (selected) {
      Style(fg = Some(Color.White), bg = Some(Color.Blue))
    } else {
      Style(fg = Some(Color.White))
    }

    Spans.from(
      Span.styled(f"$label%-12s: ", labelStyle),
      Span.styled(displayValue, valueStyle)
    )
  }

  private def renderHelp(f: Frame, area: Rect, editorState: TypeEditorState, tuiState: TuiState): Unit = {
    val baseLines = List(
      Spans.from(Span.styled("Help", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.styled("Patterns:", Style(fg = Some(Color.Cyan)))),
      Spans.from(Span.nostyle("  Comma-separated list")),
      Spans.from(Span.nostyle("  * matches any chars")),
      Spans.from(Span.nostyle("  ? matches single char")),
      Spans.from(Span.nostyle("  !pattern excludes"))
    )

    val alignmentLines = renderAlignment(editorState, tuiState)

    val keyLines = List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Keys:", Style(fg = Some(Color.DarkGray)))),
      Spans.from(Span.nostyle("[Up/Down] Navigate")),
      Spans.from(Span.nostyle("[Esc] Save and exit"))
    )

    val lines = baseLines ++ alignmentLines ++ keyLines

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Reference", Style(fg = Some(Color.Cyan))))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.fromSpans(lines*)
    )
    f.renderWidget(para, area)
  }

  private def renderAlignment(editorState: TypeEditorState, tuiState: TuiState): List[Spans] = {
    val metaDbs: Map[String, typr.MetaDb] = tuiState.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      List(
        Spans.nostyle(""),
        Spans.from(Span.styled("Column Alignment:", Style(fg = Some(Color.Cyan)))),
        Spans.from(Span.styled("  Loading sources...", Style(fg = Some(Color.DarkGray))))
      )
    } else {
      val columnPatterns = parsePatterns(editorState.dbColumns)

      if (columnPatterns.isEmpty) {
        List(
          Spans.nostyle(""),
          Spans.from(Span.styled("Column Alignment:", Style(fg = Some(Color.Cyan)))),
          Spans.from(Span.styled("  Enter column pattern", Style(fg = Some(Color.DarkGray))))
        )
      } else {
        val matchingColumns = findMatchingColumns(metaDbs, columnPatterns)

        if (matchingColumns.isEmpty) {
          List(
            Spans.nostyle(""),
            Spans.from(Span.styled("Column Alignment:", Style(fg = Some(Color.Cyan)))),
            Spans.from(Span.styled("  No matches found", Style(fg = Some(Color.DarkGray))))
          )
        } else {
          val (compatible, incompatible) = TypeSuggester.partitionByTypeCompatibility(matchingColumns)

          val compatLines = if (compatible.nonEmpty) {
            List(
              Spans.from(
                Span.styled("  Compatible: ", Style(fg = Some(Color.Green))),
                Span.styled(s"${compatible.size} columns", Style(fg = Some(Color.White)))
              )
            ) ++ compatible.take(3).map { col =>
              Spans.from(Span.styled(s"    ${col.source}.${col.table}.${col.column}", Style(fg = Some(Color.DarkGray))))
            }
          } else Nil

          val incompatLines = if (incompatible.nonEmpty) {
            List(
              Spans.from(
                Span.styled("  Incompatible: ", Style(fg = Some(Color.Red))),
                Span.styled(s"${incompatible.size} columns", Style(fg = Some(Color.White)))
              )
            ) ++ incompatible.take(2).map { col =>
              val typeName = col.dbType.getClass.getSimpleName.stripSuffix("$")
              Spans.from(
                Span.styled(s"    ${col.table}.${col.column}", Style(fg = Some(Color.DarkGray))),
                Span.styled(s" ($typeName)", Style(fg = Some(Color.Red)))
              )
            }
          } else Nil

          List(
            Spans.nostyle(""),
            Spans.from(Span.styled("Column Alignment:", Style(fg = Some(Color.Cyan))))
          ) ++ compatLines ++ incompatLines
        }
      }
    }
  }

  private def parsePatterns(input: String): List[String] = {
    input.split(",").map(_.trim).filter(_.nonEmpty).toList
  }

  private def findMatchingColumns(
      metaDbs: Map[String, typr.MetaDb],
      patterns: List[String]
  ): List[ColumnMember] = {
    val allColumns = metaDbs.flatMap { case (sourceName, metaDb) =>
      ColumnGrouper.extractColumns(sourceName, metaDb)
    }.toList

    val (inclusions, exclusions) = patterns.partition(!_.startsWith("!"))
    val exclusionPatterns = exclusions.map(_.stripPrefix("!"))

    allColumns.filter { col =>
      val matchesInclusion = inclusions.isEmpty || inclusions.exists(p => globMatches(p, col.column))
      val matchesExclusion = exclusionPatterns.exists(p => globMatches(p, col.column))
      matchesInclusion && !matchesExclusion
    }
  }

  private def globMatches(pattern: String, value: String): Boolean = {
    val regex = globToRegex(pattern)
    regex.matches(value.toLowerCase)
  }

  private def globToRegex(glob: String): scala.util.matching.Regex = {
    val escaped = glob.toLowerCase
      .replace(".", "\\.")
      .replace("**", "\u0000")
      .replace("*", ".*")
      .replace("\u0000", ".*")
      .replace("?", ".")
    s"^$escaped$$".r
  }
}
