package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.crossterm.MouseEvent
import tui.crossterm.MouseEventKind
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.FocusableId
import typr.cli.tui.SlidingDemoState
import typr.cli.tui.TuiState
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.widgets.Button
import typr.cli.tui.widgets.ButtonStyle

object SlidingDemo {

  private val stripWidth = 8

  case class PaneButton(paneIndex: Int, targetPane: Int, button: Button)

  def handleKey(state: TuiState, demo: AppScreen.SlidingDemo, keyCode: KeyCode): TuiState = {
    val demoState = demo.state

    keyCode match {
      case _: KeyCode.Tab =>
        val newState = demoState.focusNext
        state.copy(currentScreen = demo.copy(state = newState))

      case _: KeyCode.BackTab =>
        val newState = demoState.focusPrev
        state.copy(currentScreen = demo.copy(state = newState))

      case _: KeyCode.Enter =>
        // Get buttons for current pane from last render
        val currentPaneButtons = lastRenderedButtons.filter(_.paneIndex == demoState.currentPaneIndex)
        // Use focused button by index, or fall back to hover
        val targetButton = if (currentPaneButtons.nonEmpty && demoState.focusedButtonIndex < currentPaneButtons.length) {
          Some(currentPaneButtons(demoState.focusedButtonIndex))
        } else {
          // Fall back to hover
          demoState.hoverCol.flatMap { col =>
            demoState.hoverRow.flatMap { row =>
              lastRenderedButtons.find(_.button.contains(col, row))
            }
          }
        }

        targetButton match {
          case Some(paneBtn) =>
            val newState = demoState.goToPane(paneBtn.targetPane)
            state.copy(currentScreen = demo.copy(state = newState))
          case None =>
            state
        }

      case _: KeyCode.Esc =>
        // Go back one pane, or exit demo if on first pane
        if (demoState.currentPaneIndex > 0) {
          val newState = demoState.goToPane(demoState.currentPaneIndex - 1)
          state.copy(currentScreen = demo.copy(state = newState))
        } else {
          Navigator.goBack(state)
        }

      case c: KeyCode.Char if c.c() == 'q' =>
        // q always exits the demo entirely
        Navigator.goBack(state)

      case _ => state
    }
  }

  def handleMouse(state: TuiState, demo: AppScreen.SlidingDemo, event: MouseEvent): TuiState = {
    val demoState = demo.state
    val col = event.column.toInt
    val row = event.row.toInt

    event.kind match {
      case _: MouseEventKind.Moved | _: MouseEventKind.Drag =>
        val newState = demoState.updateHover(col, row)
        state.copy(currentScreen = demo.copy(state = newState))

      case _: MouseEventKind.Down =>
        lastRenderedButtons.find(_.button.contains(col, row)) match {
          case Some(paneBtn) =>
            val newState = demoState.goToPane(paneBtn.targetPane).updateHover(col, row)
            state.copy(currentScreen = demo.copy(state = newState))
          case _ =>
            state
        }

      case _ => state
    }
  }

  @volatile private var lastRenderedButtons: List[PaneButton] = Nil

  def tick(state: TuiState, demo: AppScreen.SlidingDemo): TuiState = {
    val demoState = demo.state
    if (demoState.animating) {
      val newState = demoState.tick
      state.copy(currentScreen = demo.copy(state = newState))
    } else {
      state
    }
  }

