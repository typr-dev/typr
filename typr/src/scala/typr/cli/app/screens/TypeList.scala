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
import typr.config.generated.{BridgeType, DomainType, FieldType}

import scala.jdk.CollectionConverters.*

object TypeList {

  enum Kind {
    case Field, Domain
  }

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  def element(kind: Kind): Element =
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
        val remaining = app.config.types.getOrElse(Map.empty) - name
        val nextMap = if (remaining.isEmpty) None else Some(remaining)
        app.updateConfig(c => c.copy(types = nextMap)) match {
          case Right(_) =>
            pendingDelete.set(None)
            deleteError.set(None)
          case Left(e) =>
            deleteError.set(Some(Option(e.getMessage).getOrElse(e.toString)))
            pendingDelete.set(None)
        }
      }

      val types = app.config.types
        .getOrElse(Map.empty)
        .toList
        .sortBy(_._1)
        .filter { case (_, t) => matchesKind(t, kind) }

      val addCard = length(
        3,
        addNewCard(
          kind,
          autoFocus = true,
          onActivate = {
            val (linkLabel, _) = addNewLabels(kind)
            () => router.push(RouterScreen.of(linkLabel, TypeWizard.element(kind)))
          }
        )
      )
      // Domain types get a second "create from real entity" card right under the blank-form
      // option, since picking a real entity is the easier (and recommended) path.
      val fromEntityCard: List[Element] = kind match {
        case Kind.Domain => List(length(3, fromEntityShortcut(() => router.push(RouterScreen.of("create from entity", DomainFromEntity.element)))))
        case Kind.Field  => Nil
      }
      val typeCards: List[Element] = types.map { case (name, t) =>
        length(
          3,
          typeCard(
            name,
            t,
            () => pendingDelete.set(Some(name)),
            () => router.push(RouterScreen.of(s"type · $name", TypeEditor.element(name)))
          )
        )
      }

      val title = column(length(1, empty()), length(1, text(s"  ${heading(kind)}", titleStyle)))

      val errorLine =
        deleteError.get.fold(length(0, empty())) { msg =>
          length(1, text(s"  ✗ ${msg.take(140)}", Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)))
        }

      val body = column(
        fill(1, Scrollable.column((addCard :: (fromEntityCard ::: typeCards)).asJava)),
        errorLine,
        length(1, text("  tab navigate · enter open · d delete · wheel to scroll · esc back", hintStyle))
      )

      val frame = ScreenFrame.withTitle("menu", back, title, body)

      val confirm = ConfirmDialog.of(
        pendingDelete.get.isDefined,
        s" delete ${kindNoun(kind)} ",
        pendingDelete.get.fold("")(nm => s"Delete '$nm'? This cannot be undone."),
        "delete",
        "cancel",
        true,
        () => pendingDelete.get.foreach(doDelete),
        () => pendingDelete.set(None)
      )

      stack(frame, confirm)
    }

  private def kindNoun(kind: Kind): String = kind match {
    case Kind.Field  => "field type"
    case Kind.Domain => "domain type"
  }

  private def heading(kind: Kind): String = kind match {
    case Kind.Field  => "field types"
    case Kind.Domain => "domain types"
  }

  private def matchesKind(t: BridgeType, kind: Kind): Boolean = (t, kind) match {
    case (_: FieldType, Kind.Field)   => true
    case (_: DomainType, Kind.Domain) => true
    case _                            => false
  }

  /** "Create from a real entity" shortcut on the domain-types screen. Tabs into the entity picker, where the user sees actual tables / models / records / messages from loaded sources instead of
    * having to invent a `primary` string by hand.
    */
  private def fromEntityShortcut(onActivate: Runnable): Element =
    Link.focusable(
      false,
      onActivate,
      { (focused: Boolean) =>
        val borderType = if (focused) BorderType.Thick else BorderType.Rounded
        val borderStyle = Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)
        val labelStyle =
          Style.empty.withFg(if (focused) Color.CYAN else Color.GRAY).withAddModifier(Modifier.BOLD)
        val hintTxt = Style.empty.withFg(if (focused) Color.WHITE else Color.DARK_GRAY)
        val w: Widget = (area, buffer) => {
          val block = Block.empty
            .withBorders(Borders.ALL)
            .withBorderType(borderType)
            .withBorderStyle(borderStyle)
          Paragraph
            .of(
              Line.from(
                Span.styled("  ✦  create from a real entity ", labelStyle),
                Span.styled(s" — pick a table / model / record, get fields + alignment suggestions", hintTxt)
              )
            )
            .withBlock(block)
            .render(area, buffer)
        }
        widget(w)
      }
    )

  private def addNewLabels(kind: Kind): (String, String) = kind match {
    case Kind.Field  => ("add field type", "+  add new field type")
    case Kind.Domain => ("add domain type", "+  add new domain type")
  }

  private def addNewCard(kind: Kind, autoFocus: Boolean, onActivate: Runnable): Element = {
    val (_, cardLabel) = addNewLabels(kind)
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
            .of(Line.from(Span.styled(s"  $cardLabel", labelStyle)))
            .withBlock(block)
            .render(area, buffer)
        }
        widget(w)
      }
    )
  }

  private def typeCard(name: String, t: BridgeType, onDelete: () => Unit, onActivate: Runnable): Element =
    Link.focusable(
      false,
      onActivate,
      { (focused: Boolean) =>
        component { c =>
          if (focused) c.onKey(new KeyCode.Char('d'), () => onDelete())

          val borderType = if (focused) BorderType.Thick else BorderType.Rounded
          val borderStyle = Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)

          val (icon, kindLabel, detail) = t match {
            case ft: FieldType  => ("◬", "field", fieldTypeDetail(ft))
            case dt: DomainType => ("✦", "domain", domainTypeDetail(dt))
          }

          val titleLine = Line.from(
            Span.styled(s" $icon ", borderStyle),
            Span.styled(
              s"$name ",
              if (focused) Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
              else Style.empty.withFg(Color.GRAY).withAddModifier(Modifier.BOLD)
            ),
            Span.styled(
              s"· $kindLabel ",
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
            Paragraph.of(s"  $detail").withBlock(block).withStyle(detailStyle).render(area, buffer)
          }
          widget(w)
        }
      }
    )

  private def fieldTypeDetail(t: FieldType): String = {
    val underlying = t.underlying.getOrElse("—")
    val matchers = List(
      t.api.map(_ => "api"),
      t.db.map(_ => "db"),
      t.model.map(_ => "model")
    ).flatten
    val matchersStr = if (matchers.isEmpty) "no patterns" else matchers.mkString(" · ")
    s"$underlying  ·  $matchersStr"
  }

  private def domainTypeDetail(t: DomainType): String = {
    val primary = t.primary.getOrElse("—")
    val fields = t.fields.size
    val aligned = t.alignedSources.map(_.size).getOrElse(0)
    s"primary = $primary  ·  $fields fields  ·  $aligned aligned source${if (aligned == 1) "" else "s"}"
  }
}
