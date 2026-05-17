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
import typr.avro.*
import typr.cli.app.{AppApi, LoadStatus, LoadedSource}
import typr.cli.app.components.CaretCell

import scala.jdk.CollectionConverters.*

/** Per-source Avro schema browser. Mirrors [[SpecBrowser]]: background-fetched, cached, picker search, expandable tree.
  *
  * The tree is grouped by directory: each [[AvroSchemaFile.directoryGroup]] becomes a section header, with its primary schemas listed underneath. Inline schemas live alongside their primary so the
  * eye can see what nested types a record introduces. Records expand into a list of fields with type renderings; enums into their symbols; fixed into a one-line summary.
  */
object AvroBrowser {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.GRAY)
  private val sectionStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val recordStyle: Style = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
  private val enumStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val fixedStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val errorStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val fieldNameStyle: Style = Style.empty.withFg(Color.CYAN)
  private val fieldTypeStyle: Style = Style.empty.withFg(Color.YELLOW)

  sealed trait TreeRow
  object TreeRow {
    final case class SectionHeader(label: String) extends TreeRow
    final case class RecordRow(rec: AvroRecord, isOpen: Boolean) extends TreeRow
    final case class EnumRow(e: AvroEnum, isOpen: Boolean) extends TreeRow
    final case class FixedRow(f: AvroFixed) extends TreeRow
    final case class ErrorRow(err: AvroError, isOpen: Boolean) extends TreeRow
    final case class FieldRow(name: String, tpe: String, optional: Boolean, nameWidth: Int, typeWidth: Int) extends TreeRow
    final case class SymbolRow(value: String, isDefault: Boolean) extends TreeRow
    case object Blank extends TreeRow
  }

  private final case class SearchItem(label: String, kind: SearchKind, anchorKey: String)
  private sealed trait SearchKind
  private object SearchKind {
    case object Record extends SearchKind
    case object Enum extends SearchKind
    case object Fixed extends SearchKind
    case object Error extends SearchKind
    case object Field extends SearchKind
    case object Symbol extends SearchKind
  }

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val title = column(length(1, empty()), length(1, text(s"  avro · $name", titleStyle)))
      val status = app.loadStatus.getOrElse(name, LoadStatus.NotLoaded)
      val body = app.sourceCache.get(name) match {
        case Some(LoadedSource.Avro(files)) => loadedBody(files)
        case Some(_)                        => failedBody(s"source '$name' is not an avro source")
        case None                           =>
          status match {
            case LoadStatus.Failed(err) => failedBody(err)
            case LoadStatus.NotLoaded   => notInConfigBody(name)
            case _                      => loadingBody()
          }
      }
      ScreenFrame.withTitle("schemas", back, title, body)
    }

  private def loadingBody(): Element = column(
    length(1, text("  parsing avro schemas…", Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD))),
    length(1, empty()),
    length(1, text("  walking directories, resolving $refs, building schemas.", infoStyle)),
    fill(1, empty())
  )

  private def failedBody(err: String): Element = column(
    length(1, text(s"  ✗ ${err.take(400)}", errStyle)),
    length(1, empty()),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def notInConfigBody(name: String): Element = column(
    length(1, text(s"  source '$name' not in config", errStyle)),
    length(1, empty()),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def loadedBody(files: List[AvroSchemaFile]): Element =
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

      val schemas = allSchemas(files)
      val rows: List[TreeRow] = treeRows(files, schemas, expanded.get)

      val anchorIdx: Map[String, Int] =
        rows.zipWithIndex.collect {
          case (TreeRow.RecordRow(r, _), i) => recordKey(r) -> i
          case (TreeRow.EnumRow(e, _), i)   => enumKey(e) -> i
          case (TreeRow.FixedRow(f), i)     => fixedKey(f) -> i
          case (TreeRow.ErrorRow(er, _), i) => errorKey(er) -> i
        }.toMap

      val selectedIdx = ctx.useState(() =>
        rows.zipWithIndex
          .collectFirst { case (TreeRow.RecordRow(_, _), i) => i }
          .getOrElse(
            rows.zipWithIndex.collectFirst { case (TreeRow.EnumRow(_, _), i) => i }.getOrElse(0)
          )
      )

      ctx.onKey(
        new KeyCode.Right,
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case TreeRow.RecordRow(r, false) =>
              expanded.update(_ + recordKey(r)); e.stopPropagation()
            case TreeRow.RecordRow(_, true) if hasChildAt(rows, sel + 1) =>
              selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.EnumRow(en, false) =>
              expanded.update(_ + enumKey(en)); e.stopPropagation()
            case TreeRow.EnumRow(_, true) if hasChildAt(rows, sel + 1) =>
              selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.ErrorRow(er, false) =>
              expanded.update(_ + errorKey(er)); e.stopPropagation()
            case TreeRow.ErrorRow(_, true) if hasChildAt(rows, sel + 1) =>
              selectedIdx.set(sel + 1); e.stopPropagation()
            case _ => ()
          }
        }
      )
      ctx.onKey(
        new KeyCode.Left,
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case TreeRow.RecordRow(r, true) =>
              expanded.update(_ - recordKey(r)); e.stopPropagation()
            case TreeRow.EnumRow(en, true) =>
              expanded.update(_ - enumKey(en)); e.stopPropagation()
            case TreeRow.ErrorRow(er, true) =>
              expanded.update(_ - errorKey(er)); e.stopPropagation()
            case _: TreeRow.FieldRow | _: TreeRow.SymbolRow =>
              val parent = (sel - 1 to 0 by -1).find(i =>
                rows(i).isInstanceOf[TreeRow.RecordRow]
                  || rows(i).isInstanceOf[TreeRow.EnumRow]
                  || rows(i).isInstanceOf[TreeRow.ErrorRow]
              )
              parent.foreach(selectedIdx.set)
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      // 'c' on a Record / Error: create a DomainType anchored on this avro record. (FieldType
      // doesn't have an avro-specific match pattern, so there's no 'e' shortcut here.)
      ctx.onKey(
        new KeyCode.Char('c'),
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case _: TreeRow.RecordRow | _: TreeRow.ErrorRow =>
              router.push(RouterScreen.of("create domain type", DomainFromEntity.element))
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      val searchItems = buildIndex(schemas)
      val indexedRef = ctx.useRef(() => FuzzyMatch.index[SearchItem](searchItems.asJava, (it: SearchItem) => it.label))

      val recCount = schemas.collect { case r: AvroRecord => r }.size
      val enumCount = schemas.collect { case e: AvroEnum => e }.size
      val fixedCount = schemas.collect { case f: AvroFixed => f }.size
      val errCount = schemas.collect { case e: AvroError => e }.size

      val info = Line.from(
        Span.styled(s"  ${files.size} file${plural(files.size)}", sectionStyle),
        Span.styled(s"  ·  $recCount record${plural(recCount)}", infoStyle),
        Span.styled(s"  ·  $enumCount enum${plural(enumCount)}", infoStyle),
        Span.styled(s"  ·  $fixedCount fixed", infoStyle),
        Span.styled(if (errCount > 0) s"  ·  $errCount error${plural(errCount)}" else "", infoStyle),
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
                  case TreeRow.RecordRow(r, _) =>
                    expanded.update(t => toggle(t, recordKey(r)))
                  case TreeRow.EnumRow(en, _) =>
                    expanded.update(t => toggle(t, enumKey(en)))
                  case TreeRow.ErrorRow(er, _) =>
                    expanded.update(t => toggle(t, errorKey(er)))
                  case _ => ()
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

  private def allSchemas(files: List[AvroSchemaFile]): List[AvroSchema] =
    files.flatMap(f => f.primarySchema :: f.inlineSchemas)

  private def treeRows(files: List[AvroSchemaFile], schemas: List[AvroSchema], expanded: Set[String]): List[TreeRow] = {
    val out = List.newBuilder[TreeRow]
    val grouped: List[(Option[String], List[AvroSchemaFile])] =
      files.groupBy(_.directoryGroup).toList.sortBy(_._1.getOrElse(""))

    grouped.foreach { case (group, gFiles) =>
      val label = group.map(g => s"$g/").getOrElse("(root)")
      out += TreeRow.SectionHeader(s"$label  ${gFiles.size} file${plural(gFiles.size)}")
      // For each file, append primary + inline schemas
      val sorted = gFiles.sortBy(_.primarySchema.fullName.toLowerCase)
      sorted.foreach { file =>
        val ordered = file.primarySchema :: file.inlineSchemas
        ordered.foreach {
          case r: AvroRecord =>
            val key = recordKey(r)
            val isOpen = expanded.contains(key)
            out += TreeRow.RecordRow(r, isOpen)
            if (isOpen) fieldRowsFor(r.fields).foreach(out += _)
          case en: AvroEnum =>
            val key = enumKey(en)
            val isOpen = expanded.contains(key)
            out += TreeRow.EnumRow(en, isOpen)
            if (isOpen) en.symbols.foreach(s => out += TreeRow.SymbolRow(s, en.defaultSymbol.contains(s)))
          case f: AvroFixed =>
            out += TreeRow.FixedRow(f)
          case er: AvroError =>
            val key = errorKey(er)
            val isOpen = expanded.contains(key)
            out += TreeRow.ErrorRow(er, isOpen)
            if (isOpen) fieldRowsFor(er.fields).foreach(out += _)
        }
      }
      out += TreeRow.Blank
    }
    val _ = schemas // explicit to silence unused warning when index changes
    out.result()
  }

  private def fieldRowsFor(fields: List[AvroField]): List[TreeRow] = {
    val rendered = fields.map(f => (f.name, renderType(f.fieldType), f.isOptional))
    val nameWidth = rendered.map(_._1.length).maxOption.getOrElse(0)
    val typeWidth = rendered.map(_._2.length).maxOption.getOrElse(0)
    rendered.map { case (n, t, opt) => TreeRow.FieldRow(n, t, opt, nameWidth, typeWidth) }
  }

  // ───────────────────────────────────── row rendering ─────────────────────────────────────────

  private def renderTreeRow(row: TreeRow, selected: Boolean, toggleExpand: String => Unit): Element = row match {
    case TreeRow.SectionHeader(label) =>
      text(s"  $label", sectionStyle)

    case TreeRow.Blank => empty()

    case TreeRow.RecordRow(r, isOpen) =>
      val nameStyle = if (selected) recordStyle.withBg(Color.BLUE) else recordStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(r.fullName, nameStyle),
          Span.styled(s"  record  (${r.fields.size} fields)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(recordKey(r)))),
        fill(1, widget(w))
      )

    case TreeRow.EnumRow(e, isOpen) =>
      val nameStyle = if (selected) enumStyle.withBg(Color.BLUE) else enumStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(e.fullName, nameStyle),
          Span.styled(s"  enum  (${e.symbols.size} values)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(enumKey(e)))),
        fill(1, widget(w))
      )

    case TreeRow.FixedRow(f) =>
      val nameStyle = if (selected) fixedStyle.withBg(Color.BLUE) else fixedStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("    ▪ ", Style.empty.withFg(if (selected) Color.CYAN else Color.DARK_GRAY)),
          Span.styled(f.fullName, nameStyle),
          Span.styled(s"  fixed  (${f.size} bytes)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.ErrorRow(er, isOpen) =>
      val nameStyle = if (selected) errorStyle.withBg(Color.BLUE) else errorStyle
      val tagStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(er.namespace.map(ns => s"$ns.${er.name}").getOrElse(er.name), nameStyle),
          Span.styled(s"  error  (${er.fields.size} fields)", tagStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(errorKey(er)))),
        fill(1, widget(w))
      )

    case TreeRow.FieldRow(name, tpe, optional, nameWidth, typeWidth) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ", Style.empty),
          Span.styled(name.padTo(nameWidth, ' '), fieldNameStyle),
          Span.styled("  ", Style.empty),
          Span.styled(tpe.padTo(typeWidth, ' '), fieldTypeStyle),
          Span.styled(if (optional) "  optional" else "  required", Style.empty.withFg(Color.DARK_GRAY))
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.SymbolRow(value, isDefault) =>
      val w: Widget = (area, buffer) => {
        val parts = List.newBuilder[Span]
        parts += Span.styled("        · ", Style.empty.withFg(Color.DARK_GRAY))
        parts += Span.styled(value, fieldTypeStyle)
        if (isDefault) parts += Span.styled("  (default)", Style.empty.withFg(Color.GREEN))
        val line = Line.from(parts.result()*)
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)
  }

  private def isActivatable(row: TreeRow): Boolean = row match {
    case _: TreeRow.SectionHeader => false
    case TreeRow.Blank            => false
    case _                        => true
  }

  private def hasChildAt(rows: List[TreeRow], idx: Int): Boolean =
    idx >= 0 && idx < rows.size && (rows(idx) match {
      case _: TreeRow.FieldRow | _: TreeRow.SymbolRow => true
      case _                                          => false
    })

  // ───────────────────────────────────── search index ─────────────────────────────────────────

  private def buildIndex(schemas: List[AvroSchema]): List[SearchItem] = {
    val b = List.newBuilder[SearchItem]
    schemas.foreach {
      case r: AvroRecord =>
        b += SearchItem(s"record: ${r.fullName}", SearchKind.Record, recordKey(r))
        r.fields.foreach { f =>
          b += SearchItem(s"${r.fullName}.${f.name} : ${renderType(f.fieldType)}", SearchKind.Field, recordKey(r))
        }
      case e: AvroEnum =>
        b += SearchItem(s"enum: ${e.fullName}", SearchKind.Enum, enumKey(e))
        e.symbols.foreach(s => b += SearchItem(s"${e.fullName}.$s", SearchKind.Symbol, enumKey(e)))
      case f: AvroFixed =>
        b += SearchItem(s"fixed: ${f.fullName}", SearchKind.Fixed, fixedKey(f))
      case er: AvroError =>
        b += SearchItem(s"error: ${er.fullName}", SearchKind.Error, errorKey(er))
        er.fields.foreach { f =>
          b += SearchItem(s"${er.fullName}.${f.name} : ${renderType(f.fieldType)}", SearchKind.Field, errorKey(er))
        }
    }
    b.result()
  }

  private def hitRow(item: SearchItem, selected: Boolean): Element = {
    val (icon, color) = item.kind match {
      case SearchKind.Record => ("◈", Color.CYAN)
      case SearchKind.Enum   => ("▦", Color.MAGENTA)
      case SearchKind.Fixed  => ("▪", Color.YELLOW)
      case SearchKind.Error  => ("✗", Color.RED)
      case SearchKind.Field  => ("·", Color.WHITE)
      case SearchKind.Symbol => ("·", Color.GREEN)
    }
    val style =
      if (selected) Style.empty.withBg(Color.BLUE).withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
      else Style.empty.withFg(color)
    val prefix = if (selected) "  ▶ " else "    "
    text(s"$prefix$icon  ${item.label}", style)
  }

  // ───────────────────────────────────── keys + labels ─────────────────────────────────────────

  private def recordKey(r: AvroRecord): String = s"rec:${r.fullName}"
  private def enumKey(e: AvroEnum): String = s"enum:${e.fullName}"
  private def fixedKey(f: AvroFixed): String = s"fix:${f.fullName}"
  private def errorKey(er: AvroError): String = s"err:${er.fullName}"

  private def renderType(t: AvroType): String = t match {
    case AvroType.Null           => "null"
    case AvroType.Boolean        => "boolean"
    case AvroType.Int            => "int"
    case AvroType.Long           => "long"
    case AvroType.Float          => "float"
    case AvroType.Double         => "double"
    case AvroType.Bytes          => "bytes"
    case AvroType.String         => "string"
    case AvroType.Array(items)   => s"array[${renderType(items)}]"
    case AvroType.Map(values)    => s"map[string, ${renderType(values)}]"
    case AvroType.Union(members) =>
      AvroType.unwrapNullable(AvroType.Union(members)) match {
        case Some(inner) => s"${renderType(inner)}?"
        case None        => members.map(renderType).mkString(" | ")
      }
    case AvroType.Named(fullName)       => fullName
    case AvroType.Record(r)             => r.fullName
    case AvroType.EnumType(en)          => en.fullName
    case AvroType.Fixed(f)              => f.fullName
    case AvroType.UUID                  => "uuid"
    case AvroType.Date                  => "date"
    case AvroType.TimeMillis            => "time-ms"
    case AvroType.TimeMicros            => "time-us"
    case AvroType.TimestampMillis       => "timestamp-ms"
    case AvroType.TimestampMicros       => "timestamp-us"
    case AvroType.LocalTimestampMillis  => "local-timestamp-ms"
    case AvroType.LocalTimestampMicros  => "local-timestamp-us"
    case AvroType.TimeNanos             => "time-ns"
    case AvroType.TimestampNanos        => "timestamp-ns"
    case AvroType.LocalTimestampNanos   => "local-timestamp-ns"
    case AvroType.DecimalBytes(p, s)    => s"decimal($p,$s)"
    case AvroType.DecimalFixed(p, s, _) => s"decimal($p,$s)"
    case AvroType.Duration              => "duration"
  }

  private def toggle(s: Set[String], k: String): Set[String] = if (s.contains(k)) s - k else s + k
  private def plural(n: Int): String = if (n == 1) "" else "s"
}
