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
import typr.cli.app.{AppApi, Entity, LoadStatus, LoadedSource}
import typr.cli.app.components.CaretCell
import typr.config.generated.{BridgeType, FieldType, ModelMatch, StringOrArrayString}
import typr.openapi.*

import scala.jdk.CollectionConverters.*

/** Per-source OpenAPI / JSON-schema spec browser. Mirrors [[SchemaBrowser]] in shape:
  *
  *   - On mount, look up [[AppApi.specCache]]; on miss, spawn a daemon to call [[SpecFetch.fetch]] and store the result.
  *   - Render a flat heterogeneous tree via [[jatatui.components.selectablelist.SelectableList]] — two sections, APIs and Models, each containing collapsible children (operations under an interface,
  *     properties under a model).
  *   - `/` opens a [[jatatui.components.picker.Picker]] with IntelliJ-style fuzzy ranking over interfaces, operations, models, sum types, and properties.
  *   - Up/Down navigates activatable rows, Left/Right collapses/expands or jumps in/out of a group, Enter toggles, Esc closes search or returns.
  *
  * HTTP methods are color-coded so the eye can scan a long operation list quickly. Models, sum types, and properties get their own visual treatment.
  */
object SpecBrowser {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.GRAY)
  private val sectionStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val apiNameStyle: Style = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
  private val modelStyle: Style = Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
  private val propNameStyle: Style = Style.empty.withFg(Color.CYAN)
  private val propTypeStyle: Style = Style.empty.withFg(Color.YELLOW)
  private val deprecatedStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.CROSSED_OUT)

  sealed trait TreeRow
  object TreeRow {
    final case class SectionHeader(label: String) extends TreeRow
    final case class ApiInterfaceRow(api: ApiInterface, isOpen: Boolean) extends TreeRow
    final case class ApiMethodRow(method: ApiMethod, indent: Int) extends TreeRow
    final case class ModelRow(model: ModelClass, isOpen: Boolean) extends TreeRow
    final case class SumTypeRow(sum: SumType, isOpen: Boolean) extends TreeRow
    final case class PropertyRow(name: String, tpe: String, required: Boolean, nullable: Boolean, nameWidth: Int, typeWidth: Int) extends TreeRow
    final case class EnumValueRow(value: String) extends TreeRow
    final case class SubtypeRow(name: String) extends TreeRow
    case object Blank extends TreeRow
  }

  /** One entry in the fuzzy-search index. `anchorKey` identifies the parent tree node so the host can expand-and-jump after pick.
    */
  private final case class SearchItem(label: String, kind: SearchKind, anchorKey: String)
  private sealed trait SearchKind
  private object SearchKind {
    case object Api extends SearchKind
    case object Method extends SearchKind
    case object Model extends SearchKind
    case object SumT extends SearchKind
    case object Property extends SearchKind
  }

  /** Renders the screen for source `name`. The Shell's startup loader populates [[AppApi.specCache]] in the background; this screen reads from it directly each render and renders whichever stage
    * applies (loading / failed / loaded). No duplicate fetch — the load runs once per source.
    */
  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val title = column(length(1, empty()), length(1, text(s"  spec · $name", titleStyle)))
      val status = app.loadStatus.getOrElse(name, LoadStatus.NotLoaded)
      val body = app.sourceCache.get(name) match {
        case Some(LoadedSource.Spec(spec)) => loadedBody(name, spec, app, router)
        case Some(_)                       => failedBody(s"source '$name' is not an openapi/jsonschema source")
        case None                          =>
          status match {
            case LoadStatus.Failed(err) => failedBody(err)
            case LoadStatus.NotLoaded   => notInConfigBody(name)
            case _                      => loadingBody()
          }
      }
      ScreenFrame.withTitle("schemas", back, title, body)
    }

  private def loadingBody(): Element = column(
    length(1, text("  parsing spec…", Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD))),
    length(1, empty()),
    length(1, text("  reading file, resolving $refs, extracting models and operations.", infoStyle)),
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

  private def loadedBody(sourceName: String, spec: ParsedSpec, app: AppApi, router: RouterApi): Element =
    component { ctx =>
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

      val rows: List[TreeRow] = treeRows(spec, expanded.get)

      // Quick lookup from anchorKey (set when a search hit lands on a parent) back to row idx so
      // search-commit can position selection on the right tree node.
      val anchorIdx: Map[String, Int] =
        rows.zipWithIndex.collect {
          case (TreeRow.ApiInterfaceRow(a, _), i) => apiKey(a) -> i
          case (TreeRow.ApiMethodRow(m, _), i)    => methodKey(m) -> i
          case (TreeRow.ModelRow(m, _), i)        => modelKey(m) -> i
          case (TreeRow.SumTypeRow(s, _), i)      => sumKey(s) -> i
        }.toMap

      val selectedIdx = ctx.useState(() =>
        rows.zipWithIndex
          .collectFirst { case (TreeRow.ApiInterfaceRow(_, _), i) => i }
          .getOrElse(
            rows.zipWithIndex.collectFirst { case (TreeRow.ModelRow(_, _), i) => i }.getOrElse(0)
          )
      )

      ctx.onKey(
        new KeyCode.Right,
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case TreeRow.ApiInterfaceRow(api, false) =>
              expanded.update(_ + apiKey(api)); e.stopPropagation()
            case TreeRow.ApiInterfaceRow(_, true) if hasChildAt(rows, sel + 1) =>
              selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.ModelRow(model, false) =>
              expanded.update(_ + modelKey(model)); e.stopPropagation()
            case TreeRow.ModelRow(_, true) if hasChildAt(rows, sel + 1) =>
              selectedIdx.set(sel + 1); e.stopPropagation()
            case TreeRow.SumTypeRow(sum, false) =>
              expanded.update(_ + sumKey(sum)); e.stopPropagation()
            case TreeRow.SumTypeRow(_, true) if hasChildAt(rows, sel + 1) =>
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
            case TreeRow.ApiInterfaceRow(api, true) =>
              expanded.update(_ - apiKey(api)); e.stopPropagation()
            case TreeRow.ModelRow(model, true) =>
              expanded.update(_ - modelKey(model)); e.stopPropagation()
            case TreeRow.SumTypeRow(sum, true) =>
              expanded.update(_ - sumKey(sum)); e.stopPropagation()
            case _: TreeRow.ApiMethodRow | _: TreeRow.PropertyRow | _: TreeRow.EnumValueRow | _: TreeRow.SubtypeRow =>
              val parent = (sel - 1 to 0 by -1).find(i =>
                rows(i).isInstanceOf[TreeRow.ApiInterfaceRow]
                  || rows(i).isInstanceOf[TreeRow.ModelRow]
                  || rows(i).isInstanceOf[TreeRow.SumTypeRow]
              )
              parent.foreach(selectedIdx.set)
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      // 'e' on a PropertyRow: extract this property as a FieldType. Walks back to find the
      // owning ModelRow so the ModelMatch can scope by schema (model name).
      ctx.onKey(
        new KeyCode.Char('e'),
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case prop: TreeRow.PropertyRow =>
              val parentModel = (sel - 1 to 0 by -1).iterator
                .map(i => rows(i))
                .collectFirst { case TreeRow.ModelRow(m, _) => m }
              parentModel.foreach(m => extractFieldType(sourceName, m, prop, app, router))
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      // 'c' on a Model / SumType / ApiInterface: create a DomainType anchored on a real entity.
      ctx.onKey(
        new KeyCode.Char('c'),
        (e: jatatui.react.KeyEvent) => {
          val sel = selectedIdx.get
          if (sel >= 0 && sel < rows.size) rows(sel) match {
            case _: TreeRow.ModelRow | _: TreeRow.SumTypeRow | _: TreeRow.ApiInterfaceRow =>
              router.push(RouterScreen.of("create domain type", DomainFromEntity.element))
              e.stopPropagation()
            case _ => ()
          }
        }
      )

      val searchItems = buildIndex(spec)
      val indexedRef = ctx.useRef(() => FuzzyMatch.index[SearchItem](searchItems.asJava, (it: SearchItem) => it.label))

      val info = Line.from(
        Span.styled(s"  ${spec.info.title}", sectionStyle),
        Span.styled(s"  v${spec.info.version}", infoStyle),
        Span.styled(s"  ·  ${spec.apis.size} interface${plural(spec.apis.size)}", infoStyle),
        Span.styled(s"  ·  ${countMethods(spec)} operation${plural(countMethods(spec))}", infoStyle),
        Span.styled(s"  ·  ${spec.models.size} model${plural(spec.models.size)}", infoStyle),
        Span.styled(s"  ·  ${spec.sumTypes.size} sum-type${plural(spec.sumTypes.size)}", infoStyle),
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
                  case TreeRow.ApiInterfaceRow(api, _) =>
                    expanded.update(t => toggle(t, apiKey(api)))
                  case TreeRow.ModelRow(model, _) =>
                    expanded.update(t => toggle(t, modelKey(model)))
                  case TreeRow.SumTypeRow(sum, _) =>
                    expanded.update(t => toggle(t, sumKey(sum)))
                  case _ => ()
                }
              }
              .withAutoFocus(true)
          )
        ),
        length(1, text("  ↑↓ navigate · ←→ collapse/expand · e extract field type · c create domain type · / search · esc back", hintStyle))
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
                  // For deep hits (method / property / enum / subtype), open the parent group
                  // first, then jump selection to the anchor.
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

  private def treeRows(spec: ParsedSpec, expanded: Set[String]): List[TreeRow] = {
    val out = List.newBuilder[TreeRow]
    val apis = spec.apis.sortBy(_.name.toLowerCase)
    if (apis.nonEmpty) {
      out += TreeRow.SectionHeader(s"APIs (${apis.size})")
      apis.foreach { api =>
        val key = apiKey(api)
        val isOpen = expanded.contains(key)
        out += TreeRow.ApiInterfaceRow(api, isOpen)
        if (isOpen) api.methods.foreach(m => out += TreeRow.ApiMethodRow(m, indent = 2))
      }
      out += TreeRow.Blank
    }

    val sumTypes = spec.sumTypes.sortBy(_.name.toLowerCase)
    if (sumTypes.nonEmpty) {
      out += TreeRow.SectionHeader(s"Sum types (${sumTypes.size})")
      sumTypes.foreach { sum =>
        val key = sumKey(sum)
        val isOpen = expanded.contains(key)
        out += TreeRow.SumTypeRow(sum, isOpen)
        if (isOpen) sum.subtypeNames.foreach(n => out += TreeRow.SubtypeRow(n))
      }
      out += TreeRow.Blank
    }

    val models = spec.models.sortBy(_.name.toLowerCase)
    if (models.nonEmpty) {
      out += TreeRow.SectionHeader(s"Models (${models.size})")
      models.foreach { model =>
        val key = modelKey(model)
        val isOpen = expanded.contains(key)
        out += TreeRow.ModelRow(model, isOpen)
        if (isOpen) propertyRowsFor(model).foreach(out += _)
      }
      out += TreeRow.Blank
    }

    out.result()
  }

  private def propertyRowsFor(model: ModelClass): List[TreeRow] = model match {
    case obj: ModelClass.ObjectType =>
      val props = obj.properties
      val nameWidth = props.map(_.name.length).maxOption.getOrElse(0)
      val typeWidth = props.map(p => renderTypeInfo(p.typeInfo).length).maxOption.getOrElse(0)
      props.map { p =>
        TreeRow.PropertyRow(
          p.name,
          renderTypeInfo(p.typeInfo),
          p.required,
          p.nullable,
          nameWidth,
          typeWidth
        )
      }
    case e: ModelClass.EnumType =>
      e.values.map(v => TreeRow.EnumValueRow(v))
    case w: ModelClass.WrapperType =>
      List(TreeRow.PropertyRow("(value)", renderTypeInfo(w.underlying), required = true, nullable = false, 7, renderTypeInfo(w.underlying).length))
    case a: ModelClass.AliasType =>
      List(TreeRow.PropertyRow("(alias)", renderTypeInfo(a.underlying), required = true, nullable = false, 7, renderTypeInfo(a.underlying).length))
  }

  // ───────────────────────────────────── row rendering ─────────────────────────────────────────

  private def renderTreeRow(row: TreeRow, selected: Boolean, toggleExpand: String => Unit): Element = row match {
    case TreeRow.SectionHeader(label) =>
      text(s"  $label", sectionStyle)

    case TreeRow.Blank => empty()

    case TreeRow.ApiInterfaceRow(api, isOpen) =>
      val nameStyle = if (selected) apiNameStyle.withBg(Color.BLUE) else apiNameStyle
      val countStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(api.name, nameStyle),
          Span.styled(s"  (${api.methods.size} ops)", countStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(apiKey(api)))),
        fill(1, widget(w))
      )

    case TreeRow.ApiMethodRow(method, _) =>
      val (mLabel, mStyle) = httpMethodLabel(method.httpMethod)
      val pathStyle =
        if (selected) Style.empty.withFg(Color.WHITE).withBg(Color.BLUE).withAddModifier(Modifier.BOLD)
        else Style.empty.withFg(Color.WHITE)
      val opStyle = if (selected) Style.empty.withFg(Color.YELLOW) else Style.empty.withFg(Color.DARK_GRAY)
      val rendered =
        if (method.deprecated)
          deprecatedStyle
        else mStyle
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ", Style.empty),
          Span.styled(padLeft(mLabel, 6), rendered.withAddModifier(Modifier.BOLD)),
          Span.styled("  ", Style.empty),
          Span.styled(method.path, pathStyle),
          Span.styled("  ", Style.empty),
          Span.styled(method.name, opStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.ModelRow(model, isOpen) =>
      val kind = modelKindLabel(model)
      val nameStyle = if (selected) modelStyle.withBg(Color.BLUE) else modelStyle
      val kindStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val countStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val count = model match {
        case o: ModelClass.ObjectType  => s"${o.properties.size} props"
        case e: ModelClass.EnumType    => s"${e.values.size} values"
        case _: ModelClass.WrapperType => "wrapper"
        case _: ModelClass.AliasType   => "alias"
      }
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(model.name, nameStyle),
          Span.styled(s"  [$kind]", kindStyle),
          Span.styled(s"  ($count)", countStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(modelKey(model)))),
        fill(1, widget(w))
      )

    case TreeRow.SumTypeRow(sum, isOpen) =>
      val nameStyle = if (selected) modelStyle.withBg(Color.BLUE) else modelStyle
      val kindStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val countStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(sum.name, nameStyle),
          Span.styled("  [sum]", kindStyle),
          Span.styled(s"  (${sum.subtypeNames.size} variants on ${sum.discriminator.propertyName})", countStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(sumKey(sum)))),
        fill(1, widget(w))
      )

    case TreeRow.PropertyRow(name, tpe, required, nullable, nameWidth, typeWidth) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ", Style.empty),
          Span.styled(name.padTo(nameWidth, ' '), propNameStyle),
          Span.styled("  ", Style.empty),
          Span.styled(tpe.padTo(typeWidth, ' '), propTypeStyle),
          Span.styled(
            (if (required) "  required" else "  optional") + (if (nullable) " · nullable" else ""),
            Style.empty.withFg(Color.DARK_GRAY)
          )
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.EnumValueRow(value) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        · ", Style.empty.withFg(Color.DARK_GRAY)),
          Span.styled(value, propTypeStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case TreeRow.SubtypeRow(name) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ◇ ", Style.empty.withFg(Color.DARK_GRAY)),
          Span.styled(name, propNameStyle)
        )
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
      case _: TreeRow.ApiMethodRow | _: TreeRow.PropertyRow | _: TreeRow.EnumValueRow | _: TreeRow.SubtypeRow => true
      case _                                                                                                  => false
    })

  // ───────────────────────────────────── search index ─────────────────────────────────────────

  private def buildIndex(spec: ParsedSpec): List[SearchItem] = {
    val b = List.newBuilder[SearchItem]
    spec.apis.foreach { api =>
      b += SearchItem(s"API: ${api.name}", SearchKind.Api, apiKey(api))
      api.methods.foreach { m =>
        b += SearchItem(s"${httpMethodText(m.httpMethod)} ${m.path}  ${m.name}", SearchKind.Method, apiKey(api))
      }
    }
    spec.sumTypes.foreach { s =>
      b += SearchItem(s"sum: ${s.name}", SearchKind.SumT, sumKey(s))
      s.subtypeNames.foreach(sub => b += SearchItem(s"${s.name} → $sub", SearchKind.SumT, sumKey(s)))
    }
    spec.models.foreach { m =>
      b += SearchItem(s"${modelKindShort(m)}: ${m.name}", SearchKind.Model, modelKey(m))
      m match {
        case obj: ModelClass.ObjectType =>
          obj.properties.foreach { p =>
            b += SearchItem(s"${m.name}.${p.name} : ${renderTypeInfo(p.typeInfo)}", SearchKind.Property, modelKey(m))
          }
        case e: ModelClass.EnumType =>
          e.values.foreach(v => b += SearchItem(s"${m.name}.$v", SearchKind.Property, modelKey(m)))
        case _ => ()
      }
    }
    b.result()
  }

  private def hitRow(item: SearchItem, selected: Boolean): Element = {
    val (icon, color) = item.kind match {
      case SearchKind.Api      => ("◈", Color.CYAN)
      case SearchKind.Method   => ("→", Color.WHITE)
      case SearchKind.Model    => ("▦", Color.MAGENTA)
      case SearchKind.SumT     => ("∪", Color.MAGENTA)
      case SearchKind.Property => ("·", Color.YELLOW)
    }
    val style =
      if (selected) Style.empty.withBg(Color.BLUE).withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
      else Style.empty.withFg(color)
    val prefix = if (selected) "  ▶ " else "    "
    text(s"$prefix$icon  ${item.label}", style)
  }

  // ───────────────────────────────────── keys + labels ─────────────────────────────────────────

  private def apiKey(a: ApiInterface): String = s"api:${a.name}"
  private def methodKey(m: ApiMethod): String = s"method:${m.name}:${m.path}"
  private def modelKey(m: ModelClass): String = s"model:${m.name}"
  private def sumKey(s: SumType): String = s"sum:${s.name}"

  private def httpMethodLabel(m: HttpMethod): (String, Style) = m match {
    case HttpMethod.Get     => ("GET", Style.empty.withFg(Color.GREEN))
    case HttpMethod.Post    => ("POST", Style.empty.withFg(Color.YELLOW))
    case HttpMethod.Put     => ("PUT", Style.empty.withFg(Color.BLUE))
    case HttpMethod.Delete  => ("DELETE", Style.empty.withFg(Color.RED))
    case HttpMethod.Patch   => ("PATCH", Style.empty.withFg(Color.MAGENTA))
    case HttpMethod.Head    => ("HEAD", Style.empty.withFg(Color.CYAN))
    case HttpMethod.Options => ("OPTIONS", Style.empty.withFg(Color.CYAN))
  }

  private def httpMethodText(m: HttpMethod): String = httpMethodLabel(m)._1

  private def modelKindLabel(m: ModelClass): String = m match {
    case _: ModelClass.ObjectType  => "object"
    case _: ModelClass.EnumType    => "enum"
    case _: ModelClass.WrapperType => "wrapper"
    case _: ModelClass.AliasType   => "alias"
  }

  private def modelKindShort(m: ModelClass): String = m match {
    case _: ModelClass.ObjectType  => "obj"
    case _: ModelClass.EnumType    => "enum"
    case _: ModelClass.WrapperType => "wrap"
    case _: ModelClass.AliasType   => "alias"
  }

  private def renderTypeInfo(t: TypeInfo): String = t match {
    case TypeInfo.Primitive(p)    => primitiveLabel(p)
    case TypeInfo.ListOf(inner)   => s"List[${renderTypeInfo(inner)}]"
    case TypeInfo.Optional(inner) => s"Optional[${renderTypeInfo(inner)}]"
    case TypeInfo.MapOf(k, v)     => s"Map[${renderTypeInfo(k)}, ${renderTypeInfo(v)}]"
    case TypeInfo.Ref(name)       => name
    case TypeInfo.Any             => "Any"
    case TypeInfo.InlineEnum(vs)  => s"enum(${vs.take(3).mkString("|")}${if (vs.size > 3) "…" else ""})"
  }

  private def primitiveLabel(p: PrimitiveType): String = p match {
    case PrimitiveType.String     => "String"
    case PrimitiveType.Int32      => "Int"
    case PrimitiveType.Int64      => "Long"
    case PrimitiveType.Float      => "Float"
    case PrimitiveType.Double     => "Double"
    case PrimitiveType.Boolean    => "Boolean"
    case PrimitiveType.Date       => "Date"
    case PrimitiveType.DateTime   => "DateTime"
    case PrimitiveType.Time       => "Time"
    case PrimitiveType.UUID       => "UUID"
    case PrimitiveType.URI        => "URI"
    case PrimitiveType.Email      => "Email"
    case PrimitiveType.Binary     => "Binary"
    case PrimitiveType.Byte       => "Byte"
    case PrimitiveType.BigDecimal => "BigDecimal"
  }

  private def toggle(s: Set[String], k: String): Set[String] = if (s.contains(k)) s - k else s + k
  private def plural(n: Int): String = if (n == 1) "" else "s"
  private def countMethods(spec: ParsedSpec): Int = spec.apis.map(_.methods.size).sum
  private def padLeft(s: String, n: Int): String = if (s.length >= n) s else " " * (n - s.length) + s

  /** Draft a FieldType from a model property — scoped to this source + model name via ModelMatch — write it to typr.yaml, navigate to TypeEditor for refinement.
    */
  private def extractFieldType(
      sourceName: String,
      model: ModelClass,
      prop: TreeRow.PropertyRow,
      app: AppApi,
      router: RouterApi
  ): Unit = {
    val existing = app.config.types.getOrElse(Map.empty)
    val typeName = uniqueTypeName(Entity.pascalCase(prop.name), existing.keySet)
    val ft = FieldType(
      api = None,
      db = None,
      model = Some(
        ModelMatch(
          `extension` = None,
          format = None,
          json_path = None,
          name = Some(StringOrArrayString(prop.name)),
          required = None,
          schema = Some(StringOrArrayString(model.name)),
          schema_type = None,
          source = Some(StringOrArrayString(sourceName))
        )
      ),
      underlying = Some(prop.tpe),
      validation = None
    )
    val next = existing + (typeName -> (ft: BridgeType))
    val _ = app.updateConfig(c => c.copy(types = Some(next)))
    router.push(RouterScreen.of(s"type · $typeName", TypeEditor.element(typeName)))
  }

  private def uniqueTypeName(suggested: String, existing: Set[String]): String =
    if (!existing.contains(suggested)) suggested
    else LazyList.from(2).map(n => s"$suggested$n").find(n => !existing.contains(n)).get
}
