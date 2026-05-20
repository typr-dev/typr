package typr.cli.app.screens

import jatatui.components.chrome.ScreenFrame
import typr.cli.app.components.Link
import jatatui.components.router.{RouterApi, Screen as RouterScreen}
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

/** Main menu — a 3×2 grid of cards filling the available area. Tab cycles, Enter activates, click activates directly. Esc / q quits the host.
  */
object MainMenu {

  enum Action {
    case Sources, Schemas, Outputs, DomainTypes, FieldTypes, Generate
  }

  private case class Item(
      action: Action,
      icon: String,
      title: String,
      pitch: String,
      detail: String,
      hint: String
  )

  private val items: List[Item] = List(
    Item(Action.Sources, "◈", "Sources", "Database Connections", "Postgres, MariaDB, Oracle, SQL Server, DuckDB, SQLite", "where typr reads schemas from"),
    Item(Action.Schemas, "◇", "Schema Browser", "Explore Database", "Tables, columns, types, and relationships", "see what's there before you generate"),
    Item(Action.Outputs, "▣", "Outputs", "Code Targets", "Scala, Java, or Kotlin code generation", "languages, libraries, output paths"),
    Item(
      Action.DomainTypes,
      "✦",
      "Domain Types",
      "Define Once, Use Everywhere",
      "Canonical types aligned to multiple sources",
      "e.g. Customer → postgres, mariadb, api"
    ),
    Item(Action.FieldTypes, "◬", "Field Types", "Field-Level Type Safety", "Map columns to custom types by pattern", "e.g. *_id → CustomerId, *email* → Email"),
    Item(Action.Generate, "▶", "Generate", "Run Code Generation", "Emit repositories, row types, and DSL", "from the current configuration")
  )

  private val brandStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val taglineStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  val element: Element =
    component { ctx =>
      val app = AppApi.use(ctx)
      val router = RouterApi.useRouter(ctx)

      ctx.onGlobalKey(new KeyCode.Esc, () => app.quit())
      ctx.onGlobalKey(new KeyCode.Char('q'), () => app.quit())

      val title = column(
        length(1, text("  typr", brandStyle)),
        length(1, text("  seal your system's boundaries", taglineStyle))
      )

      val pairs = items.grouped(2).toList
      val rows: List[Element] = pairs.zipWithIndex.map { case (pair, rowIdx) =>
        val cards: Array[Element] = pair.zipWithIndex.map { case (item, colIdx) =>
          val globalIdx = rowIdx * 2 + colIdx
          val (label, target) = targetFor(item.action)
          card(item, autoFocus = globalIdx == 0, () => router.push(RouterScreen.of(label, target)))
        }.toArray
        row(cards*)
      }
      val grid = column(rows*)

      val body = column(
        fill(1, grid),
        length(1, text("  tab navigate · enter select · q quit", hintStyle))
      )

      ScreenFrame.withTitle("exit", () => app.quit(), title, body)
    }

  private def targetFor(action: Action): (String, Element) = action match {
    case Action.Sources     => ("sources", SourceList.element)
    case Action.Outputs     => ("outputs", OutputList.element)
    case Action.DomainTypes => ("domain types", TypeList.element(TypeList.Kind.Domain))
    case Action.FieldTypes  => ("field types", TypeList.element(TypeList.Kind.Field))
    case Action.Generate    => ("generate", Generate.element)
    case Action.Schemas     => ("schemas", SchemaPicker.element)
  }

  /** Single card. Link.focusable handles focus + click + Enter; we just paint based on focus. */
  private def card(item: Item, autoFocus: Boolean, onActivate: Runnable): Element = {
    Link.focusable(
      autoFocus,
      onActivate,
      { (focused: Boolean) =>
        val borderType = if (focused) BorderType.Thick else BorderType.Rounded
        val borderStyle = Style.empty.withFg(if (focused) Color.YELLOW else Color.DARK_GRAY)
        val iconStyle =
          Style.empty
            .withFg(if (focused) Color.YELLOW else Color.MAGENTA)
            .withAddModifier(Modifier.BOLD)
        val titleStyle =
          if (focused) Style.empty.withFg(Color.BLACK).withBg(Color.YELLOW).withAddModifier(Modifier.BOLD)
          else Style.empty.withFg(Color.GRAY).withAddModifier(Modifier.BOLD)
        val pitchStyle = Style.empty.withFg(if (focused) Color.YELLOW else Color.DARK_GRAY)
        val detailStyle = Style.empty.withFg(if (focused) Color.WHITE else Color.GRAY)
        val cardHint = Style.empty.withFg(Color.DARK_GRAY)

        // Caret in the icon slot when focused — unambiguous "this is the one".
        val iconCell = if (focused) "▶" else item.icon
        val titleLine = Line.from(
          Span.styled(s" $iconCell ", iconStyle),
          Span.styled(s" ${item.title} ", titleStyle)
        )

        val body = java.util.List.of(
          Line.empty(),
          Line.from(Span.styled(s"  ${item.pitch}", pitchStyle)),
          Line.empty(),
          Line.from(Span.styled(s"  ${item.detail}", detailStyle)),
          Line.empty(),
          Line.from(Span.styled(s"  ${item.hint}", cardHint))
        )

        val cardWidget: Widget = (area, buffer) => {
          val block = Block.empty
            .withTitle(titleLine)
            .withBorders(Borders.ALL)
            .withBorderType(borderType)
            .withBorderStyle(borderStyle)
          Paragraph.of(body).withBlock(block).render(area, buffer)
        }

        widget(cardWidget)
      }
    )
  }
}
