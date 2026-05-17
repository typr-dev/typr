package typr.cli.app.screens
import typr.cli.app.components.Run.given

import io.circe.{Json, JsonObject}
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

import scala.jdk.CollectionConverters.*

/** New-source wizard. Name + kind dropdown + kind-specific fields, all on one screen so the form shape updates live when the kind changes. Save validates name uniqueness, writes through
  * [[AppApi.updateConfig]] (which persists to typr.yaml), and pops back to the source list.
  *
  * Field set per kind is borrowed from [[SourceForm.fieldsByKind]] so the wizard and the editor agree on what's relevant.
  */
object SourceWizard {

  private val kinds: List[String] = List("postgres", "mariadb", "oracle", "sqlserver", "duckdb", "openapi")

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
      val kindIdx = ctx.useState(() => 0)
      val values = ctx.useState(() => Map.empty[String, String])
      val status = ctx.useState(() => Status.Idle: Status)

      val currentKind: String = kinds(kindIdx.get)
      val fieldDefs: List[SourceForm.Field] = SourceForm.fieldsByKind.getOrElse(formKindKey(currentKind), Nil)

      def save(): Unit = {
        val trimmedName = name.get.trim
        if (trimmedName.isEmpty)
          status.set(Status.Failed("name required"))
        else if (app.config.sources.getOrElse(Map.empty).contains(trimmedName))
          status.set(Status.Failed(s"source '$trimmedName' already exists"))
        else {
          val newJson = buildJson(currentKind, values.get, fieldDefs)
          val newSources = app.config.sources.getOrElse(Map.empty) + (trimmedName -> newJson)
          app.updateConfig(c => c.copy(sources = Some(newSources))) match {
            case Right(_) => router.pop()
            case Left(e)  => status.set(Status.Failed(Option(e.getMessage).getOrElse(e.toString)))
          }
        }
      }

      val nameRow = length(
        3,
        TextInputComponent.of(
          TextInputProps
            .of(name.get, v => { name.set(v); status.set(Status.Idle) })
            .withTitle("name")
            .withFocusId("wiz:name")
            .withAutoFocus(true)
            .withStyle(valueStyle)
            .withFocusedStyle(focusedValueStyle)
            .withCursorStyle(cursorStyle)
        )
      )

      val kindRow = length(
        3,
        Dropdown.of(
          DropdownProps
            .of[String](
              "kind",
              kinds.asJava,
              (s: String) => s,
              kindIdx.get,
              (idx: Int) => kindIdx.set(idx)
            )
            .withFocusId("wiz:kind")
            .withStyle(valueStyle)
            .withFocusedStyle(focusedValueStyle)
        )
      )

      val fieldRows: List[Element] = fieldDefs.map { f =>
        length(
          3,
          TextInputComponent.of(
            TextInputProps
              .of(values.get.getOrElse(f.key, ""), v => values.update(_.updated(f.key, v)))
              .withTitle(f.label)
              .withFocusId(s"wiz:${f.key}")
              .withAutoFocus(false)
              .withStyle(valueStyle)
              .withFocusedStyle(focusedValueStyle)
              .withCursorStyle(cursorStyle)
          )
        )
      }

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val statusLine = status.get match {
        case Status.Idle        => text("", hintStyle)
        case Status.Failed(err) => text(s"  ✗ $err", errStyle)
      }

      val title = column(length(1, empty()), length(1, text("  add source", titleStyle)))

      val body = column(
        (nameRow
          :: kindRow
          :: fieldRows
          ::: List(
            length(1, empty()),
            saveButton,
            length(1, statusLine),
            fill(1, empty()),
            length(1, text("  tab navigate · enter (on save) commit · esc cancel", hintStyle))
          ))*
      )

      ScreenFrame.withTitle("sources", back, title, body)
    }

  /** Map user-visible kind to the form's internal kind key (some kinds share field sets). */
  private def formKindKey(kind: String): String = kind match {
    case "duckdb"                 => "duckdb"
    case "openapi" | "jsonschema" => "openapi"
    case "oracle"                 => "oracle"
    case _                        => "jdbc"
  }

  private def buildJson(kind: String, values: Map[String, String], defs: List[SourceForm.Field]): Json = {
    val obj0 = JsonObject("type" -> Json.fromString(kind))
    val merged = defs.foldLeft(obj0) { case (obj, SourceForm.Field(key, _)) =>
      val v = values.getOrElse(key, "").trim
      if (v.isEmpty) obj
      else if (key == "port")
        v.toLongOption.fold(obj.add(key, Json.fromString(v)))(n => obj.add(key, Json.fromLong(n)))
      else obj.add(key, Json.fromString(v))
    }
    Json.fromJsonObject(merged)
  }
}
