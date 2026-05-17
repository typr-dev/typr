package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.router.RouterApi
import jatatui.core.style.{Color, Style}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import jatatui.components.chrome.ScreenFrame

/** Stand-in screen for menu destinations that haven't been ported yet. Esc / b pops the router. */
object Placeholder {

  def element(label: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val body = column(
        fill(1, empty()),
        length(1, text(s"  $label — not yet ported", Style.empty.withFg(Color.YELLOW))),
        length(1, text("  press esc or b to return", Style.empty.withFg(Color.DARK_GRAY))),
        fill(1, empty())
      )

      ScreenFrame.of("menu", back, body)
    }
}
