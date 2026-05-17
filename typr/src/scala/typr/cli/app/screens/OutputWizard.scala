package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
import jatatui.components.chrome.ScreenFrame
import jatatui.components.dropdown.{Dropdown, DropdownProps}
import jatatui.components.router.RouterApi
import jatatui.components.textinput.{TextInputComponent, TextInputProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import typr.cli.app.AppApi
import typr.config.generated.{Output, StringOrArrayString}

import scala.jdk.CollectionConverters.*

/** New-output wizard. Required: name, language, path, package. Optional: sources / db_lib / json / framework / effect_type (defaults to empty). Save validates name uniqueness, writes typr.yaml, pops
  * back.
  */
object OutputWizard {

  private val languages: List[String] = List("scala", "java", "kotlin")

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val valueStyle: Style = Style.empty.withFg(Color.WHITE)
  private val focusedValueStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val cursorStyle: Style = Style.empty.withAddModifier(Modifier.REVERSED)

  sealed trait Status
  object Status {
    case object Idle extends Status
    final case class Failed(error: String) extends Status
  }

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())

      val name = ctx.useState(() => "")
      val langIdx = ctx.useState(() => 0)
      val path = ctx.useState(() => "")
      val pkg = ctx.useState(() => "")
      val sources = ctx.useState(() => "")
      val status = ctx.useState(() => Status.Idle: Status)

      def save(): Unit = {
        val n = name.get.trim
        if (n.isEmpty)
          status.set(Status.Failed("name required"))
        else if (path.get.trim.isEmpty)
          status.set(Status.Failed("path required"))
        else if (pkg.get.trim.isEmpty)
          status.set(Status.Failed("package required"))
        else if (app.config.outputs.getOrElse(Map.empty).contains(n))
          status.set(Status.Failed(s"output '$n' already exists"))
        else {
          val newOutput = Output(
            bridge = None,
            db_lib = None,
            effect_type = None,
            framework = None,
            json = None,
            language = languages(langIdx.get),
            matchers = None,
            `package` = pkg.get.trim,
            path = path.get.trim,
            scala = None,
            sources = Option(sources.get.trim).filter(_.nonEmpty).map(StringOrArrayString.apply)
          )
          val newOutputs = app.config.outputs.getOrElse(Map.empty) + (n -> newOutput)
          app.updateConfig(c => c.copy(outputs = Some(newOutputs))) match {
            case Right(_) => router.pop()
            case Left(e)  => status.set(Status.Failed(Option(e.getMessage).getOrElse(e.toString)))
          }
        }
      }

      def textRow(value: String, label: String, focusId: String, autoFocus: Boolean, onChange: String => Unit) =
        length(
          3,
          TextInputComponent.of(
            TextInputProps
              .of(value, v => { onChange(v); status.set(Status.Idle) })
              .withTitle(label)
              .withFocusId(focusId)
              .withAutoFocus(autoFocus)
              .withStyle(valueStyle)
              .withFocusedStyle(focusedValueStyle)
              .withCursorStyle(cursorStyle)
          )
        )

      val rows = List(
        textRow(name.get, "name", "wiz:name", autoFocus = true, name.set),
        length(
          3,
          Dropdown.of(
            DropdownProps
              .of[String](
                "language",
                languages.asJava,
                (s: String) => s,
                langIdx.get,
                (idx: Int) => langIdx.set(idx)
              )
              .withFocusId("wiz:lang")
              .withStyle(valueStyle)
              .withFocusedStyle(focusedValueStyle)
          )
        ),
        textRow(path.get, "path", "wiz:path", autoFocus = false, path.set),
        textRow(pkg.get, "package", "wiz:pkg", autoFocus = false, pkg.set),
        textRow(sources.get, "sources", "wiz:sources", autoFocus = false, sources.set)
      )

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val statusLine = status.get match {
        case Status.Idle        => text("", hintStyle)
        case Status.Failed(err) => text(s"  ✗ $err", errStyle)
      }

      val title = column(length(1, empty()), length(1, text("  add output", titleStyle)))

      val body = column(
        (rows
          ::: List(
            length(1, empty()),
            saveButton,
            length(1, statusLine),
            fill(1, empty()),
            length(1, text("  tab navigate · enter (on save) commit · esc cancel", hintStyle))
          ))*
      )

      ScreenFrame.withTitle("outputs", back, title, body)
    }
}
