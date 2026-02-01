package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{Box, Column, ClickableRow, Hint, Row}
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.BridgeTypeKind
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location

/** React-based rendering for MainMenu screen with local state. */
object MainMenuReact {

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
    MenuItem("Reset", "↺", "Reset to YAML", "Discard changes and reload from disk", "Reverts to last saved state"),
    MenuItem("Exit", "🚪", "Exit Typr", "Close the configuration tool", "")
  )

  /** Props for MainMenu component */
  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks
  )

  /** Create the MainMenu component with local state for selectedIndex */
  def component(props: Props): Component = {
    Component.named("MainMenu") { (ctx, area) =>
      val (selectedIndex, setSelectedIndex) = ctx.useState(0)

      // Wrap setSelectedIndex to also trigger a re-render via TuiState update
      val onSelectIndex: Int => Unit = idx => {
        setSelectedIndex(idx)
        Interactive.scheduleStateUpdate(identity) // Force re-render
      }

      val mainChunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(
          Constraint.Length(4),
          Constraint.Min(10),
          Constraint.Length(5),
          Constraint.Length(3)
        )
      ).split(area)

      renderHeader(ctx, mainChunks(0))
      renderConfigCards(ctx, mainChunks(1), selectedIndex, props.globalState, onSelectIndex, idx => handleActivate(idx, props.callbacks))
      renderActionBar(ctx, mainChunks(2), selectedIndex, props.globalState, onSelectIndex, idx => handleActivate(idx, props.callbacks))
      renderStatusBar(ctx, mainChunks(3), props.globalState)
    }
  }

  private def handleActivate(index: Int, callbacks: GlobalCallbacks): Unit = index match {
    case 0 => callbacks.navigateTo(Location.SourceList)
    case 1 => callbacks.navigateTo(Location.SchemaBrowserPicker)
    case 2 => callbacks.navigateTo(Location.OutputList)
    case 3 => callbacks.navigateTo(Location.TypeList(Some(BridgeTypeKind.Composite))) // Domain Types
    case 4 => callbacks.navigateTo(Location.TypeList(Some(BridgeTypeKind.Scalar))) // Field Types
    case 5 => callbacks.navigateTo(Location.Generating)
    case 6 => () // Save - handled by Interactive
    case 7 => resetToYaml(callbacks) // Reset to stored YAML
    case 8 => callbacks.requestExit()
    case _ => ()
  }

  private def resetToYaml(callbacks: GlobalCallbacks): Unit = {
    Interactive.scheduleStateUpdate { state =>
      import java.nio.file.Files
      try {
        val yamlContent = Files.readString(state.configPath)
        val result = for {
          substituted <- typr.cli.config.EnvSubstitution.substitute(yamlContent)
          config <- typr.cli.config.ConfigParser.parse(substituted)
        } yield config
        result match {
          case Right(reloadedConfig) =>
            state.copy(
              config = reloadedConfig,
              hasUnsavedChanges = false,
              sourceStatuses = Map.empty // Clear cached source statuses since config changed
            )
          case Left(_) =>
            // Failed to parse, keep current state
            state
        }
      } catch {
        case _: Exception =>
          // Failed to read file, keep current state
          state
      }
    }
    callbacks.setStatusMessage("Reset to stored YAML", tui.Color.Yellow)
  }

  private def renderHeader(ctx: RenderContext, area: Rect): Unit = {
    val lines = List(
      Spans.from(
        Span.styled("  TYPR", Style(fg = Some(Color.Magenta), addModifier = Modifier.BOLD))
      ),
      Spans.from(
        Span.styled("  ", Style()),
        Span.styled("Seal Your Boundaries", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)),
        Span.styled("  -  Type-safe JVM code from your database schema", Style(fg = Some(Color.DarkGray)))
      ),
      Spans.nostyle("")
    )
    val text = Text.fromSpans(lines*)
    val para = ParagraphWidget(text = text)
    ctx.frame.renderWidget(para, area)
  }

  private def renderConfigCards(
      ctx: RenderContext,
      area: Rect,
      selectedIndex: Int,
      state: GlobalState,
      onSelectIndex: Int => Unit,
      onActivate: Int => Unit
  ): Unit = {
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

    renderCard(ctx, row1Cols(0), configItems(0), 0, selectedIndex == 0, Some(state.config.sources.map(_.size).getOrElse(0)), onSelectIndex, onActivate)
    renderCard(ctx, row1Cols(1), configItems(1), 1, selectedIndex == 1, None, onSelectIndex, onActivate)
    renderCard(ctx, row2Cols(0), configItems(2), 2, selectedIndex == 2, Some(state.config.outputs.map(_.size).getOrElse(0)), onSelectIndex, onActivate)
    renderCard(ctx, row2Cols(1), configItems(3), 3, selectedIndex == 3, Some(countDomainTypes(state)), onSelectIndex, onActivate) // Domain Types
    renderCard(ctx, row3Cols(0), configItems(4), 4, selectedIndex == 4, Some(countFieldTypes(state)), onSelectIndex, onActivate) // Field Types
  }

  private def countFieldTypes(state: GlobalState): Int = {
    state.config.types.map(_.values.count(_.isInstanceOf[typr.config.generated.FieldType])).getOrElse(0)
  }

  private def countDomainTypes(state: GlobalState): Int = {
    state.config.types.map(_.values.count(_.isInstanceOf[typr.config.generated.DomainType])).getOrElse(0)
  }

  private def renderCard(
      ctx: RenderContext,
      area: Rect,
      item: MenuItem,
      index: Int,
      isSelected: Boolean,
      count: Option[Int],
      onSelectIndex: Int => Unit,
      onActivate: Int => Unit
  ): Unit = {
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

    val iconStr = if (item.icon.nonEmpty) s" ${item.icon}  " else "    "

    val lines = List(
      Spans.from(
        Span.styled(iconStr, Style(fg = Some(Color.White))),
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
    ctx.frame.renderWidget(para, area)

    ctx.onClick(area)(onActivate(index))
    ctx.onHover(area)(onSelectIndex(index))
  }

  private def renderActionBar(
      ctx: RenderContext,
      area: Rect,
      selectedIndex: Int,
      state: GlobalState,
      onSelectIndex: Int => Unit,
      onActivate: Int => Unit
  ): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(
        Constraint.Ratio(1, 4),
        Constraint.Ratio(1, 4),
        Constraint.Ratio(1, 4),
        Constraint.Ratio(1, 4)
      )
    ).split(area)

    renderActionButton(ctx, chunks(0), actionItems(0), 5, selectedIndex == 5, isPrimary = true, onSelectIndex, onActivate)
    renderActionButton(ctx, chunks(1), actionItems(1), 6, selectedIndex == 6, isPrimary = false, onSelectIndex, onActivate)
    // Only show Reset button when there are unsaved changes
    if (state.hasUnsavedChanges) {
      renderActionButton(ctx, chunks(2), actionItems(2), 7, selectedIndex == 7, isPrimary = false, onSelectIndex, onActivate)
    }
    renderActionButton(ctx, chunks(3), actionItems(3), 8, selectedIndex == 8, isPrimary = false, onSelectIndex, onActivate)
  }

  private def renderActionButton(
      ctx: RenderContext,
      area: Rect,
      item: MenuItem,
      index: Int,
      isSelected: Boolean,
      isPrimary: Boolean,
      onSelectIndex: Int => Unit,
      onActivate: Int => Unit
  ): Unit = {
    val (borderColor, titleColor, bgColor) = if (isSelected) {
      (Color.Cyan, Color.White, Some(Color.Cyan))
    } else if (isPrimary) {
      (Color.Green, Color.Green, None)
    } else {
      (Color.DarkGray, Color.Gray, None)
    }

    val fgColor = if (isSelected) Color.Black else titleColor
    val titleStyle = Style(fg = Some(fgColor), bg = bgColor, addModifier = Modifier.BOLD)

    val iconStr = if (item.icon.nonEmpty) s"  ${item.icon}  " else "      "

    val lines = List(
      Spans.from(
        Span.styled(s"$iconStr${item.name}", titleStyle)
      ),
      Spans.from(
        Span.styled(s"      ${item.longDesc}", Style(fg = Some(Color.DarkGray), bg = bgColor))
      )
    )

    val text = Text.fromSpans(lines*)

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = if (isSelected) BlockWidget.BorderType.Thick else BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(borderColor))
    )

    val para = ParagraphWidget(block = Some(block), text = text)
    ctx.frame.renderWidget(para, area)

    ctx.onClick(area)(onActivate(index))
    ctx.onHover(area)(onSelectIndex(index))
  }

  private def renderStatusBar(ctx: RenderContext, area: Rect, state: GlobalState): Unit = {
    val statusColor = if (state.hasUnsavedChanges) Color.Yellow else Color.Green
    val statusText = if (state.hasUnsavedChanges) "Modified" else "Saved"

    val lines = List(
      Spans.from(
        Span.styled(s" ${state.configPath.getFileName}", Style(fg = Some(Color.Gray))),
        Span.styled("  |  ", Style(fg = Some(Color.DarkGray))),
        Span.styled(statusText, Style(fg = Some(statusColor))),
        Span.styled("  |  ", Style(fg = Some(Color.DarkGray))),
        Span.styled("[Arrows] Navigate  ", Style(fg = Some(Color.DarkGray))),
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
    ctx.frame.renderWidget(para, area)
  }
}
