package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.bridge.ColumnGrouper
import typr.bridge.ColumnMember
import typr.bridge.TypeSuggester
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.SourceStatus
import typr.cli.tui.TypeEditorField
import typr.cli.tui.TypeEditorState
import typr.cli.tui.navigation.Navigator

/** React-based TypeEditor with local UI state via useState */
object TypeEditorReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      typeName: String,
      editorState: TypeEditorState
  )

  /** UI-only state managed locally via useState */
  case class UiState(
      selectedField: TypeEditorField,
      setSelectedField: TypeEditorField => Unit
  )

  def component(props: Props): Component = {
    Component.named("TypeEditorReact") { (ctx, area) =>
      // Initialize local UI state with useState
      val (selectedField, setSelectedField) = ctx.useState[TypeEditorField](TypeEditorField.Name)

      val ui = UiState(
        selectedField = selectedField,
        setSelectedField = setSelectedField
      )

      val chunks = Layout(
        direction = Direction.Horizontal,
        margin = Margin(1),
        constraints = Array(Constraint.Percentage(55), Constraint.Percentage(45))
      ).split(area)

      renderForm(ctx, chunks(0), props, ui)
      renderHelp(ctx, chunks(1), props)
    }
  }

  private def renderForm(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val editorState = props.editorState

    val title = if (editorState.modified) {
      Spans.from(
        Span.styled(s"Edit Field Type: ${props.typeName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
        Span.styled(" *", Style(fg = Some(Color.Yellow)))
      )
    } else {
      Spans.from(Span.styled(s"Edit Field Type: ${props.typeName}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))
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
      height = math.max(10, area.height - 4).toShort
    )

    var y: Short = innerArea.y.toShort
    val ix: Short = innerArea.x.toShort
    val iw: Int = innerArea.width.toInt
    val maxX: Int = ix + iw

    // Name field
    renderFormField(ctx, ix, y, iw, maxX, "Name", editorState.name, ui.selectedField == TypeEditorField.Name, TypeEditorField.Name, ui)
    y = (y + 3).toShort

    // Section header: Database Match
    safeSetString(ctx.buffer, ix, y, maxX, "Database Match", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    // DB Columns field
    renderFormField(ctx, ix, y, iw, maxX, "Columns", editorState.dbColumns, ui.selectedField == TypeEditorField.DbColumns, TypeEditorField.DbColumns, ui)
    y = (y + 3).toShort

    // DB Schemas field
    renderFormField(ctx, ix, y, iw, maxX, "Schemas", editorState.dbSchemas, ui.selectedField == TypeEditorField.DbSchemas, TypeEditorField.DbSchemas, ui)
    y = (y + 3).toShort

    // DB Tables field
    renderFormField(ctx, ix, y, iw, maxX, "Tables", editorState.dbTables, ui.selectedField == TypeEditorField.DbTables, TypeEditorField.DbTables, ui)
    y = (y + 3).toShort

    // DB Types field
    renderFormField(ctx, ix, y, iw, maxX, "DB Types", editorState.dbTypes, ui.selectedField == TypeEditorField.DbTypes, TypeEditorField.DbTypes, ui)
    y = (y + 3).toShort

    // Primary Key toggle
    renderToggleField(ctx, ix, y, iw, maxX, "Primary Key", editorState.primaryKey, ui.selectedField == TypeEditorField.PrimaryKey, ui)
    y = (y + 3).toShort

    // Section header: Model Match (OpenAPI)
    safeSetString(ctx.buffer, ix, y, maxX, "Model Match (OpenAPI)", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    // Model Names field
    renderFormField(ctx, ix, y, iw, maxX, "Names", editorState.modelNames, ui.selectedField == TypeEditorField.ModelNames, TypeEditorField.ModelNames, ui)
    y = (y + 3).toShort

    // Model Schemas field
    renderFormField(ctx, ix, y, iw, maxX, "Schemas", editorState.modelSchemas, ui.selectedField == TypeEditorField.ModelSchemas, TypeEditorField.ModelSchemas, ui)
    y = (y + 3).toShort

    // Model Formats field
    renderFormField(ctx, ix, y, iw, maxX, "Formats", editorState.modelFormats, ui.selectedField == TypeEditorField.ModelFormats, TypeEditorField.ModelFormats, ui)

    // Keyboard hints at bottom
    val hintY = math.min((area.y + area.height - 2).toInt, (innerArea.y + innerArea.height).toInt).toShort
    safeSetString(ctx.buffer, ix, hintY, maxX, "[Tab] Next  [Enter] Toggle  [Esc] Save & Exit", Style(fg = Some(Color.DarkGray)))
  }

  private def renderFormField(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      label: String,
      value: String,
      isFocused: Boolean,
      field: TypeEditorField,
      ui: UiState
  ): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan))
    safeSetString(ctx.buffer, x, y, maxX, label, labelStyle)

    val inputY = (y + 1).toShort
    val inputWidth = math.min(width - 2, 40)
    val inputStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue))
      else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    // Draw input background
    val bgStr = " " * inputWidth
    safeSetString(ctx.buffer, x, inputY, maxX, bgStr, inputStyle)

    // Draw value
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

    // Large clickable area
    val clickArea = Rect(x, y, width.toShort, 2)
    ctx.onClick(clickArea) {
      ui.setSelectedField(field)
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
    val labelStyle = Style(fg = Some(Color.Cyan))
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
          case editor: AppScreen.TypeEditor =>
            val es = editor.state
            val newPk = es.primaryKey match {
              case None        => Some(true)
              case Some(true)  => Some(false)
              case Some(false) => None
            }
            state.copy(currentScreen = editor.copy(state = es.copy(primaryKey = newPk, modified = true)))
          case _ => state
        }
      }
      ui.setSelectedField(TypeEditorField.PrimaryKey)
    }
  }

  private def renderHelp(ctx: RenderContext, area: Rect, props: Props): Unit = {
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

    // Help section
    safeSetString(ctx.buffer, ix, y, maxX, "Pattern Syntax", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))
    y = (y + 2).toShort

    safeSetString(ctx.buffer, ix, y, maxX, "Comma-separated list", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, ix, y, maxX, "* matches any chars", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, ix, y, maxX, "? matches single char", Style(fg = Some(Color.White)))
    y = (y + 1).toShort
    safeSetString(ctx.buffer, ix, y, maxX, "!pattern excludes", Style(fg = Some(Color.White)))
    y = (y + 2).toShort

    // Column alignment preview
    renderColumnAlignment(ctx, ix, y, maxX, ih - (y - innerArea.y).toInt, props)
  }

  private def renderColumnAlignment(ctx: RenderContext, x: Short, startY: Short, maxX: Int, maxLines: Int, props: Props): Unit = {
    var y = startY
    val es = props.editorState

    // Explain AND logic
    safeSetString(ctx.buffer, x, y, maxX, "Matching (all criteria AND-ed):", Style(fg = Some(Color.Cyan)))
    y = (y + 1).toShort

    val metaDbs: Map[String, typr.MetaDb] = props.globalState.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "  Loading sources...", Style(fg = Some(Color.DarkGray)))
      return
    }

    // Get all columns first
    val allColumns = metaDbs.flatMap { case (sourceName, metaDb) =>
      ColumnGrouper.extractColumns(sourceName, metaDb)
    }.toList

    // Show filter pipeline with counts
    val columnPatterns = parsePatterns(es.dbColumns)
    val schemaPatterns = parsePatterns(es.dbSchemas)
    val tablePatterns = parsePatterns(es.dbTables)
    val typePatterns = parsePatterns(es.dbTypes)

    // Helper to get readable DB type name
    def getTypeName(tpe: typr.db.Type): String = tpe.getClass.getSimpleName.stripSuffix("$")

    // Apply filters step by step to show effect of each
    val afterColumns = if (columnPatterns.isEmpty) allColumns else filterByPatterns(allColumns, columnPatterns, _.column)
    val afterSchemas = if (schemaPatterns.isEmpty) afterColumns else filterByPatterns(afterColumns, schemaPatterns, _.schema)
    val afterTables = if (tablePatterns.isEmpty) afterSchemas else filterByPatterns(afterSchemas, tablePatterns, _.table)
    val afterTypes = if (typePatterns.isEmpty) afterTables else filterByPatterns(afterTables, typePatterns, c => getTypeName(c.dbType))
    // Note: Primary key filter not implemented - would need MetaDb access to check PK status
    val afterPk = afterTypes

    // Show active filters and their effect
    if (columnPatterns.nonEmpty) {
      val icon = if (afterColumns.size < allColumns.size) "\u2713" else "\u2022"
      safeSetString(ctx.buffer, x, y, maxX, s"  $icon Columns: ${columnPatterns.mkString(", ")}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"    ${allColumns.size} -> ${afterColumns.size}", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    if (schemaPatterns.nonEmpty) {
      val icon = if (afterSchemas.size < afterColumns.size) "\u2713" else "\u2022"
      safeSetString(ctx.buffer, x, y, maxX, s"  $icon Schemas: ${schemaPatterns.mkString(", ")}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"    ${afterColumns.size} -> ${afterSchemas.size}", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    if (tablePatterns.nonEmpty) {
      val icon = if (afterTables.size < afterSchemas.size) "\u2713" else "\u2022"
      safeSetString(ctx.buffer, x, y, maxX, s"  $icon Tables: ${tablePatterns.mkString(", ")}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"    ${afterSchemas.size} -> ${afterTables.size}", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    if (typePatterns.nonEmpty) {
      val icon = if (afterTypes.size < afterTables.size) "\u2713" else "\u2022"
      safeSetString(ctx.buffer, x, y, maxX, s"  $icon DB Types: ${typePatterns.mkString(", ")}", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"    ${afterTables.size} -> ${afterTypes.size}", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    es.primaryKey.foreach { pk =>
      val pkStr = if (pk) "Yes" else "No"
      safeSetString(ctx.buffer, x, y, maxX, s"  \u2022 Primary Key: $pkStr", Style(fg = Some(Color.White)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"    (filter applied at generation)", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
    }

    // Check if any filters are active
    val hasFilters = columnPatterns.nonEmpty || schemaPatterns.nonEmpty || tablePatterns.nonEmpty || typePatterns.nonEmpty || es.primaryKey.isDefined

    if (!hasFilters) {
      safeSetString(ctx.buffer, x, y, maxX, "  (no filters set)", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      safeSetString(ctx.buffer, x, y, maxX, s"  ${allColumns.size} total columns", Style(fg = Some(Color.DarkGray)))
      return
    }

    y = (y + 1).toShort

    // Show final matches with full context
    val (compatible, incompatible) = TypeSuggester.partitionByTypeCompatibility(afterPk)

    if (afterPk.isEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, "  No matches", Style(fg = Some(Color.Red)))
      return
    }

    if (compatible.nonEmpty) {
      safeSetString(ctx.buffer, x, y, maxX, s"Result: ${compatible.size} compatible", Style(fg = Some(Color.Green), addModifier = Modifier.BOLD))
      y = (y + 1).toShort

      // Show matches with full context: source:schema.table.column
      compatible.take(6).foreach { col =>
        if (y < startY + maxLines - 2) {
          val schemaPrefix = if (col.schema.nonEmpty) col.schema + "." else ""
          val fullPath = s"  ${col.source}:${schemaPrefix}${col.table}.${col.column}"
          val truncated = if (fullPath.length > maxX - x - 2) fullPath.take(maxX - x - 5) + "..." else fullPath
          safeSetString(ctx.buffer, x, y, maxX, truncated, Style(fg = Some(Color.White)))
          y = (y + 1).toShort
        }
      }
      if (compatible.size > 6) {
        safeSetString(ctx.buffer, x, y, maxX, s"  ... +${compatible.size - 6} more", Style(fg = Some(Color.DarkGray)))
        y = (y + 1).toShort
      }
    }

    if (incompatible.nonEmpty && y < startY + maxLines - 1) {
      safeSetString(ctx.buffer, x, y, maxX, s"+ ${incompatible.size} incompatible types", Style(fg = Some(Color.Yellow)))
    }
  }

  private def parsePatterns(input: String): List[String] = {
    input.split(",").map(_.trim).filter(_.nonEmpty).toList
  }

  private def filterByPatterns[T](items: List[T], patterns: List[String], getValue: T => String): List[T] = {
    val (inclusions, exclusions) = patterns.partition(!_.startsWith("!"))
    val exclusionPatterns = exclusions.map(_.stripPrefix("!"))

    items.filter { item =>
      val value = getValue(item)
      val matchesInclusion = inclusions.isEmpty || inclusions.exists(p => globMatches(p, value))
      val matchesExclusion = exclusionPatterns.exists(p => globMatches(p, value))
      matchesInclusion && !matchesExclusion
    }
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

  /** Safe string writing that truncates to prevent buffer overflows */
  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    val bufferArea = buffer.area
    if (availableWidth > 0 && x >= 0 && y >= bufferArea.y && y < bufferArea.y + bufferArea.height) {
      val truncated = text.take(availableWidth)
      buffer.setString(x, y, truncated, style)
    }
  }
}
