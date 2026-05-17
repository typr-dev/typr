package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.chrome.ScreenFrame
import jatatui.components.picker.{Picker, PickerProps}
import jatatui.components.router.{RouterApi, Screen as RouterScreen}
import jatatui.components.search.FuzzyMatch
import jatatui.components.selectablelist.{SelectableList, SelectableListProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.core.text.{Line, Span}
import jatatui.core.widgets.Widget
import jatatui.react.Components.*
import jatatui.react.Element
import jatatui.widgets.paragraph.Paragraph
import tui.crossterm.KeyCode
import typr.cli.app.{AppApi, LoadStatus, LoadedSource}
import typr.cli.app.components.CaretCell
import typr.grpc.*

import scala.jdk.CollectionConverters.*

/** Per-source Protobuf / gRPC schema browser. Mirrors [[SpecBrowser]] / [[AvroBrowser]]: background-fetched, cached, with search + collapsible tree.
  *
  * Sections in the tree: Services (each expandable into its RPC methods, color-coded by RPC pattern), Messages (collapsible into fields, oneof groups callout above the affected fields), Enums
  * (collapsible into values).
  */
object ProtoBrowser {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.GRAY)
  private val sectionStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val serviceStyle: Style = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
  private val messageStyle: Style = Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
  private val enumStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val fieldNameStyle: Style = Style.empty.withFg(Color.CYAN)
  private val fieldTypeStyle: Style = Style.empty.withFg(Color.YELLOW)
  private val oneofStyle: Style = Style.empty.withFg(Color.GREEN)

  sealed trait TreeRow
  object TreeRow {
    final case class SectionHeader(label: String) extends TreeRow
    final case class ServiceRow(svc: ProtoService, isOpen: Boolean) extends TreeRow
    final case class MethodRow(method: ProtoMethod) extends TreeRow
    final case class MessageRow(msg: ProtoMessage, isOpen: Boolean) extends TreeRow
    final case class EnumRow(en: ProtoEnum, isOpen: Boolean) extends TreeRow
    final case class OneofRow(name: String) extends TreeRow
    final case class FieldRow(name: String, number: Int, tpe: String, label: String, oneof: Option[String], nameWidth: Int, typeWidth: Int) extends TreeRow
    final case class EnumValueRow(name: String, number: Int) extends TreeRow
    case object Blank extends TreeRow
  }

  private final case class SearchItem(label: String, kind: SearchKind, anchorKey: String)
  private sealed trait SearchKind
  private object SearchKind {
    case object Service extends SearchKind
    case object Method extends SearchKind
    case object Message extends SearchKind
    case object Enum extends SearchKind
    case object Field extends SearchKind
    case object Value extends SearchKind
  }

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val title = column(length(1, empty()), length(1, text(s"  grpc · $name", titleStyle)))
      val status = app.loadStatus.getOrElse(name, LoadStatus.NotLoaded)
      val body = app.sourceCache.get(name) match {
        case Some(LoadedSource.Proto(files)) => loadedBody(files)
        case Some(_)                         => failedBody(s"source '$name' is not a grpc source")
        case None                            =>
          status match {
            case LoadStatus.Failed(err) => failedBody(err)
            case LoadStatus.NotLoaded   => notInConfigBody(name)
            case _                      => loadingBody()
          }
      }
      ScreenFrame.withTitle("schemas", back, title, body)
    }

  private def notInConfigBody(name: String): Element = column(
    length(1, text(s"  source '$name' not in config", errStyle)),
    length(1, empty()),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def loadingBody(): Element = column(
    length(1, text("  compiling protos…", Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD))),
    length(1, empty()),
    length(1, text("  running protoc, building descriptors. (requires `protoc` on PATH)", infoStyle)),
    fill(1, empty())
  )

  private def failedBody(err: String): Element = column(
    length(1, text(s"  ✗ ${err.take(400)}", errStyle)),
    length(1, empty()),
    length(1, text("  hint: install protobuf (brew install protobuf) and ensure protoc is on PATH", hintStyle)),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def loadedBody(files: List[ProtoFile]): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val expanded = ctx.useState(() => Set.empty[String])
      val searchOpen = ctx.useState(() => false)

      ctx.onKey(
        new KeyCode.Char('/'),
        (e: jatatui.react.KeyEvent) => {
          if (!searchOpen.get) {
            searchOpen.set(true)
            e.stopPropagation()
          }
        }
      )

      val services = files.flatMap(_.services).sortBy(_.fullName.toLowerCase)
      val messages = files.flatMap(allMessages).sortBy(_.fullName.toLowerCase)
      val enums = (files.flatMap(_.enums) ::: messages.flatMap(_.nestedEnums)).sortBy(_.fullName.toLowerCase)

      val rows: List[TreeRow] = treeRows(services, messages, enums, expanded.get)

      val anchorIdx: Map[String, Int] =
        rows.zipWithIndex.collect {
          case (TreeRow.ServiceRow(s, _), i) => serviceKey(s) -> i
          case (TreeRow.MessageRow(m, _), i) => messageKey(m) -> i
          case (TreeRow.EnumRow(e, _), i)    => enumKeyOf(e) -> i
        }.toMap

      val selectedIdx = ctx.useState(() =>
        rows.zipWithIndex
          .collectFirst { case (TreeRow.ServiceRow(_, _), i) => i }
          .getOrElse(
            rows.zipWithIndex.collectFirst { case (TreeRow.MessageRow(_, _), i) => i }.getOrElse(0)
          )
      )

      ctx.onKey(
        new KeyCode.Right,
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case TreeRow.ServiceRow(s, false)                             => expanded.update(_ + serviceKey(s)); e.stopPropagation()
            case TreeRow.ServiceRow(_, true) if hasChildAt(rows, sel + 1) => selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.MessageRow(m, false)                             => expanded.update(_ + messageKey(m)); e.stopPropagation()
            case TreeRow.MessageRow(_, true) if hasChildAt(rows, sel + 1) => selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.EnumRow(en, false)                               => expanded.update(_ + enumKeyOf(en)); e.stopPropagation()
            case TreeRow.EnumRow(_, true) if hasChildAt(rows, sel + 1)    => selectedIdx.set(sel + 1); e.stopPropagation()
            case _                                                        => ()
          }
        }
      )
      ctx.onKey(
        new KeyCode.Left,
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case TreeRow.ServiceRow(s, true)                                                                => expanded.update(_ - serviceKey(s)); e.stopPropagation()
            case TreeRow.MessageRow(m, true)                                                                => expanded.update(_ - messageKey(m)); e.stopPropagation()
            case TreeRow.EnumRow(en, true)                                                                  => expanded.update(_ - enumKeyOf(en)); e.stopPropagation()
            case _: TreeRow.MethodRow | _: TreeRow.FieldRow | _: TreeRow.EnumValueRow | _: TreeRow.OneofRow =>
              val parent = (sel - 1 to 0 by -1).find(i =>
                rows(i).isInstanceOf[TreeRow.ServiceRow]
                  || rows(i).isInstanceOf[TreeRow.MessageRow]
                  || rows(i).isInstanceOf[TreeRow.EnumRow]
              )
              parent.foreach(selectedIdx.set)
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      // 'c' on a Message: create a DomainType from this proto message.
      ctx.onKey(
        new KeyCode.Char('c'),
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case _: TreeRow.MessageRow =>
              router.push(RouterScreen.of("create domain type", DomainFromEntity.element))
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      val searchItems = buildIndex(services, messages, enums)
      val indexedRef = ctx.useRef(() => FuzzyMatch.index[SearchItem](searchItems.asJava, (it: SearchItem) => it.label))

      val methodCount = services.map(_.methods.size).sum
      val info = Line.from(
        Span.styled(s"  ${files.size} file${plural(files.size)}", sectionStyle),
        Span.styled(s"  ·  ${services.size} service${plural(services.size)}", infoStyle),
        Span.styled(s"  ·  $methodCount method${plural(methodCount)}", infoStyle),
        Span.styled(s"  ·  ${messages.size} message${plural(messages.size)}", infoStyle),
        Span.styled(s"  ·  ${enums.size} enum${plural(enums.size)}", infoStyle),
        Span.styled(s"  ·  press / to search", hintStyle)
      )
      val infoLine: Widget = (area, buffer) => Paragraph.of(info).render(area, buffer)

      val tree = column(
        length(1, widget(infoLine)),
        length(1, empty()),
        fill(
          1,
          SelectableList.of(
            SelectableListProps
              .of[TreeRow](
                rows.asJava,
                isActivatable,
                (row, selected) => renderTreeRow(row, selected, key => expanded.update(t => toggle(t, key))),
                selectedIdx.get,
                idx => selectedIdx.set(idx)
              )
              .withOnActivate { (row: TreeRow) =>
                row match {
                  case TreeRow.ServiceRow(s, _) => expanded.update(t => toggle(t, serviceKey(s)))
                  case TreeRow.MessageRow(m, _) => expanded.update(t => toggle(t, messageKey(m)))
                  case TreeRow.EnumRow(en, _)   => expanded.update(t => toggle(t, enumKeyOf(en)))
                  case _                        => ()
                }
              }
              .withAutoFocus(true)
          )
        ),
        length(1, text("  ↑↓ navigate · ←→ collapse/expand · c create domain type · / search · esc back", hintStyle))
      )

      if (!searchOpen.get) tree
      else {
        val filter: PickerProps.Filter[SearchItem] = (query: String) =>
          if (query.isEmpty) java.util.List.of()
          else FuzzyMatch.rank(query, indexedRef.get).stream.limit(120).toList
        val rowRenderer: PickerProps.RowRenderer[SearchItem] = (item, selected) => hitRow(item, selected)
        stack(
          tree,
          Picker.of(
            PickerProps
              .of[SearchItem](
                "search",
                filter,
                rowRenderer,
                (item: SearchItem) => {
                  expanded.update(_ + item.anchorKey)
                  anchorIdx.get(item.anchorKey).foreach(selectedIdx.set)
                  searchOpen.set(false)
                },
                () => searchOpen.set(false)
              )
          )
        )
      }
    }

  // ───────────────────────────────────── tree building ─────────────────────────────────────────

  /** Flatten nested messages into a single list so they show up as top-level. We still note the parent via fullName so the tree is searchable.
    */
  private def allMessages(file: ProtoFile): List[ProtoMessage] = {
    def walk(msg: ProtoMessage): List[ProtoMessage] =
      msg :: msg.nestedMessages.flatMap(walk)
    file.messages.flatMap(walk).filterNot(_.isMapEntry)
  }

  private def treeRows(
      services: List[ProtoService],
      messages: List[ProtoMessage],
      enums: List[ProtoEnum],
      expanded: Set[String]
  ): List[TreeRow] = {
    val out = List.newBuilder[TreeRow]
    if (services.nonEmpty) {
      out += TreeRow.SectionHeader(s"Services (${services.size})")
      services.foreach { s =>
        val key = serviceKey(s)
        val isOpen = expanded.contains(key)
        out += TreeRow.ServiceRow(s, isOpen)
        if (isOpen) s.methods.foreach(m => out += TreeRow.MethodRow(m))
      }
      out += TreeRow.Blank
    }
    if (messages.nonEmpty) {
      out += TreeRow.SectionHeader(s"Messages (${messages.size})")
      messages.foreach { m =>
        val key = messageKey(m)
        val isOpen = expanded.contains(key)
        out += TreeRow.MessageRow(m, isOpen)
        if (isOpen) {
          val rendered = m.fields.map(f => (f.name, f.number, renderType(f.fieldType), labelText(f), oneofFor(f, m)))
          val nameWidth = rendered.map(_._1.length).maxOption.getOrElse(0)
          val typeWidth = rendered.map(_._3.length).maxOption.getOrElse(0)
          // Render oneof groups inline above their fields by walking ordered list.
          val seenOneofs = scala.collection.mutable.Set.empty[String]
          rendered.foreach { case (n, num, t, lbl, oneof) =>
            oneof match {
              case Some(g) if !seenOneofs.contains(g) =>
                seenOneofs += g
                out += TreeRow.OneofRow(g)
                out += TreeRow.FieldRow(n, num, t, lbl, Some(g), nameWidth, typeWidth)
              case Some(g) =>
                out += TreeRow.FieldRow(n, num, t, lbl, Some(g), nameWidth, typeWidth)
              case None =>
                out += TreeRow.FieldRow(n, num, t, lbl, None, nameWidth, typeWidth)
            }
          }
        }
      }
      out += TreeRow.Blank
    }
    if (enums.nonEmpty) {
      out += TreeRow.SectionHeader(s"Enums (${enums.size})")
      enums.foreach { en =>
        val key = enumKeyOf(en)
        val isOpen = expanded.contains(key)
        out += TreeRow.EnumRow(en, isOpen)
        if (isOpen) en.values.foreach(v => out += TreeRow.EnumValueRow(v.name, v.number))
      }
      out += TreeRow.Blank
    }
    out.result()
  }

  private def oneofFor(field: ProtoField, msg: ProtoMessage): Option[String] =
    field.oneofIndex
      .filter(idx => idx >= 0 && idx < msg.oneofs.size)
      .map(idx => msg.oneofs(idx).name)

  // ───────────────────────────────────── row rendering ─────────────────────────────────────────

  private def renderTreeRow(row: TreeRow, selected: Boolean, toggleExpand: String => Unit): Element = row match {
    case TreeRow.SectionHeader(label) =>
      text(s"  $label", sectionStyle)

    case TreeRow.Blank => empty()

    case TreeRow.ServiceRow(s, isOpen) =>
      val nameStyle = if (selected) serviceStyle.withBg(Color.BLUE) else serviceStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(s.fullName, nameStyle),
          Span.styled(s"  service  (${s.methods.size} RPCs)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(serviceKey(s)))),
        fill(1, widget(w))
      )

    case TreeRow.MethodRow(m) =>
      val (label, style) = rpcLabel(m)
      val sigStyle =
        if (selected) Style.empty.withFg(Color.WHITE).withBg(Color.BLUE).withAddModifier(Modifier.BOLD)
        else Style.empty.withFg(Color.WHITE)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ", Style.empty),
          Span.styled(padLeft(label, 8), style.withAddModifier(Modifier.BOLD)),
          Span.styled("  ", Style.empty),
          Span.styled(m.name, sigStyle),
          Span.styled(s"  ${shortName(m.inputType)} → ${shortName(m.outputType)}", Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY))
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.MessageRow(m, isOpen) =>
      val nameStyle = if (selected) messageStyle.withBg(Color.BLUE) else messageStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(m.fullName, nameStyle),
          Span.styled(s"  message  (${m.fields.size} fields)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(messageKey(m)))),
        fill(1, widget(w))
      )

    case TreeRow.EnumRow(en, isOpen) =>
      val nameStyle = if (selected) enumStyle.withBg(Color.BLUE) else enumStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(en.fullName, nameStyle),
          Span.styled(s"  enum  (${en.values.size} values)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(enumKeyOf(en)))),
        fill(1, widget(w))
      )

    case TreeRow.OneofRow(name) =>
      text(s"        ─ oneof $name ─", oneofStyle)

    case TreeRow.FieldRow(name, number, tpe, label, _, nameWidth, typeWidth) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("          ", Style.empty),
          Span.styled(padRight(s"$number.", 4), Style.empty.withFg(Color.DARK_GRAY)),
          Span.styled(name.padTo(nameWidth, ' '), fieldNameStyle),
          Span.styled("  ", Style.empty),
          Span.styled(tpe.padTo(typeWidth, ' '), fieldTypeStyle),
          Span.styled(if (label.isEmpty) "" else s"  $label", Style.empty.withFg(Color.DARK_GRAY))
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.EnumValueRow(name, number) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        · ", Style.empty.withFg(Color.DARK_GRAY)),
          Span.styled(name, fieldTypeStyle),
          Span.styled(s"  = $number", Style.empty.withFg(Color.DARK_GRAY))
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)
  }

  private def isActivatable(row: TreeRow): Boolean = row match {
    case _: TreeRow.SectionHeader => false
    case _: TreeRow.OneofRow      => false
    case TreeRow.Blank            => false
    case _                        => true
  }

  private def hasChildAt(rows: List[TreeRow], idx: Int): Boolean =
    idx >= 0 && idx < rows.size && (rows(idx) match {
      case _: TreeRow.MethodRow | _: TreeRow.FieldRow | _: TreeRow.EnumValueRow | _: TreeRow.OneofRow => true
      case _                                                                                          => false
    })

  // ───────────────────────────────────── search index ─────────────────────────────────────────

  private def buildIndex(
      services: List[ProtoService],
      messages: List[ProtoMessage],
      enums: List[ProtoEnum]
  ): List[SearchItem] = {
    val b = List.newBuilder[SearchItem]
    services.foreach { s =>
      b += SearchItem(s"service: ${s.fullName}", SearchKind.Service, serviceKey(s))
      s.methods.foreach { m =>
        b += SearchItem(s"${s.fullName}.${m.name} (${shortName(m.inputType)} → ${shortName(m.outputType)})", SearchKind.Method, serviceKey(s))
      }
    }
    messages.foreach { m =>
      b += SearchItem(s"message: ${m.fullName}", SearchKind.Message, messageKey(m))
      m.fields.foreach { f =>
        b += SearchItem(s"${m.fullName}.${f.name} : ${renderType(f.fieldType)}", SearchKind.Field, messageKey(m))
      }
    }
    enums.foreach { en =>
      b += SearchItem(s"enum: ${en.fullName}", SearchKind.Enum, enumKeyOf(en))
      en.values.foreach(v => b += SearchItem(s"${en.fullName}.${v.name}", SearchKind.Value, enumKeyOf(en)))
    }
    b.result()
  }

  private def hitRow(item: SearchItem, selected: Boolean): Element = {
    val (icon, color) = item.kind match {
      case SearchKind.Service => ("◈", Color.CYAN)
      case SearchKind.Method  => ("→", Color.WHITE)
      case SearchKind.Message => ("▦", Color.YELLOW)
      case SearchKind.Enum    => ("∪", Color.MAGENTA)
      case SearchKind.Field   => ("·", Color.WHITE)
      case SearchKind.Value   => ("·", Color.GREEN)
    }
    val style =
      if (selected) Style.empty.withBg(Color.BLUE).withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
      else Style.empty.withFg(color)
    val prefix = if (selected) "  ▶ " else "    "
    text(s"$prefix$icon  ${item.label}", style)
  }

  // ───────────────────────────────────── keys + labels ─────────────────────────────────────────

  private def serviceKey(s: ProtoService): String = s"svc:${s.fullName}"
  private def messageKey(m: ProtoMessage): String = s"msg:${m.fullName}"
  private def enumKeyOf(en: ProtoEnum): String = s"enum:${en.fullName}"

  private def labelText(field: ProtoField): String = {
    val parts = List.newBuilder[String]
    if (field.isRepeated) parts += "repeated"
    if (field.isMapField) parts += "map"
    if (field.proto3Optional) parts += "optional"
    field.label match {
      case ProtoFieldLabel.Required if !field.isRepeated => parts += "required"
      case _                                             => ()
    }
    parts.result().mkString(" ")
  }

  private def rpcLabel(m: ProtoMethod): (String, Style) = m.rpcPattern match {
    case RpcPattern.Unary           => ("UNARY", Style.empty.withFg(Color.GREEN))
    case RpcPattern.ServerStreaming => ("STREAM→", Style.empty.withFg(Color.YELLOW))
    case RpcPattern.ClientStreaming => ("←STREAM", Style.empty.withFg(Color.BLUE))
    case RpcPattern.BidiStreaming   => ("↔STREAM", Style.empty.withFg(Color.MAGENTA))
  }

  private def shortName(fullName: String): String =
    fullName.split('.').lastOption.getOrElse(fullName)

  private def renderType(t: ProtoType): String = t match {
    case ProtoType.Double      => "double"
    case ProtoType.Float       => "float"
    case ProtoType.Int32       => "int32"
    case ProtoType.Int64       => "int64"
    case ProtoType.UInt32      => "uint32"
    case ProtoType.UInt64      => "uint64"
    case ProtoType.SInt32      => "sint32"
    case ProtoType.SInt64      => "sint64"
    case ProtoType.Fixed32     => "fixed32"
    case ProtoType.Fixed64     => "fixed64"
    case ProtoType.SFixed32    => "sfixed32"
    case ProtoType.SFixed64    => "sfixed64"
    case ProtoType.Bool        => "bool"
    case ProtoType.String      => "string"
    case ProtoType.Bytes       => "bytes"
    case ProtoType.Message(fn) => shortName(fn)
    case ProtoType.Enum(fn)    => shortName(fn)
    case ProtoType.Map(k, v)   => s"map<${renderType(k)}, ${renderType(v)}>"
    case ProtoType.Timestamp   => "Timestamp"
    case ProtoType.Duration    => "Duration"
    case ProtoType.StringValue => "StringValue?"
    case ProtoType.Int32Value  => "Int32Value?"
    case ProtoType.Int64Value  => "Int64Value?"
    case ProtoType.UInt32Value => "UInt32Value?"
    case ProtoType.UInt64Value => "UInt64Value?"
    case ProtoType.FloatValue  => "FloatValue?"
    case ProtoType.DoubleValue => "DoubleValue?"
    case ProtoType.BoolValue   => "BoolValue?"
    case ProtoType.BytesValue  => "BytesValue?"
    case ProtoType.Any         => "Any"
    case ProtoType.Struct      => "Struct"
    case other                 => other.toString.takeWhile(_ != '(')
  }

  private def toggle(s: Set[String], k: String): Set[String] = if (s.contains(k)) s - k else s + k
  private def plural(n: Int): String = if (n == 1) "" else "s"
  private def padLeft(s: String, n: Int): String = if (s.length >= n) s else " " * (n - s.length) + s
  private def padRight(s: String, n: Int): String = if (s.length >= n) s else s + " " * (n - s.length)
}
