package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.BrowserSourcePickerState
import typr.cli.tui.Location
import typr.cli.tui.SchemaBrowserLevel
import typr.cli.tui.SchemaBrowserState
import typr.cli.tui.SpecBrowserState
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.stats

object BrowserSourcePicker {

  /** Unified type for browsable sources */
  sealed trait BrowsableSource
  object BrowsableSource {
    case class Database(name: SourceName, ready: SourceStatus.Ready) extends BrowsableSource
    case class Spec(name: SourceName, ready: SourceStatus.ReadySpec) extends BrowsableSource
  }

  def handleKey(state: TuiState, picker: AppScreen.BrowserSourcePicker, keyCode: KeyCode): TuiState = {
    val browsableSources = getBrowsableSources(state)

    keyCode match {
      case _: KeyCode.Up =>
        val newIdx = math.max(0, picker.state.selectedIndex - 1)
        state.copy(currentScreen = AppScreen.BrowserSourcePicker(picker.state.copy(selectedIndex = newIdx)))

      case _: KeyCode.Down =>
        val maxIdx = math.max(0, browsableSources.length - 1)
        val newIdx = math.min(maxIdx, picker.state.selectedIndex + 1)
        state.copy(currentScreen = AppScreen.BrowserSourcePicker(picker.state.copy(selectedIndex = newIdx)))

      case _: KeyCode.Enter =>
        if (browsableSources.nonEmpty && picker.state.selectedIndex < browsableSources.length) {
          browsableSources(picker.state.selectedIndex) match {
            case BrowsableSource.Database(sourceName, ready) =>
              val browserState = SchemaBrowserState.fromMetaDb(sourceName, ready.metaDb)
              val initialSchema = if (browserState.level == SchemaBrowserLevel.Tables) browserState.schemas.headOption else None
              Navigator.navigateTo(
                state,
                Location.SchemaBrowser(sourceName, browserState.level, initialSchema, None),
                AppScreen.SchemaBrowser(browserState)
              )
            case BrowsableSource.Spec(sourceName, ready) =>
              val browserState = SpecBrowserState.fromSpec(sourceName, ready.spec, ready.sourceType)
              Navigator.navigateTo(
                state,
                Location.SpecBrowser(sourceName),
                AppScreen.SpecBrowser(browserState)
              )
          }
        } else {
          state
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case _ => state
    }
  }

  def handleMouse(state: TuiState, picker: AppScreen.BrowserSourcePicker, col: Int, row: Int): TuiState = {
    val browsableSources = getBrowsableSources(state)
    val listStartY = 4

    if (browsableSources.nonEmpty && row >= listStartY) {
      val clickedIndex = row - listStartY
      if (clickedIndex >= 0 && clickedIndex < browsableSources.length) {
        val newState = state.copy(currentScreen = AppScreen.BrowserSourcePicker(picker.state.copy(selectedIndex = clickedIndex)))
        handleKey(newState, AppScreen.BrowserSourcePicker(picker.state.copy(selectedIndex = clickedIndex)), new tui.crossterm.KeyCode.Enter())
      } else {
        state
      }
    } else {
      state
    }
  }

  def tick(state: TuiState, picker: AppScreen.BrowserSourcePicker): TuiState = state

  def render(f: Frame, state: TuiState, picker: AppScreen.BrowserSourcePicker): Unit = {
    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(5))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, chunks(0), "Select Source", isBackHovered)

    val area = chunks(1)
    val buf = f.buffer

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val browsableSources = getBrowsableSources(state)
    val loadingSources = getLoadingSources(state)

    var y = innerArea.y

    if (browsableSources.isEmpty) {
      buf.setString(innerArea.x, y, "No sources with loaded schema.", Style(fg = Some(Color.DarkGray)))
      y += 1
      buf.setString(innerArea.x, y, "Wait for sources to finish loading...", Style(fg = Some(Color.DarkGray)))
      y += 2

      if (loadingSources.nonEmpty) {
        buf.setString(innerArea.x, y, "Loading:", Style(fg = Some(Color.Yellow)))
        y += 1
        loadingSources.take(5).foreach { case (name, _) =>
          buf.setString(innerArea.x + 2, y, s"${name.value}...", Style(fg = Some(Color.DarkGray)))
          y += 1
        }
      }
    } else {
      browsableSources.zipWithIndex.foreach { case (source, idx) =>
        if (y < innerArea.y + innerArea.height.toInt - 2) {
          val isSelected = idx == picker.state.selectedIndex

          val style = if (isSelected) {
            Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          } else {
            Style(fg = Some(Color.White))
          }

          val prefix = if (isSelected) "> " else "  "

          source match {
            case BrowsableSource.Database(sourceName, ready) =>
              val s = ready.metaDb.stats
              val info = s"${s.tableCount} tables, ${s.viewCount} views, ${s.enumCount} enums"
              val icon = "●"
              buf.setString(innerArea.x, y, prefix, style)
              buf.setString(innerArea.x + 2, y, s"$icon ", Style(fg = Some(Color.Green)))
              buf.setString(innerArea.x + 4, y, sourceName.value, style)
              buf.setString(innerArea.x + 4 + sourceName.value.length + 1, y, s"($info)", Style(fg = Some(Color.DarkGray)))

            case BrowsableSource.Spec(sourceName, ready) =>
              import typr.cli.tui.stats
              val s = ready.spec.stats
              val typeLabel = ready.sourceType match {
                case typr.cli.tui.SpecSourceType.OpenApi    => "OpenAPI"
                case typr.cli.tui.SpecSourceType.JsonSchema => "JSON Schema"
              }
              val info = s"$typeLabel - ${s.modelCount} models, ${s.apiCount} endpoints"
              val icon = "◆"
              buf.setString(innerArea.x, y, prefix, style)
              buf.setString(innerArea.x + 2, y, s"$icon ", Style(fg = Some(Color.Cyan)))
              buf.setString(innerArea.x + 4, y, sourceName.value, style)
              buf.setString(innerArea.x + 4 + sourceName.value.length + 1, y, s"($info)", Style(fg = Some(Color.DarkGray)))
          }
          y += 1
        }
      }

      if (loadingSources.nonEmpty) {
        y += 1
        buf.setString(innerArea.x, y, s"${loadingSources.length} source(s) still loading...", Style(fg = Some(Color.DarkGray)))
      }
    }

    val hintY = area.y + area.height.toInt - 2
    buf.setString(area.x + 2, hintY, "[Enter] Browse  [Esc] Back  [q] Quit", Style(fg = Some(Color.DarkGray)))
  }

  def getBrowsableSources(state: TuiState): List[BrowsableSource] = {
    val dbSources = state.sourceStatuses.toList.collect { case (name, ready: SourceStatus.Ready) =>
      BrowsableSource.Database(name, ready)
    }
    val specSources = state.sourceStatuses.toList.collect { case (name, ready: SourceStatus.ReadySpec) =>
      BrowsableSource.Spec(name, ready)
    }
    (dbSources ++ specSources).sortBy {
      case BrowsableSource.Database(name, _) => name.value
      case BrowsableSource.Spec(name, _)     => name.value
    }
  }

  def getLoadingSources(state: TuiState): List[(SourceName, SourceStatus)] = {
    state.sourceStatuses.toList
      .filter {
        case (_, _: SourceStatus.LoadingMetaDb)  => true
        case (_, _: SourceStatus.LoadingSpec)    => true
        case (_, _: SourceStatus.RunningSqlGlot) => true
        case (_, SourceStatus.Pending)           => true
        case _                                   => false
      }
      .sortBy(_._1.value)
  }
}