  def render(f: Frame, state: TuiState, demo: AppScreen.SlidingDemo): Unit = {
    val demoState = demo.state
    val area = f.size
    val buf = f.buffer

    // Skip rendering if area is too small
    if (area.width < 10 || area.height < 5) return

    val totalWidth = area.width.toInt
    val offset = demoState.offset

    val visiblePaneIndex = offset.toInt
    val fraction = offset - visiblePaneIndex

    val buttonsBuilder = List.newBuilder[PaneButton]

    for (i <- 0 to math.min(visiblePaneIndex + 1, demoState.panes.length - 1)) {
      val paneX: Int = if (i < visiblePaneIndex) {
        i * stripWidth
      } else if (i == visiblePaneIndex) {
        visiblePaneIndex * stripWidth
      } else {
        val baseX = visiblePaneIndex * stripWidth
        val availableWidth = totalWidth - baseX
        baseX + ((1.0 - fraction) * availableWidth).toInt
      }

      val paneW: Int = if (i < visiblePaneIndex) {
        stripWidth
      } else if (i == visiblePaneIndex) {
        val baseX = visiblePaneIndex * stripWidth
        val fullWidth = totalWidth - baseX
        ((1.0 - fraction) * fullWidth).toInt
      } else {
        totalWidth - paneX
      }

      if (paneW >= 3 && paneX >= 0 && paneX < totalWidth) {
        val clampedW = math.min(paneW, totalWidth - paneX)
        val paneArea = Rect(
          x = (area.x + paneX).toShort,
          y = area.y,
          width = clampedW.toShort,
          height = area.height
        )
        val faded = i < visiblePaneIndex || (i == visiblePaneIndex && fraction > 0.5)
        // Only show focus on the current pane (not faded ones)
        val focusIdx = if (i == demoState.currentPaneIndex && !faded) Some(demoState.focusedButtonIndex) else None
        val paneButtons = renderPane(f, paneArea, demoState.panes(i), i, faded, demoState.hoverCol, demoState.hoverRow, focusIdx)
        buttonsBuilder ++= paneButtons
      }
    }

    lastRenderedButtons = buttonsBuilder.result()

    val hintY = area.y + area.height.toInt - 1
    val hintX = area.x + 2
    val maxHintLen = math.max(0, (area.x + area.width - hintX).toInt)
    if (maxHintLen > 0 && hintY >= area.y && hintY < area.y + area.height) {
      val escHint = if (demoState.currentPaneIndex > 0) "[Esc] Prev pane" else "[Esc] Exit"
      val hintText = s"[Tab] Focus  [Enter] Press  $escHint  [q] Exit  |  Offset: ${f"${demoState.offset}%.2f"}"
      buf.setString(hintX, hintY, hintText.take(maxHintLen), Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderPane(
      f: Frame,
      area: Rect,
      title: String,
      index: Int,
      faded: Boolean,
      hoverCol: Option[Int],
      hoverRow: Option[Int],
      focusIdx: Option[Int]
  ): List[PaneButton] = {
    val paneNames = List("Pane A", "Pane B", "Pane C", "Pane D")
    val colors = List(Color.Blue, Color.Green, Color.Magenta, Color.Cyan)
    val baseColor = colors(index % colors.length)
    val color = if (faded) Color.DarkGray else baseColor

    val titleStyle = if (faded) Style(fg = Some(color)) else Style(fg = Some(color), addModifier = Modifier.BOLD)
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(title, titleStyle))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(color))
    )
    f.renderWidget(block, area)

    val buttons = List.newBuilder[PaneButton]
    val buf = f.buffer
    val maxX = area.x + area.width
    val maxY = area.y + area.height

    // Helper to safely set string within bounds
    def safeSetString(x: Int, y: Int, text: String, style: Style): Unit = {
      if (x >= 0 && x < maxX && y >= area.y && y < maxY) {
        val availWidth = (maxX - x).toInt
        if (availWidth > 0) {
          buf.setString(x, y, text.take(availWidth), style)
        }
      }
    }

    if (area.width > 6 && area.height > 4) {
      val innerX = area.x + 2
      val innerY = area.y + 2
      val textColor = if (faded) Color.DarkGray else Color.White

      safeSetString(innerX, innerY, title, Style(fg = Some(textColor)))

      if (area.width > 15 && !faded && area.height > 8) {
        safeSetString(innerX, innerY + 2, s"Index: $index", Style(fg = Some(Color.Gray)))

        val buttonY = innerY + 4
        var buttonX = innerX
        var buttonIdx = 0

        if (index > 0 && buttonX < maxX && buttonY < maxY) {
          val prevPane = paneNames(index - 1)
          val btnId = FocusableId.Button(s"pane-$index-prev")
          val prevButton = Button(
            label = s"< $prevPane",
            x = buttonX,
            y = buttonY,
            id = btnId,
            style = ButtonStyle.default
          )
          val focusedId = focusIdx.filter(_ == buttonIdx).map(_ => btnId)
          prevButton.renderWithFocus(buf, hoverCol, hoverRow, focusedId)
          buttons += PaneButton(index, index - 1, prevButton)
          buttonX += prevButton.width + 2
          buttonIdx += 1
        }

        if (index < 3 && buttonX < maxX && buttonY < maxY) {
          val nextPane = paneNames(index + 1)
          val btnId = FocusableId.Button(s"pane-$index-next")
          val nextButton = Button(
            label = s"$nextPane >",
            x = buttonX,
            y = buttonY,
            id = btnId,
            style = ButtonStyle.primary
          )
          val focusedId = focusIdx.filter(_ == buttonIdx).map(_ => btnId)
          nextButton.renderWithFocus(buf, hoverCol, hoverRow, focusedId)
          buttons += PaneButton(index, index + 1, nextButton)
        }

        for (row <- 0 until math.min(6, area.height.toInt - 9)) {
          val y = innerY + 6 + row
          if (y < maxY.toInt - 2) {
            safeSetString(innerX, y, s"Row $row", Style(fg = Some(Color.DarkGray)))
          }
        }
      } else if (area.width > 15 && area.height > 6) {
        safeSetString(innerX, innerY + 2, s"Index: $index", Style(fg = Some(Color.DarkGray)))
        for (row <- 0 until math.min(6, area.height.toInt - 7)) {
          val y = innerY + 4 + row
          if (y < maxY.toInt - 2) {
            safeSetString(innerX, y, s"Row $row", Style(fg = Some(Color.DarkGray)))
          }
        }
      }
    }

    buttons.result()
  }
}
