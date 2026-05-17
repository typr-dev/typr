package typr.cli.app.screens

import jatatui.components.button.Button
import jatatui.components.router.{RouterApi, Screen as RouterScreen}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import typr.cli.app.AppApi
import typr.cli.beta.{BetaGate, BetaTerms}

/** Full-screen acceptance modal — shown on first run, after a terms-version bump, or when the user presses `t` on the Splash to re-read.
  *
  * Two modes, controlled by [[Mode]]:
  *   - [[Mode.Initial]]: gates the rest of the app. Accept writes the file and replaces this screen with [[Splash]]; Quit calls `app.quit()`.
  *   - [[Mode.ReadOnly]]: the user already accepted and is just rereading. Accept replaced by a `[ close ]` button; Quit removed. Esc / b / close all `router.pop()`.
  */
object BetaNotice {

  enum Mode {
    case Initial // gating; not yet accepted
    case ReadOnly // already accepted; user opened from Splash
  }

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val termsStyle: Style = Style.empty.withFg(Color.WHITE)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val warningStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)

  def element(mode: Mode, binaryVersion: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)

      def accept(): Unit = {
        try BetaGate.markAccepted(binaryVersion)
        catch case _: Throwable => () // best-effort; failure is rare and not blocking
        router.replace(RouterScreen.of("splash", Splash.element(binaryVersion)))
      }

      def close(): Unit = router.pop()

      mode match {
        case Mode.Initial =>
          ctx.onGlobalKey(new KeyCode.Esc, () => app.quit())
        case Mode.ReadOnly =>
          ctx.onGlobalKey(new KeyCode.Esc, () => close())
          ctx.onGlobalKey(new KeyCode.Char('b'), () => close())
      }

      val title = length(1, text("  Typr Closed Beta", titleStyle))
      val intro = length(1, text("  Before you start, please read these terms:", hintStyle))

      val expiryNotice: List[Element] =
        if (BetaGate.isInWarningWindow()) {
          val d = BetaGate.daysUntilExpiry()
          val s = if (d == 1) "day" else "days"
          List(
            length(1, empty()),
            length(1, text(s"  This build expires in $d $s — keep an install fresh at typr.dev.", warningStyle))
          )
        } else Nil

      val termsLines: List[Element] =
        BetaTerms.displayText.linesIterator
          .drop(1)
          .toList // skip the duplicated title line
          .map(l => length(1, text(s"  $l", termsStyle)))

      val buttons: Element = mode match {
        case Mode.Initial =>
          jatatui.react.Components.row(
            fill(1, empty()),
            length(34, Button.of("I understand and accept", "beta-accept", true, () => accept())),
            length(2, empty()),
            length(12, Button.of("Quit", "beta-quit", false, () => app.quit())),
            fill(1, empty())
          )
        case Mode.ReadOnly =>
          jatatui.react.Components.row(
            fill(1, empty()),
            length(14, Button.of("close", "beta-close", true, () => close())),
            fill(1, empty())
          )
      }

      val body = column(
        (length(1, empty()) ::
          title ::
          length(1, empty()) ::
          intro ::
          length(1, empty()) ::
          termsLines :::
          expiryNotice :::
          List(
            length(1, empty()),
            length(3, buttons),
            fill(1, empty())
          ))*
      )

      // Center horizontally — terms wrap at ~70 cols, so a 78-col body is comfortable.
      jatatui.react.Components.row(fill(1, empty()), length(78, body), fill(1, empty()))
    }
}
