package typr.cli.app.screens
import typr.cli.app.components.Run.given

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
import typr.config.generated.{Output, StringOrArray, StringOrArrayArray, StringOrArrayString}

import scala.jdk.CollectionConverters.*

/** List of configured outputs. Same shape as [[SourceList]]. */
object OutputList {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()
      val pendingDelete = ctx.useState(() => Option.empty[String])
      val deleteError = ctx.useState(() => Option.empty[String])

      if (pendingDelete.get.isEmpty) {
        ctx.onGlobalKey(new KeyCode.Esc, () => back())
        ctx.onGlobalKey(new KeyCode.Char('b'), () => back())
      }

      def doDelete(name: String): Unit = {
        val remaining = app.config.outputs.getOrElse(Map.empty) - name
        val nextMap = if (remaining.isEmpty) None else Some(remaining)
        app.updateConfig(c => c.copy(outputs = nextMap)) match {
          case Right(_) =>
            pendingDelete.set(None)
            deleteError.set(None)
          case Left(e) =>
            deleteError.set(Some(Option(e.getMessage).getOrElse(e.toString)))
            pendingDelete.set(None)
        }
      }

      val outputs = app.config.outputs.getOrElse(Map.empty).toList.sortBy(_._1)

      val addCard = length(
        3,
        addNewCard(
          autoFocus = true,
          onActivate = () => router.push(RouterScreen.of("add output", OutputWizard.element))
        )
      )
      val outputCards: List[Element] = outputs.map { case (name, output) =>
        length(
          3,
          outputCard(
            name,
            output,
            () => pendingDelete.set(Some(name)),
            () => router.push(RouterScreen.of(s"output · $name", OutputEditor.element(name)))
          )
        )
      }

      val title = column(length(1, empty()), length(1, text("  outputs", titleStyle)))

      val errorLine =
        deleteError.get.fold(length(0, empty())) { msg =>
          length(1, text(s"  ✗ ${msg.take(140)}", Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)))
        }

      val body = column(
        fill(1, Scrollable.column((addCard :: outputCards).asJava)),
        errorLine,
        length(1, text("  tab navigate · enter open · d delete · wheel to scroll · esc back", hintStyle))
      )

      val frame = ScreenFrame.withTitle("menu", back, title, body)

      val confirm = ConfirmDialog.of(
        pendingDelete.get.isDefined,
        " delete output ",
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
            .of(Line.from(Span.styled("  +  add new output", labelStyle)))
            .withBlock(block)
            .render(area, buffer)
        }
        widget(w)
      }
    )

  private def outputCard(name: String, output: Output, onDelete: () => Unit, onActivate: Runnable): Element =
    Link.focusable(
      false,
      onActivate,
      { (focused: Boolean) =>
        component { c =>
          if (focused) c.onKey(new KeyCode.Char('d'), () => onDelete())

          val borderType = if (focused) BorderType.Thick else BorderType.Rounded
          val borderStyle = Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)

          val titleLine = Line.from(
            Span.styled(s" ${languageIcon(output.language)} ", borderStyle),
            Span.styled(
              s"$name ",
              if (focused) Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
              else Style.empty.withFg(Color.GRAY).withAddModifier(Modifier.BOLD)
            ),
            Span.styled(
              s"· ${output.language} ",
              Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)
            )
          )

          val detail = s"  ${output.path}  ·  ${output.`package`}  ·  ${sourcesLabel(output.sources)}"
          val detailStyle = Style.empty.withFg(if (focused) Color.WHITE else Color.GRAY)

          val w: Widget = (area, buffer) => {
            val block = Block.empty
              .withTitle(titleLine)
              .withBorders(Borders.ALL)
              .withBorderType(borderType)
              .withBorderStyle(borderStyle)
            Paragraph.of(detail).withBlock(block).withStyle(detailStyle).render(area, buffer)
          }
          widget(w)
        }
      }
    )

  private def languageIcon(lang: String): String = lang match {
    case "scala"  => "𝓢"
    case "java"   => "𝓙"
    case "kotlin" => "𝓚"
    case _        => "◇"
  }

  private def sourcesLabel(sources: Option[StringOrArray]): String = sources match {
    case Some(StringOrArrayString(s))  => s
    case Some(StringOrArrayArray(arr)) => arr.mkString(", ")
    case None                          => "*"
  }
}
