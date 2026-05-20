package typr.cli.app.screens
import typr.cli.app.components.Run.given

import io.circe.Json
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
import typr.cli.app.{AppApi, LoadStatus}
import typr.cli.config.{ConfigParser, ParsedSource}

/** Picks which source to open in a browser. Database / duckdb sources route to [[SchemaBrowser]]; openapi / jsonschema sources route to [[SpecBrowser]]. Sources whose kind we don't yet know how to
  * browse (avro, grpc, …) are listed below with a note.
  */
object SchemaPicker {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val infoStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  private sealed trait Routing
  private object Routing {
    case object DbBrowser extends Routing
    case object SpecBrowser extends Routing
    case object AvroBrowser extends Routing
    case object ProtoBrowser extends Routing
    case object Unsupported extends Routing
  }

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val sources = app.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)

      val routed: List[(String, Json, Routing)] = sources.map { case (name, json) =>
        val r = ConfigParser.parseSource(json) match {
          case Right(_: ParsedSource.Database | _: ParsedSource.DuckDb | _: ParsedSource.Sqlite) => Routing.DbBrowser
          case Right(_: ParsedSource.OpenApi | _: ParsedSource.JsonSchema)                       => Routing.SpecBrowser
          case Right(_: ParsedSource.Avro)                                                       => Routing.AvroBrowser
          case Right(_: ParsedSource.Grpc)                                                       => Routing.ProtoBrowser
          case Left(_)                                                                           => Routing.Unsupported
        }
        (name, json, r)
      }
      val browsable = routed.filter(_._3 != Routing.Unsupported)
      val unsupported = routed.filter(_._3 == Routing.Unsupported)

      val cards: List[Element] = browsable.zipWithIndex.map { case ((name, json, routing), idx) =>
        val status = app.loadStatus.getOrElse(name, LoadStatus.NotLoaded)
        val (label, target) = labelAndTarget(name, routing)
        length(
          3,
          sourceCard(
            name,
            json,
            routing,
            status,
            autoFocus = idx == 0,
            () => router.push(RouterScreen.of(label, target))
          )
        )
      }

      val title = column(length(1, empty()), length(1, text("  schema browser", titleStyle)))

      val notBrowsableNote =
        if (unsupported.isEmpty) length(0, empty())
        else
          length(
            2,
            column(
              length(1, empty()),
              length(
                1,
                text(
                  s"  ${unsupported.size} source${plural(unsupported.size)} of unrecognized type — open as a file directly.",
                  infoStyle
                )
              )
            )
          )

      val body =
        if (browsable.isEmpty)
          column(
            fill(1, empty()),
            length(1, text("  no browsable sources configured.", infoStyle)),
            length(1, text("  add a database, duckdb, or openapi source first.", hintStyle)),
            fill(1, empty()),
            length(1, text("  esc to return", hintStyle))
          )
        else
          column(
            (cards
              ::: List(
                notBrowsableNote,
                fill(1, empty()),
                length(1, text("  tab navigate · enter open · esc back", hintStyle))
              ))*
          )

      ScreenFrame.withTitle("menu", back, title, body)
    }

  private def labelAndTarget(name: String, routing: Routing): (String, Element) = routing match {
    case Routing.DbBrowser    => (s"schema · $name", SchemaBrowser.element(name))
    case Routing.SpecBrowser  => (s"spec · $name", SpecBrowser.element(name))
    case Routing.AvroBrowser  => (s"avro · $name", AvroBrowser.element(name))
    case Routing.ProtoBrowser => (s"grpc · $name", ProtoBrowser.element(name))
    case Routing.Unsupported  => (s"unsupported · $name", SchemaBrowser.element(name)) // dead branch
  }

  private def sourceCard(
      name: String,
      json: Json,
      routing: Routing,
      status: LoadStatus,
      autoFocus: Boolean,
      onActivate: Runnable
  ): Element = {
    Link.focusable(
      autoFocus,
      onActivate,
      { (focused: Boolean) =>
        val borderType = if (focused) BorderType.Thick else BorderType.Rounded
        val borderStyle = Style.empty.withFg(if (focused) Color.CYAN else Color.DARK_GRAY)

        val (icon, accentColor) = routing match {
          case Routing.DbBrowser    => ("◈", Color.CYAN)
          case Routing.SpecBrowser  => ("◇", Color.MAGENTA)
          case Routing.AvroBrowser  => ("▦", Color.YELLOW)
          case Routing.ProtoBrowser => ("▣", Color.GREEN)
          case Routing.Unsupported  => ("?", Color.DARK_GRAY)
        }

        val (badge, badgeColor) = status match {
          case LoadStatus.Loaded    => ("✓ ready", Color.GREEN)
          case LoadStatus.Loading   => ("◕ loading…", Color.YELLOW)
          case LoadStatus.Failed(_) => ("✗ failed", Color.RED)
          case LoadStatus.NotLoaded => ("— not loaded", Color.DARK_GRAY)
        }

        val titleLine = Line.from(
          Span.styled(s" $icon ", Style.empty.withFg(if (focused) accentColor else Color.DARK_GRAY)),
          Span.styled(
            s"$name ",
            if (focused) Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
            else Style.empty.withFg(Color.GRAY).withAddModifier(Modifier.BOLD)
          ),
          Span.styled(
            s"· ${kindOf(json)}  ",
            Style.empty.withFg(if (focused) accentColor else Color.DARK_GRAY)
          ),
          Span.styled(badge, Style.empty.withFg(badgeColor)),
          Span.styled(" ", Style.empty)
        )

        val detailStyle = Style.empty.withFg(if (focused) Color.WHITE else Color.GRAY)

        val detail = status match {
          case LoadStatus.Failed(err) => s"  ${err.take(120)}"
          case _                      => s"  ${detailLabel(json, routing)}"
        }

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
    )
  }

  private def kindOf(json: Json): String = {
    val c = json.hcursor
    c.get[String]("type").toOption.getOrElse {
      if (c.get[String]("path").isRight) "duckdb"
      else if (c.get[String]("spec").isRight) "openapi"
      else if (c.get[String]("proto_path").isRight) "grpc"
      else if (c.get[String]("host").isRight) "jdbc"
      else "?"
    }
  }

  private def detailLabel(json: Json, routing: Routing): String =
    (routing, ConfigParser.parseSource(json).toOption) match {
      case (Routing.SpecBrowser, Some(ParsedSource.OpenApi(b))) =>
        b.spec.orElse(b.specs.flatMap(_.headOption)).getOrElse("(no spec path)")
      case (Routing.SpecBrowser, Some(ParsedSource.JsonSchema(b))) =>
        b.spec.orElse(b.specs.flatMap(_.headOption)).getOrElse("(no spec path)")
      case (Routing.AvroBrowser, Some(ParsedSource.Avro(b))) =>
        val list = b.schemas.toList.flatten ::: b.schema.toList
        if (list.isEmpty) "(no schemas)" else list.mkString(", ")
      case (Routing.ProtoBrowser, Some(ParsedSource.Grpc(b))) =>
        b.proto_path.orElse(b.proto_paths.flatMap(_.headOption)).getOrElse("(no proto path)")
      case _ => connectionLabel(json)
    }

  private def connectionLabel(json: Json): String = {
    val c = json.hcursor
    kindOf(json) match {
      case "duckdb" | "sqlite" => c.get[String]("path").getOrElse(":memory:")
      case _                   =>
        val host = c.get[String]("host").getOrElse("")
        val port = c.get[Long]("port").map(_.toString).getOrElse("")
        val db = c.get[String]("database").getOrElse("")
        (host, port, db) match {
          case ("", _, _)  => "(no host)"
          case (h, "", "") => h
          case (h, p, "")  => s"$h:$p"
          case (h, "", d)  => s"$h/$d"
          case (h, p, d)   => s"$h:$p/$d"
        }
    }
  }

  private def plural(n: Int): String = if (n == 1) "" else "s"
}
