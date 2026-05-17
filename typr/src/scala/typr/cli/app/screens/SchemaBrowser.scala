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
import typr.MetaDb
import typr.cli.app.{AppApi, Entity, LoadStatus, LoadedSource}
import typr.cli.app.components.CaretCell
import typr.config.generated.{BridgeType, DbMatch, FieldType, StringOrArray, StringOrArrayString}

import scala.jdk.CollectionConverters.*

/** Per-source schema browser. On mount it spins up a daemon thread that calls [[MetaDbFetch.fetch]] (which in turn does the boundary parse, builds a Hikari datasource, initialises ExternalTools, and
  * runs `MetaDb.fromDb`). Progress is rendered live via the same [[ListLogger]] the Generate screen uses.
  *
  * Once loaded, relations are listed sorted by schema/name in a [[jatatui.components.selectablelist .SelectableList]] (heterogeneous rows: schema headers, relation rows, expanded column rows). Enter
  * on a relation toggles its column view.
  *
  * `/` opens a [[jatatui.components.picker.Picker]] over the tree; the filter strategy is our own [[FuzzyMatch]] for IntelliJ-style camel/snake matching. Selecting a hit expands the parent relation
  * and closes the picker.
  */
object SchemaBrowser {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val infoStyle: Style = Style.empty.withFg(Color.GRAY)
  private val schemaStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val tableStyle: Style = Style.empty.withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
  private val colNameStyle: Style = Style.empty.withFg(Color.CYAN)
  private val colTypeStyle: Style = Style.empty.withFg(Color.YELLOW)
  private val colNullStyle: Style = Style.empty.withFg(Color.DARK_GRAY)

  /** One row of the relations tree. Sealed so the [[jatatui.components.selectablelist .SelectableListProps.RowRenderer]] can pattern-match instead of guessing.
    */
  sealed trait TreeRow
  object TreeRow {
    final case class SchemaHeader(label: String) extends TreeRow
    final case class RelationRow(rel: typr.db.Relation, isOpen: Boolean) extends TreeRow
    final case class ColumnRow(name: String, tpe: String, nullable: Boolean, nameWidth: Int, typeWidth: Int) extends TreeRow
    case object Blank extends TreeRow
  }

