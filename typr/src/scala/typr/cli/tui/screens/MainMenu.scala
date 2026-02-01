package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.crossterm.MouseEventKind
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.BridgeTypeKind
import typr.cli.tui.BrowserSourcePickerState
import typr.cli.tui.FocusableId
import typr.cli.tui.Location
import typr.cli.tui.TuiState
import typr.cli.tui.navigation.Navigator

object MainMenu {
  case class MenuItem(
      name: String,
      icon: String,
      shortDesc: String,
      longDesc: String,
      hint: String
  )

  val configItems: List[MenuItem] = List(
    MenuItem(
      "Sources",
      "🗄️",
      "Database Connections",
      "Connect to PostgreSQL, MariaDB, Oracle, SQL Server, or DuckDB",
      "Configure where Typr reads your schema"
    ),
    MenuItem(
      "Schema Browser",
      "🔍",
      "Explore Database",
      "Browse tables, columns, types, and relationships",
      "Understand your database structure"
    ),
    MenuItem(
      "Outputs",
      "📦",
      "Code Targets",
      "Configure Scala, Java, or Kotlin code generation",
      "Choose languages, libraries, and output paths"
    ),
    MenuItem(
      "Domain Types",
      "🧩",
      "Define Once, Use Everywhere",
      "Create canonical types aligned to multiple sources",
      "e.g., Customer → postgres, mariadb, api"
    ),
    MenuItem(
      "Field Types",
      "🔤",
      "Field-Level Type Safety",
      "Map columns to custom types by pattern",
      "e.g., *_id → CustomerId, *email* → Email"
    )
  )

  val actionItems: List[MenuItem] = List(
    MenuItem("Generate", "⚡", "Run Code Generation", "Generate type-safe code from your configuration", "Creates repositories, row types, and DSL"),
    MenuItem("Save", "💾", "Save Configuration", "Write current configuration to file", "Persists all changes to typr.yaml"),
    MenuItem("Exit", "🚪", "Exit Typr", "Close the configuration tool", "")
  )

  val menuItems: List[MenuItem] = configItems ++ actionItems

  def focusableElements: List[FocusableId] =
    menuItems.indices.map(i => FocusableId.ListItem(i)).toList

  def handleKey(state: TuiState, menu: AppScreen.MainMenu, keyCode: KeyCode): TuiState = keyCode match {
    case _: KeyCode.Up =>
      val newIdx = navigateUp(menu.selectedIndex)
      val newState = state.copy(currentScreen = menu.copy(selectedIndex = newIdx))
      Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

    case _: KeyCode.Down =>
      val newIdx = navigateDown(menu.selectedIndex)
      val newState = state.copy(currentScreen = menu.copy(selectedIndex = newIdx))
      Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

    case _: KeyCode.Left | _: KeyCode.BackTab =>
      val newIdx = navigateLeft(menu.selectedIndex)
      val newState = state.copy(currentScreen = menu.copy(selectedIndex = newIdx))
      Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

    case _: KeyCode.Right | _: KeyCode.Tab =>
      val newIdx = navigateRight(menu.selectedIndex)
      val newState = state.copy(currentScreen = menu.copy(selectedIndex = newIdx))
      Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

    case _: KeyCode.Enter =>
      activateMenuItem(state, menu.selectedIndex)

    case _: KeyCode.Esc =>
      handleExit(state)

    case c: KeyCode.Char if c.c() == 'q' =>
      handleExit(state)

    case c: KeyCode.Char if c.c() == 's' =>
      saveConfig(state)

    case _ => state
  }

  private def navigateUp(current: Int): Int = current match {
    case 0 | 1 => current
    case 2     => 0
    case 3     => 1
    case 4     => 2
    case 5     => 4
    case 6     => 3
    case 7     => 4
    case _     => current
  }

  private def navigateDown(current: Int): Int = current match {
    case 0         => 2
    case 1         => 3
    case 2         => 4
    case 3         => 4
    case 4         => 5
    case 5 | 6 | 7 => current
    case _         => current
  }

  private def navigateLeft(current: Int): Int = current match {
    case 0 | 2 | 4 => current
    case 1         => 0
    case 3         => 2
    case 5         => current
    case 6         => 5
    case 7         => 6
    case _         => current
  }

  private def navigateRight(current: Int): Int = current match {
    case 0 => 1
    case 1 => current
    case 2 => 3
    case 3 => current
    case 4 => current
    case 5 => 6
    case 6 => 7
    case 7 => current
    case _ => current
  }

