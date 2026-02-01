package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.widgets.*
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.SplashState
import typr.cli.tui.TuiColors

/** React-based rendering for SplashScreen with local state for animation. */
object SplashScreenReact {

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

  /** Props for SplashScreen */
  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      splashState: SplashState
  )

  /** Main component - splash state (frame, progress) comes from parent via props */
  def component(props: Props): Component = {
    Component.named("SplashScreen") { (ctx, area) =>
      val buf = ctx.buffer
      val progress = props.splashState.progress
      val frame = props.splashState.frame

      ctx.frame.renderWidget(ClearWidget, area)

      val logoHeight = logo.length
      val logoWidth = logo.headOption.map(_.length).getOrElse(0)

      val startY = math.max(0, (area.height.toInt - logoHeight - 6) / 2)
      val startX = math.max(0, (area.width.toInt - logoWidth) / 2)

      val pulsePhase = math.sin(frame * 0.08)
      val pulseIntensity = (pulsePhase + 1) / 2

      val pulseR = (139 + (28 * pulseIntensity)).toInt
      val pulseG = (92 + (47 * pulseIntensity)).toInt
      val pulseB = (246 + (4 * pulseIntensity)).toInt
      val logoColor = Color.Rgb(pulseR, pulseG, pulseB)

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

      if (progress >= 0.4) {
        val versionY = startY + logoHeight + 4
        val versionX = (area.width.toInt - version.length) / 2

        if (versionY >= 0 && versionY < area.height.toInt) {
          buf.setString(versionX, versionY, version, Style(fg = Some(TuiColors.gray500)))
        }
      }

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

        val visible = (frame / 20) % 2 == 0
        if (visible && bottomY >= 0 && bottomY < area.height.toInt) {
          buf.setString(pressX, bottomY, pressText, Style(fg = Some(TuiColors.accent)))
        }

        ctx.onClick(area)(props.callbacks.navigateTo(Location.MainMenu))
      }
    }
  }
}
