package typr.cli.app.screens
import typr.cli.app.components.Run.given

import io.circe.Json
import jatatui.components.chrome.ScreenFrame
import typr.cli.app.components.Link
import jatatui.components.modal.ConfirmDialog
import jatatui.components.router.{RouterApi, Screen as RouterScreen}
import jatatui.components.scrollable.Scrollable
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.core.text.{Line, Span}
import jatatui.core.widgets.Widget
import jatatui.react.Components.*
import jatatui.react.Element
import jatatui.widgets.Borders
import jatatui.widgets.block.{Block, BorderType}
import jatatui.widgets.paragraph.Paragraph
import tui.crossterm.KeyCode
import typr.cli.app.AppApi
import typr.config.generated.{StringOrArrayArray, StringOrArrayString}

import scala.jdk.CollectionConverters.*

/** List of configured sources. Tab cycles, Enter or click opens the source in [[SourceEditor]]; `d` on a focused card asks to delete it. Esc / b returns to the previous screen.
  */
object SourceList {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()
      val pendingDelete = ctx.useState(() => Option.empty[String])
      val deleteError = ctx.useState(() => Option.empty[String])

      // Only register Esc / b when no confirm modal is open; otherwise let the modal swallow Esc.
      if (pendingDelete.get.isEmpty) {
        ctx.onGlobalKey(new KeyCode.Esc, () => back())
        ctx.onGlobalKey(new KeyCode.Char('b'), () => back())
      }

      // Cascading delete: drop the source, and scrub references to it from each output's
      // `sources` list. If an output ends up with no remaining sources, set its `sources` to
      // None (meaning "all sources" — the same default a freshly created output uses).
      def doDelete(name: String): Unit = {
        app.updateConfig { c =>
          val nextSources = {
            val remaining = c.sources.getOrElse(Map.empty) - name
            if (remaining.isEmpty) None else Some(remaining)
          }
          val nextOutputs = c.outputs.map { outputs =>
            outputs.map { case (outName, out) =>
              val updated = out.sources match {
                case Some(StringOrArrayString(s)) if s == name => out.copy(sources = None)
                case Some(StringOrArrayArray(arr))             =>
                  val filtered = arr.filterNot(_ == name)
                  val next =
                    if (filtered.isEmpty) None
                    else if (filtered.size == 1) Some(StringOrArrayString(filtered.head))
                    else Some(StringOrArrayArray(filtered))
                  out.copy(sources = next)
                case _ => out
              }
              outName -> updated
            }
          }
          c.copy(sources = nextSources, outputs = nextOutputs)
        } match {
          case Right(_) =>
            pendingDelete.set(None)
            deleteError.set(None)
          case Left(e) =>
            deleteError.set(Some(Option(e.getMessage).getOrElse(e.toString)))
            pendingDelete.set(None)
        }
      }