  private def activateMenuItem(state: TuiState, index: Int): TuiState = index match {
    case 0 => // Sources
      Navigator.navigateTo(state, Location.SourceList, AppScreen.SourceList(selectedIndex = 0, testResults = Map.empty))
    case 1 => // Schema Browser
      Navigator.navigateTo(state, Location.SchemaBrowserPicker, AppScreen.BrowserSourcePicker(BrowserSourcePickerState(selectedIndex = 0)))
    case 2 => // Outputs
      Navigator.navigateTo(state, Location.OutputList, AppScreen.OutputList(selectedIndex = 0))
    case 3 => // Domain Types
      Navigator.navigateTo(
        state,
        Location.TypeList(Some(BridgeTypeKind.Composite)),
        AppScreen.TypeList(selectedIndex = TypeList.AddButtonIndex, typeKindFilter = Some(BridgeTypeKind.Composite))
      )
    case 4 => // Field Types
      Navigator.navigateTo(
        state,
        Location.TypeList(Some(BridgeTypeKind.Scalar)),
        AppScreen.TypeList(selectedIndex = TypeList.AddButtonIndex, typeKindFilter = Some(BridgeTypeKind.Scalar))
      )
    case 5 => // Generate
      Navigator.navigateTo(state, Location.Generating, AppScreen.Generating(typr.cli.tui.GeneratingState.initial))
    case 6 => // Save
      saveConfig(state)
    case 7 => // Exit
      handleExit(state)
    case _ => state
  }

  private def handleExit(state: TuiState): TuiState = {
    if (state.hasUnsavedChanges) {
      Navigator.showUnsavedChangesDialog(state)
    } else {
      state.copy(shouldExit = true)
    }
  }

  private def saveConfig(state: TuiState): TuiState = {
    import typr.cli.config.ConfigWriter
    ConfigWriter.write(state.configPath, state.config) match {
      case Right(_) =>
        state.copy(
          hasUnsavedChanges = false,
          statusMessage = Some(("Configuration saved!", Color.Green))
        )
      case Left(e) =>
        state.copy(statusMessage = Some((s"Save failed: ${e.getMessage}", Color.Red)))
    }
  }

  def handleMouse(
      state: TuiState,
      menu: AppScreen.MainMenu,
      col: Int,
      row: Int,
      termWidth: Int,
      termHeight: Int,
      eventKind: MouseEventKind
  ): TuiState = {
    val cardAreas = calculateCardAreas(termWidth, termHeight)
    val actionAreas = calculateActionAreas(termWidth, termHeight)

    val clickedCard = cardAreas.zipWithIndex.find { case ((x1, y1, x2, y2), _) =>
      col >= x1 && col < x2 && row >= y1 && row < y2
    }

    val clickedAction = actionAreas.zipWithIndex.find { case ((x1, y1, x2, y2), _) =>
      col >= x1 && col < x2 && row >= y1 && row < y2
    }

    eventKind match {
      case _: MouseEventKind.Down =>
        clickedCard match {
          case Some((_, idx)) =>
            activateMenuItem(state, idx)
          case None =>
            clickedAction match {
              case Some((_, idx)) =>
                activateMenuItem(state, idx + 5)
              case None =>
                state
            }
        }
      case _: MouseEventKind.Moved | _: MouseEventKind.Drag =>
        val hoveredIdx = clickedCard.map(_._2).orElse(clickedAction.map(_._2 + 5))
        hoveredIdx match {
          case Some(idx) if idx != menu.selectedIndex =>
            val newState = state.copy(currentScreen = menu.copy(selectedIndex = idx))
            Navigator.setFocus(newState, FocusableId.ListItem(idx))
          case _ =>
            state
        }
      case _ =>
        state
    }
  }

  private def calculateCardAreas(termWidth: Int, termHeight: Int): List[(Int, Int, Int, Int)] = {
    val margin = 1
    val headerHeight = 4
    val actionBarHeight = 5
    val statusBarHeight = 3

    val contentTop = margin + headerHeight
    val contentBottom = termHeight - margin - actionBarHeight - statusBarHeight
    val contentHeight = contentBottom - contentTop
    val rowHeight = contentHeight / 3

    val contentLeft = margin
    val contentRight = termWidth - margin
    val contentWidth = contentRight - contentLeft
    val colWidth = contentWidth / 2

    List(
      (contentLeft, contentTop, contentLeft + colWidth, contentTop + rowHeight),
      (contentLeft + colWidth, contentTop, contentRight, contentTop + rowHeight),
      (contentLeft, contentTop + rowHeight, contentLeft + colWidth, contentTop + 2 * rowHeight),
      (contentLeft + colWidth, contentTop + rowHeight, contentRight, contentTop + 2 * rowHeight),
      (contentLeft, contentTop + 2 * rowHeight, contentLeft + colWidth, contentBottom)
    )
  }

