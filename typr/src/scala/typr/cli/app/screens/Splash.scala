package typr.cli.app.screens

import jatatui.components.router.{RouterApi, Screen as RouterScreen}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import typr.cli.beta.{BetaGate, BetaTerms}

/** Animated logo + auto-dismiss. Always reached after [[BetaNotice]] (initial mode) has run, so the 2-second auto-dismiss only kicks in for accepted users — first-run users see the gate modal until
  * they accept, then the splash, then the main menu.
  *
  * Bottom chip shows the closed-beta state. During the 14-day warning window it goes yellow and counts down. `t` opens [[BetaNotice]] in read-only mode for users who want to reread.
  */
object Splash {

  private val logo: List[String] = List(
    "  ████████╗██╗   ██╗██████╗ ██████╗  ",
    "  ╚══██╔══╝╚██╗ ██╔╝██╔══██╗██╔══██╗ ",
    "     ██║    ╚████╔╝ ██████╔╝██████╔╝ ",
    "     ██║     ╚██╔╝  ██╔═══╝ ██╔══██╗ ",
    "     ██║      ██║   ██║     ██║  ██║ ",
    "     ╚═╝      ╚═╝   ╚═╝     ╚═╝  ╚═╝ "
  )

  private val tagline = "Seal Your System's Boundaries"
  private val hint = "press any key to continue"

  private val logoStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val taglineStyle: Style = Style.empty.withFg(Color.WHITE)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val warningStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)

  private val AutoDismissMs: Long = 2000L

  /** `binaryVersion` is threaded through so the `t`-key BetaNotice pop has a value to pass into [[BetaGate.markAccepted]] if the user happens to accept from there (read-only mode doesn't re-accept,
    * but the screen factory still needs the param). Defaulting at this layer is fine — the value is informational, not load-bearing.
    */
  def element(binaryVersion: String = "unknown"): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val advance = () => router.replace(RouterScreen.of("main menu", MainMenu.element))

      ctx.useTimeout(AutoDismissMs, () => advance())
      ctx.onGlobalKey(ANY_KEY, () => advance())
      ctx.onClick(() => advance())

      // 't' opens the terms screen in read-only mode (push so esc returns here).
      ctx.onGlobalKey(new KeyCode.Char('t'), () => router.push(RouterScreen.of("terms", BetaNotice.element(BetaNotice.Mode.ReadOnly, binaryVersion))))

      val (chipText, chipStyle) =
        if (BetaGate.isInWarningWindow()) {
          val d = BetaGate.daysUntilExpiry()
          val units = if (d == 1) "day" else "days"
          (s"closed beta · expires in $d $units — typr.dev/get · press t for terms", warningStyle)
        } else
          (s"closed beta · build expires ${BetaTerms.expiresOn} · press t for terms", hintStyle)

      val logoLines: Array[Element] = logo.map(l => length(1, text(l, logoStyle))).toArray
      val body = column(
        (length(2, empty())
          :: logoLines.toList
          ::: List(
            length(1, empty()),
            length(1, text(tagline, taglineStyle)),
            length(1, empty()),
            length(1, text(hint, hintStyle)),
            fill(1, empty()),
            length(1, text(s"  $chipText", chipStyle))
          ))*
      )

      row(fill(1, empty()), length(80, body), fill(1, empty()))
    }
}