      val sources = app.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)

      val addCard = length(
        3,
        addNewCard(
          autoFocus = true,
          onActivate = () => router.push(RouterScreen.of("add source", SourceWizard.element))
        )
      )
      val sourceCards: List[Element] = sources.map { case (name, json) =>
        length(
          3,
          sourceCard(
            name,
            json,
            () => pendingDelete.set(Some(name)),
            () => router.push(RouterScreen.of(s"source · $name", SourceEditor.element(name)))
          )
        )
      }

      val title = column(length(1, empty()), length(1, text("  sources", titleStyle)))

      val errorLine =
        deleteError.get.fold(length(0, empty())) { msg =>
          length(1, text(s"  ✗ ${msg.take(140)}", Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)))
        }

      val body = column(
        fill(1, Scrollable.column((addCard :: sourceCards).asJava)),
        errorLine,
        length(1, text("  tab navigate · enter open · d delete · wheel to scroll · esc back", hintStyle))
      )

      val frame = ScreenFrame.withTitle("menu", back, title, body)

      val confirm = ConfirmDialog.of(
        pendingDelete.get.isDefined,
        " delete source ",
        pendingDelete.get.fold("")(nm => s"Delete '$nm'? This cannot be undone."),
        "delete",
        "cancel",
        true,
        () => pendingDelete.get.foreach(doDelete),
        () => pendingDelete.set(None)
      )

      stack(frame, confirm)
    }

  private def addNewCard(autoFocus: Boolean, onActivate: Runnable): Element =
    Link.focusable(
      autoFocus,
      onActivate,
      { (focused: Boolean) =>
        val borderType = if (focused) BorderType.Thick else BorderType.Rounded
        val borderStyle = Style.empty.withFg(if (focused) Color.GREEN else Color.DARK_GRAY)
        val labelStyle =
          Style.empty.withFg(if (focused) Color.GREEN else Color.GRAY).withAddModifier(Modifier.BOLD)

        val w: Widget = (area, buffer) => {
          val block = Block.empty
            .withBorders(Borders.ALL)
            .withBorderType(borderType)
            .withBorderStyle(borderStyle)
          Paragraph
            .of(Line.from(Span.styled("  +  add new source", labelStyle)))
            .withBlock(block)
            .render(area, buffer)
        }
        widget(w)
      }
    )

  private def sourceCard(name: String, json: Json, onDelete: () => Unit, onActivate: Runnable): Element =
    Link.focusable(
      false,
      onActivate,
      { (focused: Boolean) =>
        component { c =>
          if (focused) c.onKey(new KeyCode.Char('d'), () => onDelete())

          val borderType = if (focused) BorderType.Thick else BorderType.Rounded
          val borderStyle = Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)

          val titleLine = Line.from(
            Span.styled(s" ${sourceIcon(json)} ", borderStyle),
            Span.styled(
              s"$name ",
              if (focused) Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
              else Style.empty.withFg(Color.GRAY).withAddModifier(Modifier.BOLD)
            ),
            Span.styled(
              s"· ${sourceKind(json)} ",
              Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)
            )
          )

          val detailStyle = Style.empty.withFg(if (focused) Color.WHITE else Color.GRAY)

          val w: Widget = (area, buffer) => {
            val block = Block.empty
              .withTitle(titleLine)
              .withBorders(Borders.ALL)
              .withBorderType(borderType)
              .withBorderStyle(borderStyle)
            Paragraph
              .of(s"  ${sourceDetails(json)}")
              .withBlock(block)
              .withStyle(detailStyle)
              .render(area, buffer)
          }
          widget(w)
        }
      }
    )

  private def sourceKind(json: Json): String = {
    val c = json.hcursor
    c.get[String]("type").toOption.getOrElse {
      if (c.get[String]("path").isRight) "duckdb"
      else if (c.get[String]("spec").isRight) "openapi"
      else if (c.get[String]("host").isRight) "jdbc"
      else "?"
    }
  }

  private def sourceIcon(json: Json): String = sourceKind(json) match {
    case "duckdb"  => "◆"
    case "openapi" => "✦"
    case "jdbc"    => "◈"
    case _         => "◇"
  }

  private def sourceDetails(json: Json): String = {
    val c = json.hcursor
    sourceKind(json) match {
      case "duckdb" =>
        c.get[String]("path").getOrElse(":memory:")
      case "openapi" =>
        c.get[String]("spec")
          .toOption
          .orElse(c.downField("specs").downArray.as[String].toOption)
          .getOrElse("")
      case _ =>
        val host = c.get[String]("host").getOrElse("")
        val port = c.get[Long]("port").map(_.toString).getOrElse("")
        val db = c.get[String]("database").getOrElse("")
        (host, port, db) match {
          case ("", _, _)  => ""
          case (h, "", "") => h
          case (h, p, "")  => s"$h:$p"
          case (h, "", d)  => s"$h/$d"
          case (h, p, d)   => s"$h:$p/$d"
        }
    }
  }
}