  private def calculateActionAreas(termWidth: Int, termHeight: Int): List[(Int, Int, Int, Int)] = {
    val margin = 1
    val statusBarHeight = 3
    val actionBarHeight = 5

    val actionTop = termHeight - margin - statusBarHeight - actionBarHeight
    val actionBottom = termHeight - margin - statusBarHeight

    val contentLeft = margin
    val contentRight = termWidth - margin
    val contentWidth = contentRight - contentLeft
    val btnWidth = contentWidth / 3

    List(
      (contentLeft, actionTop, contentLeft + btnWidth, actionBottom),
      (contentLeft + btnWidth, actionTop, contentLeft + 2 * btnWidth, actionBottom),
      (contentLeft + 2 * btnWidth, actionTop, contentRight, actionBottom)
    )
  }

  def render(f: Frame, state: TuiState, menu: AppScreen.MainMenu): Unit = {
    val mainChunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(4),
        Constraint.Min(10),
        Constraint.Length(5),
        Constraint.Length(3)
      )
    ).split(f.size)

    renderHeader(f, mainChunks(0))
    renderConfigCards(f, mainChunks(1), menu.selectedIndex, state)
    renderActionBar(f, mainChunks(2), menu.selectedIndex, state)
    renderStatusBar(f, mainChunks(3), state)
  }

  private def renderHeader(f: Frame, area: Rect): Unit = {
    val lines = List(
      Spans.from(
        Span.styled("  ╔═╗ ╦ ╦ ╔═╗ ╦═╗", Style(fg = Some(Color.Magenta), addModifier = Modifier.BOLD))
      ),
      Spans.from(
        Span.styled("  ║   ╚╦╝ ╠═╝ ╠╦╝", Style(fg = Some(Color.Magenta), addModifier = Modifier.BOLD)),
        Span.styled("   Seal Your Boundaries", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
        Span.styled("  —  Type-safe JVM code from your database schema", Style(fg = Some(Color.DarkGray)))
      ),
      Spans.from(
        Span.styled("  ╩   ╩   ╩   ╩╚═", Style(fg = Some(Color.Magenta), addModifier = Modifier.BOLD))
      )
    )
    val text = Text.fromSpans(lines*)
    val para = ParagraphWidget(text = text)
    f.renderWidget(para, area)
  }

  private def renderConfigCards(f: Frame, area: Rect, selectedIndex: Int, state: TuiState): Unit = {
    val rowChunks = Layout(
      direction = Direction.Vertical,
      constraints = Array(
        Constraint.Ratio(1, 3),
        Constraint.Ratio(1, 3),
        Constraint.Ratio(1, 3)
      )
    ).split(area)

    val row1Cols = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Ratio(1, 2), Constraint.Ratio(1, 2))
    ).split(rowChunks(0))

    val row2Cols = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Ratio(1, 2), Constraint.Ratio(1, 2))
    ).split(rowChunks(1))

    val row3Cols = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Ratio(1, 2), Constraint.Ratio(1, 2))
    ).split(rowChunks(2))

    renderCard(f, row1Cols(0), configItems(0), selectedIndex == 0, Some(state.config.sources.map(_.size).getOrElse(0)))
    renderCard(f, row1Cols(1), configItems(1), selectedIndex == 1, None)
    renderCard(f, row2Cols(0), configItems(2), selectedIndex == 2, Some(state.config.outputs.map(_.size).getOrElse(0)))
    renderCard(f, row2Cols(1), configItems(3), selectedIndex == 3, Some(countDomainTypes(state)))
    renderCard(f, row3Cols(0), configItems(4), selectedIndex == 4, Some(countFieldTypes(state)))
  }

  private def countFieldTypes(state: TuiState): Int = {
    state.config.types.map(_.values.count(_.isInstanceOf[typr.config.generated.FieldType])).getOrElse(0)
  }

  private def countDomainTypes(state: TuiState): Int = {
    state.config.types.map(_.values.count(_.isInstanceOf[typr.config.generated.DomainType])).getOrElse(0)
  }

  private def renderCard(f: Frame, area: Rect, item: MenuItem, isSelected: Boolean, count: Option[Int]): Unit = {
    val borderStyle = if (isSelected) {
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.DarkGray))
    }

    val titleStyle = if (isSelected) {
      Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.Gray))
    }

    val countStr = count.map(c => s" ($c)").getOrElse("")
    val countStyle = if (isSelected) Style(fg = Some(Color.Yellow)) else Style(fg = Some(Color.DarkGray))

    val lines = List(
      Spans.from(
        Span.styled(s" ${item.icon}  ", Style(fg = Some(Color.White))),
        Span.styled(item.name, titleStyle),
        Span.styled(countStr, countStyle)
      ),
      Spans.from(
        Span.styled(s"     ${item.shortDesc}", if (isSelected) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray)))
      ),
      Spans.nostyle(""),
      Spans.from(
        Span.styled(s"     ${item.longDesc}", Style(fg = Some(Color.Gray)))
      ),
      Spans.nostyle(""),
      Spans.from(
        Span.styled(s"     ${item.hint}", Style(fg = Some(Color.DarkGray)))
      )
    )

    val text = Text.fromSpans(lines*)

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = if (isSelected) BlockWidget.BorderType.Thick else BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )

    val para = ParagraphWidget(block = Some(block), text = text)
    f.renderWidget(para, area)
  }

  private def renderActionBar(f: Frame, area: Rect, selectedIndex: Int, _state: TuiState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(
        Constraint.Ratio(1, 3),
        Constraint.Ratio(1, 3),
        Constraint.Ratio(1, 3)
      )
    ).split(area)

    renderActionButton(f, chunks(0), actionItems(0), selectedIndex == 5, isPrimary = true)
    renderActionButton(f, chunks(1), actionItems(1), selectedIndex == 6, isPrimary = false)
    renderActionButton(f, chunks(2), actionItems(2), selectedIndex == 7, isPrimary = false)
  }

  private def renderActionButton(f: Frame, area: Rect, item: MenuItem, isSelected: Boolean, isPrimary: Boolean): Unit = {
    val (borderColor, titleColor, bgColor) = if (isSelected) {
      (Color.Cyan, Color.White, Some(Color.Blue))
    } else if (isPrimary) {
      (Color.Green, Color.Green, None)
    } else {
      (Color.DarkGray, Color.Gray, None)
    }

    val titleStyle = Style(fg = Some(titleColor), bg = bgColor, addModifier = Modifier.BOLD)

    val lines = List(
      Spans.from(
        Span.styled(s"  ${item.icon}  ${item.name}", titleStyle)
      ),
      Spans.from(
        Span.styled(s"      ${item.longDesc}", Style(fg = Some(Color.DarkGray)))
      )
    )

    val text = Text.fromSpans(lines*)

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = if (isSelected) BlockWidget.BorderType.Thick else BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(borderColor))
    )

    val para = ParagraphWidget(block = Some(block), text = text)
    f.renderWidget(para, area)
  }

  private def renderStatusBar(f: Frame, area: Rect, state: TuiState): Unit = {
    val statusColor = if (state.hasUnsavedChanges) Color.Yellow else Color.Green
    val statusText = if (state.hasUnsavedChanges) "● Modified" else "● Saved"

    val lines = List(
      Spans.from(
        Span.styled(s" ${state.configPath.getFileName}", Style(fg = Some(Color.Gray))),
        Span.styled("  │  ", Style(fg = Some(Color.DarkGray))),
        Span.styled(statusText, Style(fg = Some(statusColor))),
        Span.styled("  │  ", Style(fg = Some(Color.DarkGray))),
        Span.styled("[←↑↓→] Navigate  ", Style(fg = Some(Color.DarkGray))),
        Span.styled("[Enter] Select  ", Style(fg = Some(Color.DarkGray))),
        Span.styled("[s] Save  ", Style(fg = Some(Color.DarkGray))),
        Span.styled("[q] Quit", Style(fg = Some(Color.DarkGray)))
      )
    )

    val text = Text.fromSpans(lines*)

    val block = BlockWidget(
      borders = Borders.TOP,
      borderStyle = Style(fg = Some(Color.DarkGray))
    )

    val para = ParagraphWidget(block = Some(block), text = text)
    f.renderWidget(para, area)
  }
}
