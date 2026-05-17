package typr.cli.app.screens

import typr.config.generated.{AlignedSource, DomainGenerateOptions, DomainType, FieldSpec, FieldSpecObject, FieldSpecString}

/** Flat editable mirror of [[DomainType]]. Keeps a stable ordering for fields and alignedSources so editing one row doesn't reshuffle the others mid-keystroke (Map iteration order isn't preserved by
  * io.circe encoders the way a List is).
  */
final case class DomainTypeForm(
    primary: String,
    description: String,
    fields: Vector[DomainTypeForm.FieldRow],
    aligned: Vector[DomainTypeForm.AlignedRow],
    fieldsOpen: Boolean,
    alignedOpen: Boolean,
    generateOpen: Boolean,
    // generate options
    genBuilder: String,
    genCopy: String,
    genDomainType: String,
    genInterface: String,
    genMappers: String,
    // pass-through for fields/options we don't edit yet
    original: DomainType
)

object DomainTypeForm {

  /** Editable row representing one entry of `fields: Map[String, FieldSpec]`. Round-trips the compact `FieldSpec` shape (just a type string); if the on-disk yaml used the verbose `FieldSpecObject` we
    * preserve those settings (array / nullable / default / description) on `origin`, so saving doesn't drop them silently.
    */
  final case class FieldRow(name: String, tpe: String, origin: Option[FieldSpec])

  /** Editable row representing one entry of `alignedSources: Map[String, AlignedSource]`. Keeps the full typed value on `origin` for round-trip; the editable surface here is intentionally narrow
    * (key, entity, mode, direction, readonly). Deep edits live on a sub-screen.
    */
  final case class AlignedRow(
      key: String,
      entity: String,
      mode: String,
      direction: String,
      readonly: String,
      origin: AlignedSource
  )

  // ─────────────────────────────────── from DomainType ───────────────────────────────────

  def of(dt: DomainType): DomainTypeForm = {
    val fields = dt.fields.toVector
      .sortBy(_._1)
      .map { case (name, spec) =>
        FieldRow(name = name, tpe = specToTypeString(spec), origin = Some(spec))
      }

    val aligned = dt.alignedSources
      .getOrElse(Map.empty)
      .toVector
      .sortBy(_._1)
      .map { case (k, src) =>
        AlignedRow(
          key = k,
          entity = src.entity.getOrElse(""),
          mode = src.mode.getOrElse(""),
          direction = src.direction.getOrElse(""),
          readonly = boolToStr(src.readonly),
          origin = src
        )
      }

    val gen = dt.generate.getOrElse(emptyGen)

    DomainTypeForm(
      primary = dt.primary.getOrElse(""),
      description = dt.description.getOrElse(""),
      fields = fields,
      aligned = aligned,
      fieldsOpen = fields.nonEmpty,
      alignedOpen = aligned.nonEmpty,
      generateOpen = dt.generate.isDefined,
      genBuilder = boolToStr(gen.builder),
      genCopy = boolToStr(gen.copy),
      genDomainType = boolToStr(gen.domainType),
      genInterface = boolToStr(gen.interface),
      genMappers = boolToStr(gen.mappers),
      original = dt
    )
  }

  // ─────────────────────────────────── to DomainType ───────────────────────────────────

  def toDomainType(form: DomainTypeForm): DomainType = {
    val fieldMap: Map[String, FieldSpec] = form.fields
      .filter(_.name.trim.nonEmpty)
      .map { row =>
        val name = row.name.trim
        val tpe = row.tpe.trim
        val spec: FieldSpec = row.origin match {
          case Some(obj: FieldSpecObject) =>
            // Preserve verbose flags (array / nullable / default / description), update the type.
            obj.copy(`type` = tpe)
          case _ =>
            // Compact form covers the common case; default to it.
            if (tpe.isEmpty) FieldSpecString(name) // best guess if user hasn't filled in
            else FieldSpecString(tpe)
        }
        name -> spec
      }
      .toMap

    val alignedMap: Option[Map[String, AlignedSource]] = {
      val pairs = form.aligned.filter(_.key.trim.nonEmpty).map { row =>
        val origin = row.origin
        val updated = origin.copy(
          entity = nonEmpty(row.entity).orElse(origin.entity),
          mode = nonEmpty(row.mode).orElse(origin.mode),
          direction = nonEmpty(row.direction).orElse(origin.direction),
          readonly = parseBool(row.readonly).orElse(origin.readonly)
        )
        row.key.trim -> updated
      }
      if (pairs.isEmpty) None else Some(pairs.toMap)
    }

    val generate: Option[DomainGenerateOptions] = {
      val raw = DomainGenerateOptions(
        builder = parseBool(form.genBuilder),
        canonical = form.original.generate.flatMap(_.canonical),
        copy = parseBool(form.genCopy),
        domainType = parseBool(form.genDomainType),
        interface = parseBool(form.genInterface),
        mappers = parseBool(form.genMappers)
      )
      val empty = raw.builder.isEmpty && raw.canonical.isEmpty && raw.copy.isEmpty &&
        raw.domainType.isEmpty && raw.interface.isEmpty && raw.mappers.isEmpty
      if (form.generateOpen && !empty) Some(raw) else None
    }

    form.original.copy(
      primary = nonEmpty(form.primary),
      description = nonEmpty(form.description),
      fields = fieldMap,
      alignedSources = alignedMap,
      generate = generate
    )
  }

  // ─────────────────────────────────── helpers ───────────────────────────────────

  private def specToTypeString(spec: FieldSpec): String = spec match {
    case FieldSpecString(v)   => v
    case obj: FieldSpecObject => obj.`type`
    case _                    => ""
  }

  private def nonEmpty(s: String): Option[String] = {
    val t = s.trim
    if (t.isEmpty) None else Some(t)
  }

  private def boolToStr(b: Option[Boolean]): String = b match {
    case Some(true)  => "yes"
    case Some(false) => "no"
    case None        => ""
  }

  private def parseBool(s: String): Option[Boolean] = s.trim.toLowerCase match {
    case "yes" | "true" | "y" | "1" => Some(true)
    case "no" | "false" | "n" | "0" => Some(false)
    case _                          => None
  }

  private val emptyGen = DomainGenerateOptions(None, None, None, None, None, None)

  /** Default values for a new row. */
  def newField: FieldRow = FieldRow(name = "", tpe = "", origin = None)
  def newAligned: AlignedRow = AlignedRow(
    key = "",
    entity = "",
    mode = "",
    direction = "",
    readonly = "",
    origin = AlignedSource(None, None, None, None, None, None, None, None, None)
  )

}
