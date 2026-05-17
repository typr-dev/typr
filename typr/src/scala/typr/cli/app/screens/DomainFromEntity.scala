package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
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
import typr.cli.app.{AppApi, Entity, EntityCatalog, LoadStatus}
import typr.config.generated.{BridgeType, DomainType, FieldSpec, FieldSpecObject}

import scala.jdk.CollectionConverters.*

/** Create a domain type by picking a real entity from a loaded source. Two-column layout:
  *
  *   - **Left**: every entity the [[EntityCatalog]] knows about, grouped by source. Selectable list (Tab/Up/Down move, Enter commits). Sources still loading appear as a single "(loading…)"
  *     placeholder so the user knows more is coming.
  *   - **Right**: live preview of the domain type that would be created from the currently selected entity — name, primary, fields — plus alignment suggestions: other entities from other sources
  *     whose field-name overlap looks like the same concept ("api:Customer — 6 of 7 fields match"). User toggles each suggestion to include it as an `alignedSources` entry.
  *
  * On `Create`, writes a new [[DomainType]] into `types:` and navigates straight to [[TypeEditor]] for any further tweaks. `/` opens a fuzzy picker over entity names if the list gets long.
  */
object DomainFromEntity {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val okStyle: Style = Style.empty.withFg(Color.GREEN).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.GRAY)
  private val sectionStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val mutedStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val previewStyle: Style = Style.empty.withFg(Color.WHITE)

  sealed trait Row
  object Row {
    final case class SourceHeader(label: String, kind: Entity.Kind) extends Row
    final case class EntityRow(entity: Entity) extends Row
    final case class LoadingRow(sourceName: String) extends Row
    final case class FailedRow(sourceName: String, message: String) extends Row
    case object Blank extends Row
  }

  val element: Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      val entities = EntityCatalog.fromCache(app.sourceCache)
      val rows = buildRows(app, entities)

      val firstActivatable = rows.zipWithIndex.collectFirst { case (_: Row.EntityRow, i) => i }
      val selectedIdx = ctx.useState(() => firstActivatable.getOrElse(0))
      val acceptedAligns = ctx.useState(() => Set.empty[String])
      val searchOpen = ctx.useState(() => false)
      val saveErr = ctx.useState(() => Option.empty[String])

      // Reset accepted alignments when the user moves to a different entity, otherwise the
      // checkmarks on the right don't visibly belong to the row on the left.
      val prevSelRef = ctx.useRef(() => -1)
      if (prevSelRef.get != selectedIdx.get) {
        prevSelRef.set(selectedIdx.get)
        acceptedAligns.set(Set.empty)
        saveErr.set(None)
      }

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())
      ctx.onKey(
        new KeyCode.Char('/'),
        (e: jatatui.react.KeyEvent) => {
          if (!searchOpen.get) { searchOpen.set(true); e.stopPropagation() }
        }
      )

      val selectedEntity: Option[Entity] = rows.lift(selectedIdx.get).collect { case Row.EntityRow(e) =>
        e
      }

      def createDraft(): Unit = selectedEntity.foreach { entity =>
        val suggestions = EntityCatalog.alignmentSuggestions(entity, entities)
        val accepted = suggestions.filter(s => acceptedAligns.get.contains(s.target.primaryKey))
        val draft = buildDraft(entity, accepted.map(_.target))
        val existing = app.config.types.getOrElse(Map.empty)
        val typeName = uniqueName(entity.suggestedName, existing.keySet)
        val next = existing + (typeName -> (draft: BridgeType))
        app.updateConfig(c => c.copy(types = Some(next))) match {
          case Right(_) =>
            // Push the editor so the user can refine. `replace` would lose the back button to
            // DomainFromEntity, but the natural next step is to *edit* the type — push it on
            // top so esc returns here.
            router.push(RouterScreen.of(s"type · $typeName", TypeEditor.element(typeName)))
          case Left(e) =>
            saveErr.set(Some(Option(e.getMessage).getOrElse(e.toString)))
        }
      }

      val title = column(length(1, empty()), length(1, text("  domain type · from a real entity", titleStyle)))

      val leftPane = renderEntityList(rows, selectedIdx)
      val rightPane = renderPreview(selectedEntity, entities, acceptedAligns, () => createDraft(), saveErr.get)

      val body = column(
        fill(
          1,
          jatatui.react.Components.row(
            length(56, leftPane),
            length(2, empty()),
            fill(1, rightPane)
          )
        ),
        length(1, text("  ↑↓ navigate · enter create · / search · esc back", hintStyle))
      )

      val screen = ScreenFrame.withTitle("domain types", back, title, body)

      if (!searchOpen.get) screen
      else {
        val items: List[Entity] = entities
        val indexedRef = ctx.useRef(() => FuzzyMatch.index[Entity](items.asJava, (e: Entity) => s"${e.sourceName}:${e.path}"))
        val filter: PickerProps.Filter[Entity] = (q: String) =>
          if (q.isEmpty) java.util.List.of()
          else FuzzyMatch.rank(q, indexedRef.get).stream.limit(120).toList
        val rowRenderer: PickerProps.RowRenderer[Entity] = (e, sel) => searchHit(e, sel)
        stack(
          screen,
          Picker.of(
            PickerProps.of[Entity](
              "find entity",
              filter,
              rowRenderer,
              (picked: Entity) => {
                val idx = rows.indexWhere {
                  case Row.EntityRow(e) => e.sourceName == picked.sourceName && e.path == picked.path
                  case _                => false
                }
                if (idx >= 0) selectedIdx.set(idx)
                searchOpen.set(false)
              },
              () => searchOpen.set(false)
            )
          )
        )
      }
    }

  // ─────────────────────────────────── left pane ───────────────────────────────────

  private def buildRows(app: AppApi, entities: List[Entity]): List[Row] = {
    val out = List.newBuilder[Row]
    val configured = app.config.sources.getOrElse(Map.empty).keys.toList.sorted

    configured.foreach { sourceName =>
      val status = app.loadStatus.getOrElse(sourceName, LoadStatus.NotLoaded)
      val ents = entities.filter(_.sourceName == sourceName)
      val kind = ents.headOption.map(_.kind).getOrElse(Entity.Kind.Db)
      out += Row.SourceHeader(sourceName, kind)
      status match {
        case LoadStatus.Loaded if ents.nonEmpty        => ents.foreach(e => out += Row.EntityRow(e))
        case LoadStatus.Loaded                         => out += Row.LoadingRow(sourceName)
        case LoadStatus.Loading | LoadStatus.NotLoaded => out += Row.LoadingRow(sourceName)
        case LoadStatus.Failed(err)                    => out += Row.FailedRow(sourceName, err)
      }
      out += Row.Blank
    }
    out.result()
  }

  private def renderEntityList(rows: List[Row], sel: jatatui.react.State[Int]): Element = {
    val rowsJava = rows.asJava
    SelectableList.of(
      SelectableListProps
        .of[Row](
          rowsJava,
          r => r.isInstanceOf[Row.EntityRow],
          (r, isSel) => renderRow(r, isSel),
          sel.get,
          idx => sel.set(idx)
        )
        .withAutoFocus(true)
    )
  }

  private def renderRow(r: Row, selected: Boolean): Element = r match {
    case Row.SourceHeader(label, kind) =>
      val (icon, colour) = kind match {
        case Entity.Kind.Db    => ("◈", Color.CYAN)
        case Entity.Kind.Spec  => ("◇", Color.MAGENTA)
        case Entity.Kind.Avro  => ("▦", Color.YELLOW)
        case Entity.Kind.Proto => ("▣", Color.GREEN)
      }
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(s" $icon ", Style.empty.withFg(colour)),
          Span.styled(label, sectionStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case Row.EntityRow(entity) =>
      val nameStyle =
        if (selected) Style.empty.withFg(Color.WHITE).withBg(Color.BLUE).withAddModifier(Modifier.BOLD)
        else Style.empty.withFg(Color.WHITE)
      val countStyle = if (selected) Style.empty.withFg(Color.YELLOW) else mutedStyle
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("    ", Style.empty),
          Span.styled(entity.path, nameStyle),
          Span.styled(s"  (${entity.fields.size} field${if (entity.fields.size == 1) "" else "s"})", countStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)

    case Row.LoadingRow(_) =>
      text("    ◕ loading…", Style.empty.withFg(Color.YELLOW))
    case Row.FailedRow(_, err) =>
      text(s"    ✗ ${err.take(40)}", errStyle)
    case Row.Blank =>
      empty()
  }

  // ─────────────────────────────────── right pane ───────────────────────────────────

  private def renderPreview(
      selected: Option[Entity],
      all: List[Entity],
      acceptedAligns: jatatui.react.State[Set[String]],
      onCreate: () => Unit,
      saveErr: Option[String]
  ): Element = selected match {
    case None =>
      column(
        length(1, empty()),
        length(1, text("  pick an entity on the left", mutedStyle)),
        length(1, empty()),
        length(1, text("  once you do, you'll see:", infoStyle)),
        length(1, text("    · the suggested type name + fields", infoStyle)),
        length(1, text("    · matching entities in other sources you could align with", infoStyle)),
        fill(1, empty())
      )

    case Some(entity) =>
      val suggestions = EntityCatalog.alignmentSuggestions(entity, all)

      val previewLines: List[Element] = List(
        length(1, text("  draft", sectionStyle)),
        length(1, text(s"    name        ${entity.suggestedName}", previewStyle)),
        length(1, text(s"    primary     ${entity.primaryKey}", previewStyle)),
        length(1, text(s"    fields      ${entity.fields.size}", previewStyle)),
        length(1, empty())
      )

      val fieldsHeader = length(1, text("  fields preview", sectionStyle))
      val fieldRows = entity.fields.take(8).map { f =>
        length(1, text(s"    · ${f.name.padTo(20, ' ')}  ${f.typeLabel}${if (f.optional) "  (optional)" else ""}", previewStyle))
      }
      val fieldOverflow =
        if (entity.fields.size > 8) List(length(1, text(s"    (+${entity.fields.size - 8} more)", mutedStyle)))
        else Nil

      val alignHeader = length(
        1,
        text(
          if (suggestions.isEmpty) "  no alignment candidates yet (more loading?)"
          else s"  suggested aligned sources (${suggestions.size})",
          sectionStyle
        )
      )
      val alignRows: List[Element] = suggestions.map { s =>
        length(1, alignmentRow(s, acceptedAligns))
      }

      val errRow: Element = saveErr match {
        case Some(msg) => length(1, text(s"  ✗ ${msg.take(160)}", errStyle))
        case None      => length(0, empty())
      }

      val createButton = length(3, Button.of("create", "create-domain", true, () => onCreate()))

      column(
        (previewLines :::
          List(fieldsHeader) :::
          fieldRows :::
          fieldOverflow :::
          List(length(1, empty()), alignHeader) :::
          alignRows :::
          List(length(1, empty()), createButton, errRow, fill(1, empty())))*
      )
  }

  /** A single alignment-suggestion row: clickable label that toggles whether the suggestion is included on Create. Renders the match count + percentage.
    */
  private def alignmentRow(s: EntityCatalog.Alignment, accepted: jatatui.react.State[Set[String]]): Element =
    component { c =>
      val key = s.target.primaryKey
      val included = accepted.get.contains(key)
      val focused = c.useFocus(java.util.Optional.of(s"align:$key"), false)

      def toggle(): Unit =
        accepted.update(set => if (set.contains(key)) set - key else set + key)

      if (focused) c.onKey(new KeyCode.Enter, () => toggle())
      c.onClick(() => toggle())

      val box = if (included) "[✓]" else "[ ]"
      val pct = (s.score * 100).toInt
      val label = s"  $box  ${s.target.primaryKey}  ·  ${s.matched}/${s.total} fields  ($pct%)"
      val style =
        if (focused) Style.empty.withFg(Color.WHITE).withBg(Color.BLUE).withAddModifier(Modifier.BOLD)
        else if (included) okStyle
        else previewStyle
      text(label, style)
    }

  // ─────────────────────────────────── commit ───────────────────────────────────

  /** Build a [[DomainType]] from a primary entity + accepted alignment targets. Fields come from the primary's columns/properties/fields, encoded as the verbose [[FieldSpecObject]] form (so we
    * preserve the optional flag without forcing a follow-up edit).
    */
  private def buildDraft(primary: Entity, alignWith: List[Entity]): DomainType = {
    val fields: Map[String, FieldSpec] =
      primary.fields.iterator.map { f =>
        f.name -> (FieldSpecObject(
          array = None,
          default = None,
          description = None,
          nullable = if (f.optional) Some(true) else None,
          `type` = f.typeLabel
        ): FieldSpec)
      }.toMap

    val aligned: Option[Map[String, typr.config.generated.AlignedSource]] =
      if (alignWith.isEmpty) None
      else
        Some(alignWith.iterator.map { e =>
          e.primaryKey -> typr.config.generated.AlignedSource(
            direction = None,
            entity = Some(e.path),
            exclude = None,
            field_overrides = None,
            include_extra = None,
            mappings = None,
            mode = None,
            readonly = None,
            type_policy = None
          )
        }.toMap)

    DomainType(
      alignedSources = aligned,
      description = None,
      fields = fields,
      generate = None,
      primary = Some(primary.primaryKey),
      projections = None
    )
  }

  /** Disambiguate against existing type names — append a numeric suffix if needed. */
  private def uniqueName(suggested: String, existing: Set[String]): String =
    if (!existing.contains(suggested)) suggested
    else LazyList.from(2).map(n => s"$suggested$n").find(n => !existing.contains(n)).get

  // ─────────────────────────────────── search ───────────────────────────────────

  private def searchHit(entity: Entity, selected: Boolean): Element = {
    val style =
      if (selected) Style.empty.withBg(Color.BLUE).withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
      else Style.empty.withFg(Color.GRAY)
    val (icon, colour) = entity.kind match {
      case Entity.Kind.Db    => ("◈", Color.CYAN)
      case Entity.Kind.Spec  => ("◇", Color.MAGENTA)
      case Entity.Kind.Avro  => ("▦", Color.YELLOW)
      case Entity.Kind.Proto => ("▣", Color.GREEN)
    }
    val prefix = if (selected) "  ▶ " else "    "
    val w: Widget = (area, buffer) => {
      val line = Line.from(
        Span.styled(prefix, style),
        Span.styled(s"$icon ", Style.empty.withFg(if (selected) Color.WHITE else colour)),
        Span.styled(s"${entity.sourceName}:${entity.path}", style),
        Span.styled(s"  (${entity.fields.size})", Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY))
      )
      Paragraph.of(line).render(area, buffer)
    }
    widget(w)
  }
}
