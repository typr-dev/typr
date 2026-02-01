package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, Hint}
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.BrowserPane
import typr.cli.tui.ColumnInfo
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.SchemaBrowserLevel
import typr.cli.tui.SchemaBrowserState

/** React-based SchemaBrowser with local UI state via useState */
object SchemaBrowserReact {

  val CardHeight: Int = 3

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      browserState: SchemaBrowserState
  )

  /** UI-only state managed locally via useState */
  case class UiState(
      selectedSchemaIndex: Int,
      setSelectedSchemaIndex: Int => Unit,
      selectedTableIndex: Int,
      setSelectedTableIndex: Int => Unit,
      selectedColumnIndex: Int,
      setSelectedColumnIndex: Int => Unit
  )

  def component(props: Props): Component = {
    Component.named("SchemaBrowserReact") { (ctx, area) =>
      // Initialize local UI state with useState
      val (selectedSchemaIndex, setSelectedSchemaIndex) = ctx.useState(0)
      val (selectedTableIndex, setSelectedTableIndex) = ctx.useState(0)
      val (selectedColumnIndex, setSelectedColumnIndex) = ctx.useState(0)

      val ui = UiState(
        selectedSchemaIndex = selectedSchemaIndex,
        setSelectedSchemaIndex = setSelectedSchemaIndex,
        selectedTableIndex = selectedTableIndex,
        setSelectedTableIndex = setSelectedTableIndex,
        selectedColumnIndex = selectedColumnIndex,
        setSelectedColumnIndex = setSelectedColumnIndex
      )

      val browserState = props.browserState

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(10))
      ).split(area)

      val title = browserState.level match {
        case SchemaBrowserLevel.Schemas => s"Browse: ${browserState.sourceName.value}"
        case SchemaBrowserLevel.Tables =>
          val schema = browserState.currentSchema.getOrElse("default")
          s"Browse: ${browserState.sourceName.value} / $schema"
        case SchemaBrowserLevel.Columns =>
          val table = browserState.currentTable.map { case (s, t) => s.map(_ + ".").getOrElse("") + t }.getOrElse("table")
          s"Browse: $table"
      }

      BackButton(title)(props.callbacks.goBack()).render(ctx, chunks(0))

      val contentArea = chunks(1)

      browserState.level match {
        case SchemaBrowserLevel.Schemas =>
          renderSchemaCards(ctx, contentArea, props, ui)
        case SchemaBrowserLevel.Tables =>
          renderTableCards(ctx, contentArea, props, ui)
        case SchemaBrowserLevel.Columns =>
          renderColumnCards(ctx, contentArea, props, ui)
      }
    }
  }

  private def renderSchemaCards(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState
    val innerX = (area.x + 1).toShort
    val innerY = (area.y + 1).toShort
    val innerWidth = math.max(20, area.width - 2).toInt
    val innerHeight = math.max(5, area.height - 3).toInt
    val maxX = innerX + innerWidth

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Schemas", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    var y = innerY.toInt
    val visibleCards = (innerHeight - 1) / (CardHeight + 1)

    browserState.schemas.zipWithIndex.foreach { case (schema, idx) =>
      if (y + CardHeight <= innerY + innerHeight - 2) {
        val isSelected = idx == ui.selectedSchemaIndex
        renderSchemaCard(ctx, innerX, y.toShort, innerWidth, maxX, schema, isSelected, idx, ui)
        y += CardHeight + 1
      }
    }

    // Scroll handlers
    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedSchemaIndex - 1)
      ui.setSelectedSchemaIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val newIdx = math.min(browserState.schemas.length - 1, ui.selectedSchemaIndex + 1)
      ui.setSelectedSchemaIndex(math.max(0, newIdx))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[Enter] Browse • [f] Search • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderSchemaCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      schema: String,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val cardWidth = math.min(width - 2, 50)
    val borderColor = if (isSelected) Color.Cyan else Color.DarkGray
    val bgColor = if (isSelected) Some(Color.Blue) else None

    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, bottomBorder, borderStyle)

    val iconStyle = Style(fg = Some(Color.Yellow), bg = bgColor)
    val nameStyle = Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, " □ ", iconStyle)
    safeSetString(ctx.buffer, (x + 5).toShort, (y + 1).toShort, maxX, schema.take(cardWidth - 8), nameStyle)

    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)
    ctx.onHover(cardArea)(() => {
      ui.setSelectedSchemaIndex(idx)
    })
    ctx.onClick(cardArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case browser: AppScreen.SchemaBrowser =>
            SchemaBrowser.navigateToTablesFromReact(state, browser, idx)
          case _ => state
        }
      }
    }
  }

  private def renderTableCards(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(40), Constraint.Percentage(60))
    ).split(area)

    // Left: Schema context (faded)
    renderSchemaContext(ctx, chunks(0), props, ui)

    // Right: Table cards
    val innerX = (chunks(1).x + 1).toShort
    val innerY = (chunks(1).y + 1).toShort
    val innerWidth = math.max(20, chunks(1).width - 2).toInt
    val innerHeight = math.max(5, chunks(1).height - 3).toInt
    val maxX = innerX + innerWidth

    val schemaTitle = browserState.currentSchema.map(s => s": $s").getOrElse("")
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Tables$schemaTitle", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, chunks(1))

    var y = innerY.toInt

    browserState.tables.zipWithIndex.foreach { case ((schema, table), idx) =>
      if (y + CardHeight <= innerY + innerHeight - 2) {
        val isSelected = idx == ui.selectedTableIndex
        val displayName = schema.filter(_ != browserState.currentSchema.getOrElse("")).map(s => s"$s.").getOrElse("") + table
        renderTableCard(ctx, innerX, y.toShort, innerWidth, maxX, displayName, isSelected, idx, ui)
        y += CardHeight + 1
      }
    }

    ctx.onScrollUp(chunks(1)) {
      val newIdx = math.max(0, ui.selectedTableIndex - 1)
      ui.setSelectedTableIndex(newIdx)
    }

    ctx.onScrollDown(chunks(1)) {
      val newIdx = math.min(browserState.tables.length - 1, ui.selectedTableIndex + 1)
      ui.setSelectedTableIndex(math.max(0, newIdx))
    }

    val hintY = (chunks(1).y + chunks(1).height - 2).toShort
    safeSetString(ctx.buffer, (chunks(1).x + 2).toShort, hintY, maxX, "[Enter] Columns • [f] Search • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderSchemaContext(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Schemas", Style(fg = Some(Color.DarkGray))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(Color.DarkGray))
    )
    ctx.frame.renderWidget(block, area)

    var y = (area.y + 1).toShort
    val maxX = area.x + area.width - 2

    browserState.schemas.zipWithIndex.foreach { case (schema, idx) =>
      if (y < area.y + area.height - 2) {
        val isSelected = idx == ui.selectedSchemaIndex
        val style = if (isSelected) Style(fg = Some(Color.Gray), addModifier = Modifier.BOLD) else Style(fg = Some(Color.DarkGray))
        val prefix = if (isSelected) "▸ " else "  "
        safeSetString(ctx.buffer, (area.x + 2).toShort, y, maxX.toInt, s"$prefix$schema", style)
        y = (y + 1).toShort
      }
    }
  }

  private def renderTableCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      tableName: String,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val cardWidth = math.min(width - 2, 50)
    val borderColor = if (isSelected) Color.Cyan else Color.DarkGray
    val bgColor = if (isSelected) Some(Color.Blue) else None

    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, bottomBorder, borderStyle)

    val iconStyle = Style(fg = Some(Color.Yellow), bg = bgColor)
    val nameStyle = Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, " ▣ ", iconStyle)
    safeSetString(ctx.buffer, (x + 5).toShort, (y + 1).toShort, maxX, tableName.take(cardWidth - 8), nameStyle)

    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)
    ctx.onHover(cardArea)(() => {
      ui.setSelectedTableIndex(idx)
    })
    ctx.onClick(cardArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case browser: AppScreen.SchemaBrowser =>
            SchemaBrowser.navigateToColumnsFromReact(state, browser, idx)
          case _ => state
        }
      }
    }
  }

  private def renderColumnCards(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(35), Constraint.Percentage(65))
    ).split(area)

    // Left: Column list as cards
    renderColumnList(ctx, chunks(0), props, ui)

    // Right: Column details
    renderColumnDetails(ctx, chunks(1), props, ui)
  }

  private def renderColumnList(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val tableName = browserState.currentTable.map { case (s, t) => s.map(_ + ".").getOrElse("") + t }.getOrElse("Columns")
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(tableName, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 1).toShort
    val innerY = (area.y + 1).toShort
    val innerWidth = math.max(10, area.width - 2).toInt
    val innerHeight = math.max(3, area.height - 3).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    browserState.columns.zipWithIndex.foreach { case (col, idx) =>
      if (y + 2 <= innerY + innerHeight - 1) {
        val isSelected = idx == ui.selectedColumnIndex
        renderColumnRow(ctx, innerX, y.toShort, innerWidth, maxX, col, isSelected, idx, ui)
        y += 2
      }
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedColumnIndex - 1)
      ui.setSelectedColumnIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val newIdx = math.min(browserState.columns.length - 1, ui.selectedColumnIndex + 1)
      ui.setSelectedColumnIndex(math.max(0, newIdx))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[f] Search • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderColumnRow(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      col: ColumnInfo,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val bgColor = if (isSelected) Some(Color.Blue) else None
    val borderColor = if (isSelected) Color.Cyan else Color.DarkGray

    // Draw row background
    if (isSelected) {
      safeSetString(ctx.buffer, x, y, maxX, " " * (width - 2), Style(bg = Some(Color.Blue)))
      safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, " " * (width - 2), Style(bg = Some(Color.Blue)))
    }

    // Icons
    val pkIcon = if (col.isPrimaryKey) "◆" else " "
    val fkIcon = if (col.foreignKey.isDefined) "→" else " "
    val nullIcon = if (col.nullable) "?" else "!"
    val typeIcon = col.matchingType.map(_ => "⚡").getOrElse(" ")

    val iconStyle = Style(fg = Some(Color.Yellow), bg = bgColor)
    val nameStyle = if (isSelected) Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD) else Style(fg = Some(Color.White), bg = bgColor)
    val typeStyle = Style(fg = Some(Color.DarkGray), bg = bgColor)

    safeSetString(ctx.buffer, x, y, maxX, s" $pkIcon$fkIcon$nullIcon$typeIcon ", iconStyle)
    safeSetString(ctx.buffer, (x + 6).toShort, y, maxX, col.name.take(width - 10), nameStyle)
    safeSetString(ctx.buffer, (x + 6).toShort, (y + 1).toShort, maxX, SchemaBrowser.dbTypeName(col.dbType).take(width - 10), typeStyle)

    val rowArea = Rect(x, y, (width - 2).toShort, 2)
    ctx.onClick(rowArea) {
      ui.setSelectedColumnIndex(idx)
    }
    ctx.onHover(rowArea)(() => {
      ui.setSelectedColumnIndex(idx)
    })
  }

  private def renderColumnDetails(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Column Details", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 2).toShort
    val innerY = (area.y + 2).toShort
    val innerWidth = math.max(10, area.width - 4).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    // Get current column using local UI state
    val currentColumn = browserState.columns.lift(ui.selectedColumnIndex)

    currentColumn match {
      case Some(col) =>
        // Name card
        val nameCardWidth = math.min(innerWidth, 50)
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╭" + "─" * (nameCardWidth - 2) + "╮", Style(fg = Some(Color.Cyan)))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (nameCardWidth - 2) + "│", Style(fg = Some(Color.Cyan)))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, col.name, Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (nameCardWidth - 2) + "│", Style(fg = Some(Color.Cyan)))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, SchemaBrowser.dbTypeName(col.dbType), Style(fg = Some(Color.Yellow)))
        val nullStr = if (col.nullable) " nullable" else " not null"
        safeSetString(ctx.buffer, (innerX + 2 + SchemaBrowser.dbTypeName(col.dbType).length).toShort, y.toShort, maxX, nullStr, Style(fg = Some(Color.DarkGray)))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╰" + "─" * (nameCardWidth - 2) + "╯", Style(fg = Some(Color.Cyan)))
        y += 2

        // Attributes
        if (col.isPrimaryKey) {
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "◆ Primary Key", Style(fg = Some(Color.Green)))
          y += 1
        }

        col.foreignKey.foreach { fk =>
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "→ Foreign Key:", Style(fg = Some(Color.Yellow)))
          y += 1
          val target = s"  ${fk.targetSchema.map(_ + ".").getOrElse("")}${fk.targetTable}.${fk.targetColumn}"
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, target, Style(fg = Some(Color.Cyan)))
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "  Press [g] to navigate", Style(fg = Some(Color.DarkGray)))
          y += 2
        }

        col.matchingType match {
          case Some(typeName) =>
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "⚡ Bridge Type:", Style(fg = Some(Color.Yellow)))
            y += 1
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"  $typeName", Style(fg = Some(Color.Green)))
            y += 1
          case None =>
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "No Bridge type matched", Style(fg = Some(Color.DarkGray)))
            y += 1
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Press [t] to create one", Style(fg = Some(Color.DarkGray)))
            y += 1
        }

        col.comment.foreach { comment =>
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Comment:", Style(fg = Some(Color.Yellow)))
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, comment.take(innerWidth - 2), Style(fg = Some(Color.Gray)))
        }

        col.defaultValue.foreach { default =>
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"Default: $default", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Select a column to view details", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = (area.y + area.height - 2).toShort
    val hints = currentColumn.flatMap(_.foreignKey) match {
      case Some(_) => "[g] Go to FK • [t] Create Type"
      case None    => "[t] Create Type"
    }
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, hints, Style(fg = Some(Color.DarkGray)))
  }

  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    val bufferArea = buffer.area
    if (availableWidth > 0 && x >= 0 && y >= bufferArea.y && y < bufferArea.y + bufferArea.height) {
      buffer.setString(x, y, text.take(availableWidth), style)
    }
  }
}
