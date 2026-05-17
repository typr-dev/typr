package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
import jatatui.components.chrome.ScreenFrame
import jatatui.components.router.RouterApi
import jatatui.components.scrollable.Scrollable
import jatatui.components.textinput.{TextInputComponent, TextInputProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.core.text.{Line, Span}
import jatatui.react.Components.*
import jatatui.react.Element
import tui.crossterm.KeyCode
import typr.cli.app.{AppApi, Entity, EntityCatalog, LoadedSource, TypePreview}
import typr.config.generated.{BridgeType, DomainType, FieldType}
import jatatui.widgets.paragraph.Paragraph

import scala.jdk.CollectionConverters.*

/** Form-based editor for a single bridge type. Two variants in one screen since [[FieldType]] and [[DomainType]] have entirely different shapes — render different fields per kind, share the common
  * save plumbing.
  *
  * First cut is intentionally narrow: FieldType edits `underlying` only; DomainType edits `primary` and `description`. The deep nested bits (DbMatch / ApiMatch / ModelMatch / ValidationRules on field
  * types; `fields` / `alignedSources` on domain types) get their own editors later — they're each a screen's worth of UI.
  */
object TypeEditor {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val okStyle: Style = Style.empty.withFg(Color.GREEN).withAddModifier(Modifier.BOLD)
  private val valueStyle: Style = Style.empty.withFg(Color.WHITE)
  private val focusedValueStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val cursorStyle: Style = Style.empty.withAddModifier(Modifier.REVERSED)

  sealed trait SaveResult
  object SaveResult {
    case object None extends SaveResult
    case object Saved extends SaveResult
    final case class Failed(error: String) extends SaveResult
  }

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())

      app.config.types.getOrElse(Map.empty).get(name) match {
        case Some(ft: FieldType)  => fieldEditor(name, ft, app, back)
        case Some(dt: DomainType) => domainEditor(name, dt, app, back)
        case _                    => notFound(name, back)
      }
    }

  private def notFound(name: String, back: () => Unit): Element = {
    val body = column(
      fill(1, empty()),
      length(1, text(s"  type '$name' not found", errStyle)),
      length(1, text("  press esc to return", hintStyle)),
      fill(1, empty())
    )
    ScreenFrame.of("types", back, body)
  }

  // ---- FieldType ----

  private val sectionStyle: Style =
    Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
  private val sectionMutedStyle: Style =
    Style.empty.withFg(Color.DARK_GRAY).withAddModifier(Modifier.BOLD)

  private def fieldEditor(name: String, ft: FieldType, app: AppApi, back: () => Unit): Element =
    component { ctx =>
      val form = ctx.useState(() => FieldTypeForm.of(ft))
      val saved = ctx.useState(() => SaveResult.None: SaveResult)

      def save(): Unit = {
        val newType = FieldTypeForm.toFieldType(form.get)
        val newTypes = app.config.types.getOrElse(Map.empty) + (name -> (newType: BridgeType))
        app.updateConfig(c => c.copy(types = Some(newTypes))) match {
          case Right(_) => saved.set(SaveResult.Saved)
          case Left(e)  => saved.set(SaveResult.Failed(Option(e.getMessage).getOrElse(e.toString)))
        }
      }

      def update(f: FieldTypeForm => FieldTypeForm): Unit = {
        form.update(t => f(t))
        saved.set(SaveResult.None)
      }

      val underlyingRow = length(
        3,
        fieldRow(
          label = "underlying",
          focusId = "ft:underlying",
          value = form.get.underlying,
          autoFocus = true,
          onChange = v => update(_.copy(underlying = v))
        )
      )

      // Each match section is collapsible. Header is a focusable toggle row.
      val apiRows = matchSection(
        label = "api match",
        focusId = "ft:api-toggle",
        isOpen = form.get.apiOpen,
        toggle = () => update(s => s.copy(apiOpen = !s.apiOpen)),
        children = List(
          textField("name", "ft:api-name", form.get.apiName, v => update(_.copy(apiName = v))),
          textField("path", "ft:api-path", form.get.apiPath, v => update(_.copy(apiPath = v))),
          textField("http_method", "ft:api-method", form.get.apiHttpMethod, v => update(_.copy(apiHttpMethod = v))),
          textField("location", "ft:api-location", form.get.apiLocation, v => update(_.copy(apiLocation = v))),
          textField("operation_id", "ft:api-opid", form.get.apiOperationId, v => update(_.copy(apiOperationId = v))),
          textField("source", "ft:api-source", form.get.apiSource, v => update(_.copy(apiSource = v))),
          textField("required", "ft:api-required", form.get.apiRequired, v => update(_.copy(apiRequired = v))),
          textField("extension", "ft:api-extension", form.get.apiExtension, v => update(_.copy(apiExtension = v)))
        )
      )

      val dbRows = matchSection(
        label = "db match",
        focusId = "ft:db-toggle",
        isOpen = form.get.dbOpen,
        toggle = () => update(s => s.copy(dbOpen = !s.dbOpen)),
        children = List(
          textField("column", "ft:db-column", form.get.dbColumn, v => update(_.copy(dbColumn = v))),
          textField("table", "ft:db-table", form.get.dbTable, v => update(_.copy(dbTable = v))),
          textField("schema", "ft:db-schema", form.get.dbSchema, v => update(_.copy(dbSchema = v))),
          textField("db_type", "ft:db-type", form.get.dbType, v => update(_.copy(dbType = v))),
          textField("source", "ft:db-source", form.get.dbSource, v => update(_.copy(dbSource = v))),
          textField("domain", "ft:db-domain", form.get.dbDomain, v => update(_.copy(dbDomain = v))),
          textField("comment", "ft:db-comment", form.get.dbComment, v => update(_.copy(dbComment = v))),
          textField("references", "ft:db-references", form.get.dbReferences, v => update(_.copy(dbReferences = v))),
          textField("annotation", "ft:db-annotation", form.get.dbAnnotation, v => update(_.copy(dbAnnotation = v))),
          textField("nullable", "ft:db-nullable", form.get.dbNullable, v => update(_.copy(dbNullable = v))),
          textField("primary_key", "ft:db-pk", form.get.dbPrimaryKey, v => update(_.copy(dbPrimaryKey = v)))
        )
      )

      val modelRows = matchSection(
        label = "model match",
        focusId = "ft:model-toggle",
        isOpen = form.get.modelOpen,
        toggle = () => update(s => s.copy(modelOpen = !s.modelOpen)),
        children = List(
          textField("name", "ft:model-name", form.get.modelName, v => update(_.copy(modelName = v))),
          textField("schema", "ft:model-schema", form.get.modelSchema, v => update(_.copy(modelSchema = v))),
          textField("schema_type", "ft:model-schema-type", form.get.modelSchemaType, v => update(_.copy(modelSchemaType = v))),
          textField("format", "ft:model-format", form.get.modelFormat, v => update(_.copy(modelFormat = v))),
          textField("json_path", "ft:model-jsonpath", form.get.modelJsonPath, v => update(_.copy(modelJsonPath = v))),
          textField("source", "ft:model-source", form.get.modelSource, v => update(_.copy(modelSource = v))),
          textField("required", "ft:model-required", form.get.modelRequired, v => update(_.copy(modelRequired = v))),
          textField("extension", "ft:model-extension", form.get.modelExtension, v => update(_.copy(modelExtension = v)))
        )
      )

      val validationRows = matchSection(
        label = "validation",
        focusId = "ft:val-toggle",
        isOpen = form.get.validationOpen,
        toggle = () => update(s => s.copy(validationOpen = !s.validationOpen)),
        children = List(
          textField("pattern", "ft:val-pattern", form.get.valPattern, v => update(_.copy(valPattern = v))),
          textField("min", "ft:val-min", form.get.valMin, v => update(_.copy(valMin = v))),
          textField("max", "ft:val-max", form.get.valMax, v => update(_.copy(valMax = v))),
          textField("exclusive_min", "ft:val-emin", form.get.valExclusiveMin, v => update(_.copy(valExclusiveMin = v))),
          textField("exclusive_max", "ft:val-emax", form.get.valExclusiveMax, v => update(_.copy(valExclusiveMax = v))),
          textField("min_length", "ft:val-minlen", form.get.valMinLength, v => update(_.copy(valMinLength = v))),
          textField("max_length", "ft:val-maxlen", form.get.valMaxLength, v => update(_.copy(valMaxLength = v))),
          textField("allowed_values", "ft:val-allowed", form.get.valAllowedValues, v => update(_.copy(valAllowedValues = v)))
        )
      )

      // Preview-matches results live in a separate state slot. Recomputed synchronously when
      // the user clicks the button — no I/O, just walks cached MetaDb structures.
      val previewResults = ctx.useState(() => Option.empty[List[TypePreview.Result]])

      def runPreview(): Unit = {
        val cachedDbs = app.sourceCache.toList.sortBy(_._1).collect { case (sourceName, LoadedSource.Db(metaDb)) =>
          sourceName -> metaDb
        }
        val current = FieldTypeForm.toFieldType(form.get)
        val results = cachedDbs.map { case (sourceName, metaDb) =>
          TypePreview.runDb(name, current, sourceName, metaDb)
        }
        previewResults.set(Some(results))
      }

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val previewButton = length(3, Button.of("preview matches", "preview", false, () => runPreview()))

      val actionRow = length(
        3,
        jatatui.react.Components.row(
          length(14, saveButton),
          length(2, empty()),
          length(24, previewButton),
          fill(1, empty())
        )
      )

      val statusLine = saved.get match {
        case SaveResult.None        => text("", hintStyle)
        case SaveResult.Saved       => text(s"  ✓ saved to ${app.configPath.getFileName}", okStyle)
        case SaveResult.Failed(err) => text(s"  ✗ save failed: ${err.take(120)}", errStyle)
      }

      val cachedDbCount = app.sourceCache.count { case (_, v) => v.isInstanceOf[LoadedSource.Db] }
      val previewRows: List[Element] = renderPreviewResults(previewResults.get, cachedDbCount)

      val title = column(
        length(1, empty()),
        length(1, text(s"  type · $name  ·  field", titleStyle))
      )

      val formRows: List[Element] =
        underlyingRow ::
          length(1, empty()) ::
          apiRows :::
          dbRows :::
          modelRows :::
          validationRows :::
          List(length(1, empty()), actionRow, length(1, statusLine)) :::
          previewRows

      val body = column(
        fill(1, Scrollable.column(formRows.asJava)),
        length(1, text("  tab navigate · enter (on toggles) collapse/expand · wheel to scroll · esc back", hintStyle))
      )

      ScreenFrame.withTitle("types", back, title, body)
    }

  /** Render the per-source preview results. Each source gets a header + up to 8 matched columns; more than 8 collapses to "(+N more)". Empty result for a source just notes "no matches" so the user
    * can tell the difference between "no cached sources" and "cached but nothing matched".
    */
  private def renderPreviewResults(results: Option[List[TypePreview.Result]], cachedCount: Int): List[Element] = {
    val previewTitleStyle = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
    val previewSourceStyle = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
    val previewMatchStyle = Style.empty.withFg(Color.WHITE)
    val previewMutedStyle = Style.empty.withFg(Color.DARK_GRAY)

    results match {
      case None if cachedCount == 0 =>
        List(
          length(1, empty()),
          length(1, text("  preview matches:", previewTitleStyle)),
          length(1, text("    no sources loaded yet — open a source in the schema browser first.", previewMutedStyle))
        )
      case None =>
        Nil
      case Some(perSource) =>
        val header = List(
          length(1, empty()),
          length(1, text(s"  preview matches  ($cachedCount cached source${if (cachedCount == 1) "" else "s"})", previewTitleStyle))
        )
        if (perSource.isEmpty) header ::: List(length(1, text("    no cached sources to test against.", previewMutedStyle)))
        else
          header ::: perSource.flatMap { res =>
            val matchCount = res.matches.size
            val sourceHeader =
              length(1, text(s"    ${res.sourceName}  ·  $matchCount match${if (matchCount == 1) "" else "es"}", previewSourceStyle))
            val matchRows: List[Element] =
              if (res.matches.isEmpty)
                List(length(1, text("      (none)", previewMutedStyle)))
              else {
                val visible = res.matches.take(8)
                val rows = visible.map { m =>
                  length(1, text(s"      · ${m.qualifiedColumn}  ${m.dbType}", previewMatchStyle))
                }
                val overflow = matchCount - visible.size
                if (overflow > 0) rows :+ length(1, text(s"      (+$overflow more)", previewMutedStyle))
                else rows
              }
            sourceHeader :: matchRows
          }
    }
  }

  /** Per-field alignment status across each aligned source. Rendered live as the user edits fields / aligned sources, so they see drift instantly without leaving the editor.
    *
    * ✓ domain field maps to an entity field with the same (normalised) name ⚠ domain field has no counterpart in the aligned entity (drift you should fix) · aligned entity has a field with no
    * counterpart in the domain (extra; the mapper generator will pick a default)
    *
    * The matrix only renders when there's something to compare against — at least one aligned source and at least one field on the domain side.
    */
  private def renderAlignmentMatrix(
      form: DomainTypeForm,
      cache: collection.Map[String, LoadedSource]
  ): List[Element] = {
    val domainFields = form.fields.filter(_.name.trim.nonEmpty).map(_.name.trim).toList
    val alignedKeys = form.aligned.filter(_.key.trim.nonEmpty).map(_.key.trim).toList

    if (domainFields.isEmpty || alignedKeys.isEmpty) return Nil

    // Build per-source entity lookup. For an aligned-source key "boundary:entity", find the
    // matching entity by its primaryKey (same shape).
    val allEntities = EntityCatalog.fromCache(cache)
    val byKey: Map[String, Entity] = allEntities.iterator.map(e => e.primaryKey -> e).toMap

    val resolved: List[(String, Option[Entity])] = alignedKeys.map(k => k -> byKey.get(k))

    // If none of the aligned keys resolve, the user is probably still typing — skip the matrix
    // so a stale ✗-everywhere block doesn't shout at them mid-edit.
    if (resolved.forall(_._2.isEmpty)) return Nil

    val titleStyle = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
    val sourceStyle = Style.empty.withFg(Color.CYAN).withAddModifier(Modifier.BOLD)
    val fieldStyle = Style.empty.withFg(Color.WHITE)
    val matchStyle = Style.empty.withFg(Color.GREEN)
    val missingStyle = Style.empty.withFg(Color.YELLOW)
    val extraStyle = Style.empty.withFg(Color.DARK_GRAY)
    val mutedStyle = Style.empty.withFg(Color.DARK_GRAY)

    val maxFieldWidth = domainFields.map(_.length).max
    val perSourceWidth = (resolved.map(_._1.length).maxOption.getOrElse(8) + 6).max(20)

    // Header
    val headerLine = Line.from(
      Span.styled("  ", Style.empty),
      Span.styled("field".padTo(maxFieldWidth + 4, ' '), titleStyle),
      Span.styled(resolved.map { case (k, _) => k.padTo(perSourceWidth, ' ') }.mkString, sourceStyle)
    )
    val headerWidget: jatatui.core.widgets.Widget = (area, buffer) => Paragraph.of(headerLine).render(area, buffer)

    // One row per domain field
    val fieldRows: List[Element] = domainFields.map { fieldName =>
      val normField = Entity.normalise(fieldName)
      val cells: List[Span] = resolved.map { case (_, entityOpt) =>
        entityOpt match {
          case None =>
            Span.styled("(source not loaded)".padTo(perSourceWidth, ' '), mutedStyle)
          case Some(entity) =>
            entity.fields.find(f => Entity.normalise(f.name) == normField) match {
              case Some(matched) =>
                Span.styled(s"✓  ${matched.name}".padTo(perSourceWidth, ' '), matchStyle)
              case None =>
                Span.styled("⚠  (missing)".padTo(perSourceWidth, ' '), missingStyle)
            }
        }
      }
      val w: jatatui.core.widgets.Widget = (area, buffer) => {
        val line = Line.from(
          (Span.styled("  ", Style.empty)
            +: Span.styled(fieldName.padTo(maxFieldWidth + 4, ' '), fieldStyle)
            +: cells)*
        )
        Paragraph.of(line).render(area, buffer)
      }
      length(1, widget(w))
    }

    // Extras: fields in an aligned entity that don't appear in the domain
    val domainNormSet = domainFields.iterator.map(Entity.normalise).toSet
    val extrasBySource: List[(String, List[String])] = resolved
      .collect { case (key, Some(entity)) =>
        val extras = entity.fields.filter(f => !domainNormSet.contains(Entity.normalise(f.name))).map(_.name)
        key -> extras
      }
      .filter(_._2.nonEmpty)

    val extraRows: List[Element] =
      if (extrasBySource.isEmpty) Nil
      else
        length(1, empty()) ::
          length(1, text("  extras (fields in aligned sources not yet in domain):", titleStyle)) ::
          extrasBySource.flatMap { case (key, extras) =>
            val visible = extras.take(6)
            val overflow = extras.size - visible.size
            val header = length(1, text(s"    ${key}", sourceStyle))
            val rows = visible.map(n => length(1, text(s"      ·  $n", extraStyle)))
            val tail =
              if (overflow > 0) List(length(1, text(s"      (+$overflow more)", mutedStyle)))
              else Nil
            header :: rows ::: tail
          }

    length(1, empty()) ::
      length(1, text("  alignment matrix", titleStyle)) ::
      length(1, widget(headerWidget)) ::
      fieldRows ::: extraRows
  }

  /** Renders a collapsible section header followed by its child rows when expanded. The header row is itself a focusable+activatable toggle so the user can Tab to it and press Enter to
    * collapse/expand without reaching for the mouse.
    */
  private def matchSection(
      label: String,
      focusId: String,
      isOpen: Boolean,
      toggle: () => Unit,
      children: List[Element]
  ): List[Element] = {
    val header = length(1, sectionToggle(label, focusId, isOpen, toggle))
    if (isOpen) header :: children.map(c => length(3, c)) ::: List(length(1, empty()))
    else List(header)
  }

  private def sectionToggle(label: String, focusId: String, isOpen: Boolean, toggle: () => Unit): Element =
    component { ctx =>
      val focused = ctx.useFocus(java.util.Optional.of(focusId), false)
      if (focused) ctx.onKey(new KeyCode.Enter, () => toggle())
      ctx.onClick(() => toggle())
      val caret = if (isOpen) "▾" else "▸"
      val st = if (focused) sectionStyle else sectionMutedStyle
      text(s"  $caret  $label", st)
    }

  private def textField(
      label: String,
      focusId: String,
      value: String,
      onChange: String => Unit
  ): Element = fieldRow(label, focusId, value, autoFocus = false, onChange)

  // ---- DomainType ----

  private def domainEditor(name: String, dt: DomainType, app: AppApi, back: () => Unit): Element =
    component { ctx =>
      val form = ctx.useState(() => DomainTypeForm.of(dt))
      val saved = ctx.useState(() => SaveResult.None: SaveResult)

      def save(): Unit = {
        val newType = DomainTypeForm.toDomainType(form.get)
        val newTypes = app.config.types.getOrElse(Map.empty) + (name -> (newType: BridgeType))
        app.updateConfig(c => c.copy(types = Some(newTypes))) match {
          case Right(_) => saved.set(SaveResult.Saved)
          case Left(e)  => saved.set(SaveResult.Failed(Option(e.getMessage).getOrElse(e.toString)))
        }
      }

      def update(f: DomainTypeForm => DomainTypeForm): Unit = {
        form.update(t => f(t))
        saved.set(SaveResult.None)
      }

      val primaryRow = length(
        3,
        fieldRow(
          label = "primary",
          focusId = "dt:primary",
          value = form.get.primary,
          autoFocus = true,
          onChange = v => update(_.copy(primary = v))
        )
      )
      val descRow = length(
        3,
        fieldRow(
          label = "description",
          focusId = "dt:description",
          value = form.get.description,
          autoFocus = false,
          onChange = v => update(_.copy(description = v))
        )
      )

      val fieldRows = fieldsSection(form.get, update)
      val alignedRows = alignedSection(form.get, update)
      val generateRows = generateSection(form.get, update)

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val statusLine = saved.get match {
        case SaveResult.None        => text("", hintStyle)
        case SaveResult.Saved       => text(s"  ✓ saved to ${app.configPath.getFileName}", okStyle)
        case SaveResult.Failed(err) => text(s"  ✗ save failed: ${err.take(120)}", errStyle)
      }

      val title = column(
        length(1, empty()),
        length(1, text(s"  type · $name  ·  domain", titleStyle))
      )

      val matrixRows = renderAlignmentMatrix(form.get, app.sourceCache)

      val formRows: List[Element] =
        primaryRow ::
          descRow ::
          length(1, empty()) ::
          fieldRows :::
          alignedRows :::
          generateRows :::
          matrixRows :::
          List(length(1, empty()), saveButton, length(1, statusLine))

      val body = column(
        fill(1, Scrollable.column(formRows.asJava)),
        length(1, text("  tab navigate · enter (on +/×/toggle) act · wheel to scroll · esc back", hintStyle))
      )

      ScreenFrame.withTitle("types", back, title, body)
    }

  // One row per `fields:` entry. Each row has [name input | type input | delete button]. A
  // trailing "+ add field" row at the bottom appends a blank entry.
  private def fieldsSection(form: DomainTypeForm, update: (DomainTypeForm => DomainTypeForm) => Unit): List[Element] = {
    val header = length(
      1,
      sectionToggle(
        s"fields (${form.fields.size})",
        "dt:fields-toggle",
        form.fieldsOpen,
        () => update(s => s.copy(fieldsOpen = !s.fieldsOpen))
      )
    )
    if (!form.fieldsOpen) List(header)
    else {
      val rows = form.fields.zipWithIndex.toList.map { case (row, idx) =>
        length(
          3,
          jatatui.react.Components.row(
            fill(
              2,
              fieldRow(
                label = "name",
                focusId = s"dt:field-name-$idx",
                value = row.name,
                autoFocus = false,
                onChange = v => update(_.copy(fields = form.fields.updated(idx, row.copy(name = v))))
              )
            ),
            length(2, empty()),
            fill(
              3,
              fieldRow(
                label = "type",
                focusId = s"dt:field-type-$idx",
                value = row.tpe,
                autoFocus = false,
                onChange = v => update(_.copy(fields = form.fields.updated(idx, row.copy(tpe = v))))
              )
            ),
            length(2, empty()),
            length(
              7,
              Button.of(
                "×",
                s"dt:field-del-$idx",
                false,
                () => update(_.copy(fields = form.fields.patch(idx, Nil, 1)))
              )
            )
          )
        )
      }
      val addRow = length(
        3,
        Button.of(
          "+ add field",
          "dt:field-add",
          false,
          () => update(_.copy(fields = form.fields :+ DomainTypeForm.newField))
        )
      )
      (header :: rows) ::: List(addRow, length(1, empty()))
    }
  }

  // alignedSources: similar pattern. Row holds key, entity, mode, direction, readonly.
  private def alignedSection(form: DomainTypeForm, update: (DomainTypeForm => DomainTypeForm) => Unit): List[Element] = {
    val header = length(
      1,
      sectionToggle(
        s"aligned sources (${form.aligned.size})",
        "dt:aligned-toggle",
        form.alignedOpen,
        () => update(s => s.copy(alignedOpen = !s.alignedOpen))
      )
    )
    if (!form.alignedOpen) List(header)
    else {
      val rows = form.aligned.zipWithIndex.toList.flatMap { case (row, idx) =>
        List(
          // Line 1: key  entity  delete
          length(
            3,
            jatatui.react.Components.row(
              fill(
                3,
                fieldRow(
                  label = "key  (boundary:entity)",
                  focusId = s"dt:al-key-$idx",
                  value = row.key,
                  autoFocus = false,
                  onChange = v => update(_.copy(aligned = form.aligned.updated(idx, row.copy(key = v))))
                )
              ),
              length(2, empty()),
              fill(
                3,
                fieldRow(
                  label = "entity",
                  focusId = s"dt:al-entity-$idx",
                  value = row.entity,
                  autoFocus = false,
                  onChange = v => update(_.copy(aligned = form.aligned.updated(idx, row.copy(entity = v))))
                )
              ),
              length(2, empty()),
              length(
                7,
                Button.of(
                  "×",
                  s"dt:al-del-$idx",
                  false,
                  () => update(_.copy(aligned = form.aligned.patch(idx, Nil, 1)))
                )
              )
            )
          ),
          // Line 2: mode  direction  readonly
          length(
            3,
            jatatui.react.Components.row(
              fill(
                1,
                fieldRow(
                  label = "mode  (exact|superset|subset)",
                  focusId = s"dt:al-mode-$idx",
                  value = row.mode,
                  autoFocus = false,
                  onChange = v => update(_.copy(aligned = form.aligned.updated(idx, row.copy(mode = v))))
                )
              ),
              length(2, empty()),
              fill(
                1,
                fieldRow(
                  label = "direction  (in|out|in-out)",
                  focusId = s"dt:al-dir-$idx",
                  value = row.direction,
                  autoFocus = false,
                  onChange = v => update(_.copy(aligned = form.aligned.updated(idx, row.copy(direction = v))))
                )
              ),
              length(2, empty()),
              fill(
                1,
                fieldRow(
                  label = "readonly  (yes|no)",
                  focusId = s"dt:al-ro-$idx",
                  value = row.readonly,
                  autoFocus = false,
                  onChange = v => update(_.copy(aligned = form.aligned.updated(idx, row.copy(readonly = v))))
                )
              )
            )
          ),
          length(1, empty())
        )
      }
      val addRow = length(
        3,
        Button.of(
          "+ add aligned source",
          "dt:al-add",
          false,
          () => update(_.copy(aligned = form.aligned :+ DomainTypeForm.newAligned))
        )
      )
      (header :: rows) ::: List(addRow, length(1, empty()))
    }
  }

  // generate options: simple 3-state yes/no/empty per flag.
  private def generateSection(form: DomainTypeForm, update: (DomainTypeForm => DomainTypeForm) => Unit): List[Element] = {
    val header = length(
      1,
      sectionToggle(
        "generate options",
        "dt:gen-toggle",
        form.generateOpen,
        () => update(s => s.copy(generateOpen = !s.generateOpen))
      )
    )
    if (!form.generateOpen) List(header)
    else
      List(
        header,
        length(3, fieldRow("builder  (yes|no)", "dt:gen-builder", form.genBuilder, false, v => update(_.copy(genBuilder = v)))),
        length(3, fieldRow("copy  (yes|no)", "dt:gen-copy", form.genCopy, false, v => update(_.copy(genCopy = v)))),
        length(3, fieldRow("domain type  (yes|no)", "dt:gen-dt", form.genDomainType, false, v => update(_.copy(genDomainType = v)))),
        length(3, fieldRow("interface  (yes|no)", "dt:gen-iface", form.genInterface, false, v => update(_.copy(genInterface = v)))),
        length(3, fieldRow("mappers  (yes|no)", "dt:gen-map", form.genMappers, false, v => update(_.copy(genMappers = v)))),
        length(1, empty())
      )
  }

  private def fieldRow(
      label: String,
      focusId: String,
      value: String,
      autoFocus: Boolean,
      onChange: String => Unit
  ): Element =
    TextInputComponent.of(
      TextInputProps
        .of(value, v => onChange(v))
        .withTitle(label)
        .withFocusId(focusId)
        .withAutoFocus(autoFocus)
        .withStyle(valueStyle)
        .withFocusedStyle(focusedValueStyle)
        .withCursorStyle(cursorStyle)
    )
}
