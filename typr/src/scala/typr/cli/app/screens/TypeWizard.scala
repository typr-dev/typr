package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
import jatatui.components.chrome.ScreenFrame
import jatatui.components.router.RouterApi
import jatatui.components.textinput.{TextInputComponent, TextInputProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import typr.cli.app.AppApi
import typr.config.generated.{BridgeType, DomainType, FieldType}

/** New-type wizard. The kind (`Field` / `Domain`) is fixed by which list the wizard was launched from, so no kind-picker — just the name + the minimum fields needed to make a valid type. Save
  * persists via [[AppApi.updateConfig]] and pops back to the list.
  *
  * Matches the editor's first-cut depth: FieldType captures `underlying` only; DomainType captures `primary` + `description`. Deep editing of nested matchers / fields map belongs in the next pass on
  * the editor.
  */
object TypeWizard {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val valueStyle: Style = Style.empty.withFg(Color.WHITE)
  private val focusedValueStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val cursorStyle: Style = Style.empty.withAddModifier(Modifier.REVERSED)

  sealed trait Status
  object Status {
    case object Idle extends Status
    final case class Failed(error: String) extends Status
  }

  def element(kind: TypeList.Kind): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())

      val name = ctx.useState(() => "")
      val underlying = ctx.useState(() => "") // FieldType
      val primary = ctx.useState(() => "") // DomainType
      val descr = ctx.useState(() => "") // DomainType
      val status = ctx.useState(() => Status.Idle: Status)

      def save(): Unit = {
        val n = name.get.trim
        if (n.isEmpty)
          status.set(Status.Failed("name required"))
        else if (app.config.types.getOrElse(Map.empty).contains(n))
          status.set(Status.Failed(s"type '$n' already exists"))
        else {
          val newType: BridgeType = kind match {
            case TypeList.Kind.Field =>
              FieldType(
                api = None,
                db = None,
                model = None,
                underlying = Option(underlying.get.trim).filter(_.nonEmpty),
                validation = None
              )
            case TypeList.Kind.Domain =>
              DomainType(
                alignedSources = None,
                description = Option(descr.get.trim).filter(_.nonEmpty),
                fields = Map.empty,
                generate = None,
                primary = Option(primary.get.trim).filter(_.nonEmpty),
                projections = None
              )
          }
          val newTypes = app.config.types.getOrElse(Map.empty) + (n -> newType)
          app.updateConfig(c => c.copy(types = Some(newTypes))) match {
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

      val kindRows: List[Element] = kind match {
        case TypeList.Kind.Field =>
          List(textRow(underlying.get, "underlying", "wiz:underlying", autoFocus = false, underlying.set))
        case TypeList.Kind.Domain =>
          List(
            textRow(primary.get, "primary", "wiz:primary", autoFocus = false, primary.set),
            textRow(descr.get, "description", "wiz:description", autoFocus = false, descr.set)
          )
      }

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val statusLine = status.get match {
        case Status.Idle        => text("", hintStyle)
        case Status.Failed(err) => text(s"  ✗ $err", errStyle)
      }

      val heading = kind match {
        case TypeList.Kind.Field  => "  add field type"
        case TypeList.Kind.Domain => "  add domain type"
      }

      val note = kind match {
        case TypeList.Kind.Field =>
          "  match patterns (api / db / model) + validation come from the editor after save."
        case TypeList.Kind.Domain =>
          "  fields + aligned sources come from the editor after save."
      }

      val title = column(length(1, empty()), length(1, text(heading, titleStyle)))

      val body = column(
        (textRow(name.get, "name", "wiz:name", autoFocus = true, name.set)
          :: kindRows
          ::: List(
            length(1, empty()),
            saveButton,
            length(1, statusLine),
            length(1, empty()),
            length(1, text(note, infoStyle)),
            fill(1, empty()),
            length(1, text("  tab navigate · enter (on save) commit · esc cancel", hintStyle))
          ))*
      )

      ScreenFrame.withTitle("types", back, title, body)
    }
}
