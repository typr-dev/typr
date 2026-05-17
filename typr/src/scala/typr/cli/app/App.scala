package typr.cli.app

import cats.effect.{ExitCode, IO}
import jatatui.crossterm.Jatatui
import jatatui.react.{Element, KeyEvent as JKeyEvent, MouseEvent as JMouseEvent, Renderer}
import tui.crossterm.{Command, CrosstermJni, Duration, Event, KeyCode, KeyEventKind, MouseEventKind}
import typr.cli.config.{ConfigParser, EnvSubstitution}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

/** New TUI shell built on jatatui-react. Replaces the legacy `typr.cli.tui` package screen by screen — see [[screens]]. Bootstrap owns the loop directly (not [[jatatui.react.ReactApp.run]]) because
  * we need a quit flag the app can flip without relying on Esc-to-quit.
  */
object App {

  /** Entry point invoked by `typr tui`. Loads typr.yaml, then runs the React loop. Bail with an error if the config doesn't exist or fails to parse — same behaviour as the legacy `Interactive.run`,
    * so the user gets a useful message instead of an empty screen.
    */
  def run(configPath: Path): IO[ExitCode] =
    IO.blocking {
      // Expiry check happens before any terminal setup — we want a plain stderr block, not a
      // half-initialised TUI. Acceptance is enforced inside the TUI itself via [[BetaNotice]].
      if (typr.cli.beta.BetaGate.isExpired()) {
        System.err.println(typr.cli.beta.BetaGate.expiredMessage)
        ExitCode(78)
      } else {
        val absolutePath = configPath.toAbsolutePath
        if (!Files.exists(absolutePath)) {
          System.err.println(s"Error: Config file not found: $absolutePath")
          System.err.println(s"Hint: run `typr init` to create one in the current directory.")
          ExitCode.Error
        } else {
          val yaml = Files.readString(absolutePath)
          val substituted = EnvSubstitution.substitute(yaml).getOrElse(yaml)
          ConfigParser.parse(substituted) match {
            case Right(config) =>
              val running = new java.util.concurrent.atomic.AtomicBoolean(true)
              val root = Shell.element(config, absolutePath, () => running.set(false))
              loop(root, running)
              ExitCode.Success
            case Left(err) =>
              System.err.println(s"Error: Failed to parse config: $err")
              ExitCode.Error
          }
        }
      }
    }

  /** Create an empty typr.yaml at `configPath` (if missing) and drop into the editor. The skeleton is intentionally minimal — empty `sources` / `outputs` / `types` maps — so the editor's add-source /
    * add-output / add-type wizards have something to extend.
    */
  def init(configPath: Path): IO[ExitCode] =
    IO.blocking {
      val absolutePath = configPath.toAbsolutePath
      if (Files.exists(absolutePath))
        System.out.println(s"$absolutePath already exists; opening it.")
      else
        Files.write(
          absolutePath,
          InitialYaml.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE
        )
      ()
    }.flatMap(_ => run(configPath))

  private val InitialYaml: String =
    """# typr config — see https://github.com/oyvindberg/typr for docs
      |sources: {}
      |outputs: {}
      |types: {}
      |""".stripMargin

  private def loop(root: Element, running: java.util.concurrent.atomic.AtomicBoolean): Unit =
    Jatatui.runIo { terminal =>
      val jni = new CrosstermJni
      jni.execute(new Command.EnableMouseCapture)
      try {
        val renderer = new Renderer
        // 100ms — same cadence as ReactApp's internal loop. Background work (e.g. source loaders)
        // can call `renderer.requestRerender()` to wake the loop sooner.
        val pollTimeout = new Duration(0L, 100_000_000)
        while (running.get()) {
          if (renderer.takeDirty()) {
            val _ = terminal.draw(jframe => renderer.render(jframe, root))
          }
          if (jni.poll(pollTimeout)) jni.read() match {
            case k: Event.Key if k.keyEvent.kind == KeyEventKind.Press =>
              val code = k.keyEvent.code
              val mods = k.keyEvent.modifiers
              code match {
                case _: KeyCode.Tab     => renderer.tab()
                case _: KeyCode.BackTab => renderer.shiftTab()
                case _                  => val _ = renderer.dispatchKey(new JKeyEvent(code, mods))
              }
            case m: Event.Mouse =>
              val me = m.mouseEvent
              val kind = me.kind match {
                case _: MouseEventKind.Down       => JMouseEvent.Kind.DOWN
                case _: MouseEventKind.Up         => JMouseEvent.Kind.UP
                case _: MouseEventKind.Drag       => JMouseEvent.Kind.DRAG
                case _: MouseEventKind.Moved      => JMouseEvent.Kind.MOVE
                case _: MouseEventKind.ScrollUp   => JMouseEvent.Kind.SCROLL_UP
                case _: MouseEventKind.ScrollDown => JMouseEvent.Kind.SCROLL_DOWN
                case _                            => null
              }
              if (kind != null) {
                val _ = renderer.dispatchMouse(new JMouseEvent(me.column, me.row, me.modifiers, kind))
              }
            case _: Event.Resize => renderer.requestRerender()
            case _               => ()
          }
        }
      } finally
        try jni.execute(new Command.DisableMouseCapture)
        catch case _: RuntimeException => () // best-effort restore
    }
}
