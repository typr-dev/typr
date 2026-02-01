package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.SplashState
import typr.cli.tui.TuiColors
import typr.cli.tui.TuiState

object SplashScreen {

  // ASCII art for "typr" - large block letters
  private val logo: List[String] = List(
    "  ████████╗██╗   ██╗██████╗ ██████╗  ",
    "  ╚══██╔══╝╚██╗ ██╔╝██╔══██╗██╔══██╗ ",
    "     ██║    ╚████╔╝ ██████╔╝██████╔╝ ",
    "     ██║     ╚██╔╝  ██╔═══╝ ██╔══██╗ ",
    "     ██║      ██║   ██║     ██║  ██║ ",
    "     ╚═╝      ╚═╝   ╚═╝     ╚═╝  ╚═╝ "
  )

  private val tagline = "Seal Your System's Boundaries"
  private val version = "v0.1.0"

  def handleKey(state: TuiState, splash: AppScreen.Splash, keyCode: KeyCode): TuiState = {
    state.copy(currentScreen = AppScreen.MainMenu(selectedIndex = 0))
  }

  def tick(state: TuiState, splash: AppScreen.Splash): TuiState = {
    val newSplashState = splash.state.tick
    if (newSplashState.isDone) {
      state.copy(currentScreen = AppScreen.MainMenu(selectedIndex = 0))
    } else {
      state.copy(currentScreen = AppScreen.Splash(newSplashState))
    }
  }

  def render(f: Frame, state: TuiState, splash: AppScreen.Splash): Unit = {
    val area = f.size
    val buf = f.buffer
    val progress = splash.state.progress
    val frame = splash.state.frame

    f.renderWidget(ClearWidget, area)

    val logoHeight = logo.length
    val logoWidth = logo.headOption.map(_.length).getOrElse(0)

    val startY = math.max(0, (area.height.toInt - logoHeight - 6) / 2)
    val startX = math.max(0, (area.width.toInt - logoWidth) / 2)

    // Smooth pulsating effect - oscillates between primary and primaryLight
    val pulsePhase = math.sin(frame * 0.08) // Slow, smooth pulse
    val pulseIntensity = (pulsePhase + 1) / 2 // Normalize to 0-1

    // Interpolate between primary (139,92,246) and primaryLight (167,139,250)
    val pulseR = (139 + (28 * pulseIntensity)).toInt
    val pulseG = (92 + (47 * pulseIntensity)).toInt
    val pulseB = (246 + (4 * pulseIntensity)).toInt
    val logoColor = Color.Rgb(pulseR, pulseG, pulseB)

    // Render logo with fade-in and pulsating color
    logo.zipWithIndex.foreach { case (line, lineIdx) =>
      val y = startY + lineIdx
      if (y >= 0 && y < area.height.toInt) {
        val visibleChars = if (progress < 0.2) {
          val charProgress = progress / 0.2
          (line.length * charProgress).toInt
        } else {
          line.length
        }

        val visibleLine = line.take(visibleChars)
        val style = Style(fg = Some(logoColor), addModifier = Modifier.BOLD)
        buf.setString(startX, y, visibleLine, style)
      }
    }

    // Render tagline with fade-in
    if (progress >= 0.25) {
      val taglineY = startY + logoHeight + 2
      val taglineX = (area.width.toInt - tagline.length) / 2

      val taglineOpacity = math.min(1.0, (progress - 0.25) / 0.15)
      val r = (167 * taglineOpacity).toInt
      val g = (139 * taglineOpacity).toInt
      val b = (250 * taglineOpacity).toInt
      val taglineStyle = Style(fg = Some(Color.Rgb(r, g, b)))

      if (taglineY >= 0 && taglineY < area.height.toInt) {
        buf.setString(taglineX, taglineY, tagline, taglineStyle)
      }
    }

    // Render version
    if (progress >= 0.4) {
      val versionY = startY + logoHeight + 4
      val versionX = (area.width.toInt - version.length) / 2

      if (versionY >= 0 && versionY < area.height.toInt) {
        buf.setString(versionX, versionY, version, Style(fg = Some(TuiColors.gray500)))
      }
    }

    // Loading indicator or press any key
    val bottomY = startY + logoHeight + 6
    if (progress < 0.9) {
      val numDots = ((frame / 6) % 4)
      val dots = "." * numDots + " " * (3 - numDots)
      val loadingText = s"Loading$dots"
      val loadingX = (area.width.toInt - loadingText.length) / 2

      if (bottomY >= 0 && bottomY < area.height.toInt) {
        buf.setString(loadingX, bottomY, loadingText, Style(fg = Some(TuiColors.gray400)))
      }
    } else {
      val pressText = "Press any key to continue"
      val pressX = (area.width.toInt - pressText.length) / 2

      // Subtle blink
      val visible = (frame / 20) % 2 == 0
      if (visible && bottomY >= 0 && bottomY < area.height.toInt) {
        buf.setString(pressX, bottomY, pressText, Style(fg = Some(TuiColors.accent)))
      }
    }
  }
}