  /** One row of the search index. */
  private final case class SearchItem(label: String, relation: typr.db.RelationName, column: Option[String])

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())
      ctx.onGlobalKey(new KeyCode.Char('b'), () => back())

      val title = column(length(1, empty()), length(1, text(s"  schema · $name", titleStyle)))
      val status = app.loadStatus.getOrElse(name, LoadStatus.NotLoaded)
      val body = app.sourceCache.get(name) match {
        case Some(LoadedSource.Db(metaDb)) => loadedBody(name, metaDb, app, router)
        case Some(_)                       => failedBody(s"source '$name' is not a database source")
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
    length(1, text("  connecting…", Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD))),
    length(1, empty()),
    length(1, text("  introspecting the database. The Shell loads sources in the background;", infoStyle)),
    length(1, text("  this view becomes live as soon as that completes.", infoStyle)),
    fill(1, empty())
  )

  private def notInConfigBody(name: String): Element = column(
    length(1, text(s"  source '$name' not in config", errStyle)),
    length(1, empty()),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def failedBody(err: String): Element = column(
    length(1, text(s"  ✗ ${err.take(200)}", errStyle)),
    length(1, empty()),
    length(1, text("  esc to return", hintStyle)),
    fill(1, empty())
  )

  private def loadedBody(sourceName: String, metaDb: MetaDb, app: AppApi, router: jatatui.components.router.RouterApi): Element =
    component { ctx =>
      val expanded = ctx.useState(() => Set.empty[String])
      val searchOpen = ctx.useState(() => false)

      val relations = metaDb.relations.values.flatMap(_.get.toList).toList.sortBy(_.name)

      // Open the search modal on '/'. Bubble-phase + stopPropagation so the outer Esc handler
      // (which closes the screen) doesn't fire too. Picker forcibly claims focus on mount as
      // of jatatui 0.30.0+10 — no need for app-side ctx.focus.
      ctx.onKey(
        new KeyCode.Char('/'),
        (e: jatatui.react.KeyEvent) => {
          if (!searchOpen.get) {
            searchOpen.set(true)
            e.stopPropagation()
          }
        }
      )

      if (relations.isEmpty)
        column(
          fill(1, empty()),
          length(1, text("  (no tables / views)", infoStyle)),
          fill(1, empty())
        )
      else {
        // Build the flat tree row list.
        val rows: List[TreeRow] = treeRows(relations, expanded.get)

        // Find the index of a relation in `rows`, so search-commit can position selection.
        val relationIdx: Map[String, Int] =
          rows.zipWithIndex.collect { case (TreeRow.RelationRow(r, _), i) => r.name.value -> i }.toMap

        val selectedIdx = ctx.useState(() => rows.zipWithIndex.collectFirst { case (TreeRow.RelationRow(_, _), i) => i }.getOrElse(0))

        // Tree-view left/right expand-collapse, bubble-phase from the SelectableList's focus.
        //   Right on a closed relation: open it.
        //   Right on an open relation: jump selection to its first column.
        //   Left on an open relation: close it.
        //   Left on a column row: jump back up to the parent relation.
        ctx.onKey(
          new KeyCode.Right,
          (e: jatatui.react.KeyEvent) => {
            val sel = selectedIdx.get
            if (sel >= 0 && sel < rows.size) rows(sel) match {
              case TreeRow.RelationRow(rel, false) =>
                expanded.update(_ + rel.name.value)
                e.stopPropagation()
              case TreeRow.RelationRow(_, true) if sel + 1 < rows.size && rows(sel + 1).isInstanceOf[TreeRow.ColumnRow] =>
                selectedIdx.set(sel + 1)
                e.stopPropagation()
              case _ => ()
            }
          }
        )
        ctx.onKey(
          new KeyCode.Left,
          (e: jatatui.react.KeyEvent) => {
            val sel = selectedIdx.get
            if (sel >= 0 && sel < rows.size) rows(sel) match {
              case TreeRow.RelationRow(rel, true) =>
                expanded.update(_ - rel.name.value)
                e.stopPropagation()
              case _: TreeRow.ColumnRow =>
                // Walk back up to the most recent RelationRow.
                val parent = (sel - 1 to 0 by -1).find(i => rows(i).isInstanceOf[TreeRow.RelationRow])
                parent.foreach(selectedIdx.set)
                e.stopPropagation()
              case _ => ()
            }
          }
        )

        // 'e' on a ColumnRow: extract this column as a FieldType. Walks back to find the
        // RelationRow that owns this column so the generated DbMatch can include the table.
        ctx.onKey(
          new KeyCode.Char('e'),
          (e: jatatui.react.KeyEvent) => {
            val sel = selectedIdx.get
            if (sel >= 0 && sel < rows.size) rows(sel) match {
              case col: TreeRow.ColumnRow =>
                val parentRel = (sel - 1 to 0 by -1).iterator
                  .map(i => rows(i))
                  .collectFirst { case TreeRow.RelationRow(r, _) => r }
                parentRel.foreach(rel => extractFieldType(sourceName, rel, col, app, router))
                e.stopPropagation()
              case _ => ()
            }
          }
        )

        // 'c' on a RelationRow: create a DomainType from this relation. Pushes the entity-based
        // discovery screen so the user also sees alignment suggestions from other sources.
        ctx.onKey(
          new KeyCode.Char('c'),
          (e: jatatui.react.KeyEvent) => {
            val sel = selectedIdx.get
            if (sel >= 0 && sel < rows.size) rows(sel) match {
              case TreeRow.RelationRow(_, _) =>
                router.push(RouterScreen.of("create domain type", DomainFromEntity.element))
                e.stopPropagation()
              case _ => ()
            }
          }
        )

        // Search index (built once per mount via useRef).
        val searchItems = buildIndex(relations)
        val indexedRef = ctx.useRef(() => FuzzyMatch.index[SearchItem](searchItems.asJava, (it: SearchItem) => it.label))

        val tree = column(
          length(
            1,
            text(
              s"  ${relations.size} relation${if (relations.size == 1) "" else "s"}  ·  ${metaDb.dbType}  ·  press / to search",
              infoStyle
            )
          ),
          length(1, empty()),
          fill(
            1,
            SelectableList.of(
              SelectableListProps
                .of[TreeRow](
                  rows.asJava,
                  row => row.isInstanceOf[TreeRow.RelationRow],
                  (row, selected) => renderTreeRow(row, selected, key => expanded.update { s => if (s.contains(key)) s - key else s + key }),
                  selectedIdx.get,
                  idx => selectedIdx.set(idx)
                )
                .withOnActivate { (row: TreeRow) =>
                  row match {
                    case TreeRow.RelationRow(rel, _) =>
                      expanded.update { s =>
                        val key = rel.name.value
                        if (s.contains(key)) s - key else s + key
                      }
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
            else FuzzyMatch.rank(query, indexedRef.get).stream.limit(100).toList

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
                    expanded.update(_ + item.relation.value)
                    // Jump selection to the (now-expanded) relation row so it's visible.
                    relationIdx.get(item.relation.value).foreach(selectedIdx.set)
                    searchOpen.set(false)
                  },
                  () => searchOpen.set(false)
                )
            )
          )
        }
      }
    }

  private def treeRows(relations: List[typr.db.Relation], expanded: Set[String]): List[TreeRow] = {
    val grouped = relations.groupBy(_.name.schema.getOrElse("")).toList.sortBy(_._1)
    grouped.flatMap { case (schema, rels) =>
      val header = TreeRow.SchemaHeader(if (schema.isEmpty) "(default)" else schema)
      val entries = rels.flatMap { rel =>
        val key = rel.name.value
        val isOpen = expanded.contains(key)
        val relRow = TreeRow.RelationRow(rel, isOpen)
        val colRows: List[TreeRow] = if (isOpen) columnRowsFor(rel) else Nil
        relRow :: colRows
      }
      header :: entries ::: List(TreeRow.Blank)
    }
  }

  private def columnRowsFor(rel: typr.db.Relation): List[TreeRow] = {
    val cols: List[(String, String, Boolean)] = rel match {
      case t: typr.db.Table =>
        t.cols.toList.map(c => (c.name.value, columnTypeLabel(c.tpe), isNullable(c.nullability)))
      case v: typr.db.View =>
        v.cols.toList.map { case (c, _) => (c.name.value, columnTypeLabel(c.tpe), isNullable(c.nullability)) }
    }
    val nameWidth = cols.map(_._1.length).maxOption.getOrElse(0)
    val typeWidth = cols.map(_._2.length).maxOption.getOrElse(0)
    cols.map { case (name, tpe, nullable) =>
      TreeRow.ColumnRow(name, tpe, nullable, nameWidth, typeWidth)
    }
  }

  /** Renders one row of the tree given the SelectableList's reported selection state. */
  private def renderTreeRow(row: TreeRow, selected: Boolean, toggleExpand: String => Unit): Element = row match {
    case TreeRow.SchemaHeader(label)      => text(s"  $label/", schemaStyle)
    case TreeRow.Blank                    => empty()
    case TreeRow.RelationRow(rel, isOpen) =>
      val nameStyle = if (selected) tableStyle.withBg(Color.BLUE) else tableStyle
      val countStyle = Style.empty.withFg(if (selected) Color.YELLOW else Color.DARK_GRAY)
      val colCount = rel match {
        case t: typr.db.Table => t.cols.toList.size
        case v: typr.db.View  => v.cols.toList.size
      }
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled(rel.name.name, nameStyle),
          Span.styled(s"  ($colCount cols)", countStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      jatatui.react.Components.row(
        length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(rel.name.value))),
        fill(1, widget(w))
      )
    case TreeRow.ColumnRow(name, tpe, nullable, nameWidth, typeWidth) =>
      val w: Widget = (area, buffer) => {
        val line = Line.from(
          Span.styled("        ", colNameStyle),
          Span.styled(name.padTo(nameWidth, ' '), colNameStyle),
          Span.styled("  ", colNameStyle),
          Span.styled(tpe.padTo(typeWidth, ' '), colTypeStyle),
          Span.styled(if (nullable) "  null" else "  not null", colNullStyle)
        )
        Paragraph.of(line).render(area, buffer)
      }
      widget(w)
  }

  /** Draft a FieldType from a column, write it to typr.yaml, navigate to TypeEditor for refinement. Match pattern is scoped to this `source:table.column` by default — narrow but unambiguous; the user
    * can broaden it (drop the table, switch to a glob) in the editor.
    */
  private def extractFieldType(
      sourceName: String,
      rel: typr.db.Relation,
      col: TreeRow.ColumnRow,
      app: AppApi,
      router: jatatui.components.router.RouterApi
  ): Unit = {
    val existing = app.config.types.getOrElse(Map.empty)
    val typeName = uniqueTypeName(Entity.pascalCase(col.name), existing.keySet)
    val tableName = rel.name.name
    val schemaName = rel.name.schema.filter(_.nonEmpty)
    val ft = FieldType(
      api = None,
      db = Some(
        DbMatch(
          annotation = None,
          column = Some(StringOrArrayString(col.name)),
          comment = None,
          db_type = None,
          domain = None,
          nullable = None,
          primary_key = None,
          references = None,
          schema = schemaName.map(s => StringOrArrayString(s): StringOrArray),
          source = Some(StringOrArrayString(sourceName)),
          table = Some(StringOrArrayString(tableName))
        )
      ),
      model = None,
      underlying = Some(col.tpe),
      validation = None
    )
    val next = existing + (typeName -> (ft: BridgeType))
    val _ = app.updateConfig(c => c.copy(types = Some(next)))
    router.push(RouterScreen.of(s"type · $typeName", TypeEditor.element(typeName)))
  }

  private def uniqueTypeName(suggested: String, existing: Set[String]): String =
    if (!existing.contains(suggested)) suggested
    else LazyList.from(2).map(n => s"$suggested$n").find(n => !existing.contains(n)).get

  private def buildIndex(rels: List[typr.db.Relation]): List[SearchItem] =
    rels.flatMap { rel =>
      val relLabel = rel.name.value
      val cols = rel match {
        case t: typr.db.Table =>
          t.cols.toList.map(c => SearchItem(s"$relLabel.${c.name.value}", rel.name, Some(c.name.value)))
        case v: typr.db.View =>
          v.cols.toList.map { case (c, _) => SearchItem(s"$relLabel.${c.name.value}", rel.name, Some(c.name.value)) }
      }
      SearchItem(relLabel, rel.name, None) :: cols
    }

  private def hitRow(item: SearchItem, selected: Boolean): Element = {
    val style =
      if (selected) Style.empty.withBg(Color.BLUE).withFg(Color.WHITE).withAddModifier(Modifier.BOLD)
      else Style.empty.withFg(Color.GRAY)
    val prefix = if (selected) "  ▶ " else "    "
    val icon = if (item.column.isDefined) "·" else "◇"
    text(s"$prefix$icon  ${item.label}", style)
  }

  private def columnTypeLabel(tpe: typr.db.Type): String = tpe.toString
  private def isNullable(n: typr.Nullability): Boolean = n match {
    case typr.Nullability.Nullable        => true
    case typr.Nullability.NullableUnknown => true
    case _                                => false
  }
}
