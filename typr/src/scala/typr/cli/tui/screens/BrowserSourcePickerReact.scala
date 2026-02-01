package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, ClickableRow, Hint}
import tui.widgets.*
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.SchemaBrowserLevel
import typr.cli.tui.SchemaBrowserState
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.SpecBrowserState
import typr.cli.tui.stats

/** React-based rendering for BrowserSourcePicker screen with local state. */
object BrowserSourcePickerReact {

  /** Props for BrowserSourcePicker */
  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks
  )

  def component(props: Props): Component = {
    Component.named("BrowserSourcePicker") { (ctx, area) =>
      val (selectedIndex, setSelectedIndex) = ctx.useState(0)

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(5))
      ).split(area)

      BackButton("Select Source")(props.callbacks.goBack()).render(ctx, chunks(0))

      val contentArea = chunks(1)
      val block = BlockWidget(
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

      val browsableSources = BrowserSourcePicker.getBrowsableSources(toTuiState(props.globalState))
      val loadingSources = BrowserSourcePicker.getLoadingSources(toTuiState(props.globalState))

      val onBrowse = (idx: Int) => {
        if (browsableSources.nonEmpty && idx < browsableSources.length) {
          browsableSources(idx) match {
            case BrowserSourcePicker.BrowsableSource.Database(sourceName, ready) =>
              val browserState = SchemaBrowserState.fromMetaDb(sourceName, ready.metaDb)
              val initialSchema = if (browserState.level == SchemaBrowserLevel.Tables) browserState.schemas.headOption else None
              props.callbacks.navigateTo(Location.SchemaBrowser(sourceName, browserState.level, initialSchema, None))
            case BrowserSourcePicker.BrowsableSource.Spec(sourceName, ready) =>
              props.callbacks.navigateTo(Location.SpecBrowser(sourceName))
          }
        }
      }

      renderSources(ctx, innerArea, browsableSources, loadingSources, selectedIndex, setSelectedIndex, onBrowse)

      val hintY = contentArea.y + contentArea.height.toInt - 2
      ctx.buffer.setString((contentArea.x + 2).toShort, hintY, "[Enter] Browse  [Esc] Back  [q] Quit", Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderSources(
      ctx: RenderContext,
      area: Rect,
      browsableSources: List[BrowserSourcePicker.BrowsableSource],
      loadingSources: List[(SourceName, SourceStatus)],
      selectedIndex: Int,
      onSelectIndex: Int => Unit,
      onBrowse: Int => Unit
  ): Unit = {
    val buf = ctx.buffer
    var y = area.y

    if (browsableSources.isEmpty) {
      buf.setString(area.x, y, "No sources with loaded schema.", Style(fg = Some(Color.DarkGray)))
      y = (y + 1).toShort
      buf.setString(area.x, y, "Wait for sources to finish loading...", Style(fg = Some(Color.DarkGray)))
      y = (y + 2).toShort

      if (loadingSources.nonEmpty) {
        buf.setString(area.x, y, "Loading:", Style(fg = Some(Color.Yellow)))
        y = (y + 1).toShort
        loadingSources.take(5).foreach { case (name, _) =>
          buf.setString((area.x + 2).toShort, y, s"${name.value}...", Style(fg = Some(Color.DarkGray)))
          y = (y + 1).toShort
        }
      }
    } else {
      browsableSources.zipWithIndex.foreach { case (source, idx) =>
        if (y < area.y + area.height.toInt - 2) {
          val isSelected = idx == selectedIndex

          val style = if (isSelected) {
            Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          } else {
            Style(fg = Some(Color.White))
          }

          val prefix = if (isSelected) "> " else "  "

          source match {
            case BrowserSourcePicker.BrowsableSource.Database(sourceName, ready) =>
              val s = ready.metaDb.stats
              val info = s"${s.tableCount} tables, ${s.viewCount} views, ${s.enumCount} enums"
              val icon = "●"
              buf.setString(area.x, y, prefix, style)
              buf.setString((area.x + 2).toShort, y, s"$icon ", Style(fg = Some(Color.Green)))
              buf.setString((area.x + 4).toShort, y, sourceName.value, style)
              buf.setString((area.x + 4 + sourceName.value.length + 1).toShort, y, s"($info)", Style(fg = Some(Color.DarkGray)))

            case BrowserSourcePicker.BrowsableSource.Spec(sourceName, ready) =>
              val s = ready.spec.stats
              val typeLabel = ready.sourceType match {
                case typr.cli.tui.SpecSourceType.OpenApi    => "OpenAPI"
                case typr.cli.tui.SpecSourceType.JsonSchema => "JSON Schema"
              }
              val info = s"$typeLabel - ${s.modelCount} models, ${s.apiCount} endpoints"
              val icon = "◆"
              buf.setString(area.x, y, prefix, style)
              buf.setString((area.x + 2).toShort, y, s"$icon ", Style(fg = Some(Color.Cyan)))
              buf.setString((area.x + 4).toShort, y, sourceName.value, style)
              buf.setString((area.x + 4 + sourceName.value.length + 1).toShort, y, s"($info)", Style(fg = Some(Color.DarkGray)))
          }

          val rowArea = Rect(area.x, y, area.width, 1)
          ctx.onClick(rowArea)(onBrowse(idx))
          ctx.onHover(rowArea)(onSelectIndex(idx))

          y = (y + 1).toShort
        }
      }

      if (loadingSources.nonEmpty) {
        y = (y + 1).toShort
        buf.setString(area.x, y, s"${loadingSources.length} source(s) still loading...", Style(fg = Some(Color.DarkGray)))
      }
    }
  }

  private def toTuiState(globalState: GlobalState): typr.cli.tui.TuiState = {
    typr.cli.tui.TuiState(
      config = globalState.config,
      configPath = globalState.configPath,
      currentScreen = typr.cli.tui.AppScreen.MainMenu(0),
      previousScreen = None,
      navigation = globalState.navigation,
      sourceStatuses = globalState.sourceStatuses,
      hasUnsavedChanges = globalState.hasUnsavedChanges,
      statusMessage = globalState.statusMessage,
      shouldExit = globalState.shouldExit,
      hoverPosition = None,
      terminalSize = globalState.terminalSize
    )
  }
}
