package typr.cli.app.screens

import typr.config.generated.{ApiMatch, DbMatch, FieldType, ModelMatch, StringOrArray, StringOrArrayArray, StringOrArrayString, ValidationRules}

/** Flat string-only mirror of [[FieldType]]. Holds raw form values during editing; converts back to the typed config on save. Anything that's a [[StringOrArray]] is stored as a single comma-separated
  * string; booleans as one of "", "yes", "no"; numerics as their decimal text.
  *
  * Round-trip is lossy in trivial ways (e.g. a `StringOrArrayString("foo")` and a one-element `StringOrArrayArray(List("foo"))` both encode as "foo" → on save we always emit the string variant when
  * there's no comma). That matches the convention typr's existing yaml uses.
  */
final case class FieldTypeForm(
    underlying: String,
    apiOpen: Boolean,
    dbOpen: Boolean,
    modelOpen: Boolean,
    validationOpen: Boolean,
    // ApiMatch
    apiName: String,
    apiPath: String,
    apiHttpMethod: String,
    apiLocation: String, // comma-separated list of (path/query/header/cookie)
    apiOperationId: String,
    apiSource: String,
    apiRequired: String, // "" | "yes" | "no"
    apiExtension: String, // "k=v, k=v"
    // DbMatch
    dbColumn: String,
    dbTable: String,
    dbSchema: String,
    dbType: String,
    dbSource: String,
    dbDomain: String,
    dbComment: String,
    dbReferences: String,
    dbAnnotation: String,
    dbNullable: String,
    dbPrimaryKey: String,
    // ModelMatch
    modelName: String,
    modelSchema: String,
    modelSchemaType: String,
    modelFormat: String,
    modelJsonPath: String,
    modelSource: String,
    modelRequired: String,
    modelExtension: String,
    // ValidationRules
    valPattern: String,
    valMin: String,
    valMax: String,
    valExclusiveMin: String,
    valExclusiveMax: String,
    valMinLength: String,
    valMaxLength: String,
    valAllowedValues: String, // JSON-encoded list, e.g. ["foo", "bar"]
    // original for round-tripping fields we don't touch
    original: FieldType
)

object FieldTypeForm {

  // ────────────────────────────────── from FieldType ──────────────────────────────────

  def of(ft: FieldType): FieldTypeForm = {
    val api = ft.api.getOrElse(emptyApi)
    val db = ft.db.getOrElse(emptyDb)
    val mm = ft.model.getOrElse(emptyModel)
    val vr = ft.validation.getOrElse(emptyVal)
    FieldTypeForm(
      underlying = ft.underlying.getOrElse(""),
      apiOpen = ft.api.isDefined,
      dbOpen = ft.db.isDefined,
      modelOpen = ft.model.isDefined,
      validationOpen = ft.validation.isDefined,
      apiName = soaToStr(api.name),
      apiPath = soaToStr(api.path),
      apiHttpMethod = soaToStr(api.http_method),
      apiLocation = api.location.map(_.mkString(", ")).getOrElse(""),
      apiOperationId = soaToStr(api.operation_id),
      apiSource = soaToStr(api.source),
      apiRequired = boolToStr(api.required),
      apiExtension = mapToStr(api.`extension`),
      dbColumn = soaToStr(db.column),
      dbTable = soaToStr(db.table),
      dbSchema = soaToStr(db.schema),
      dbType = soaToStr(db.db_type),
      dbSource = soaToStr(db.source),
      dbDomain = soaToStr(db.domain),
      dbComment = soaToStr(db.comment),
      dbReferences = soaToStr(db.references),
      dbAnnotation = soaToStr(db.annotation),
      dbNullable = boolToStr(db.nullable),
      dbPrimaryKey = boolToStr(db.primary_key),
      modelName = soaToStr(mm.name),
      modelSchema = soaToStr(mm.schema),
      modelSchemaType = soaToStr(mm.schema_type),
      modelFormat = soaToStr(mm.format),
      modelJsonPath = soaToStr(mm.json_path),
      modelSource = soaToStr(mm.source),
      modelRequired = boolToStr(mm.required),
      modelExtension = mapToStr(mm.`extension`),
      valPattern = vr.pattern.getOrElse(""),
      valMin = doubleToStr(vr.min),
      valMax = doubleToStr(vr.max),
      valExclusiveMin = doubleToStr(vr.exclusive_min),
      valExclusiveMax = doubleToStr(vr.exclusive_max),
      valMinLength = longToStr(vr.min_length),
      valMaxLength = longToStr(vr.max_length),
      valAllowedValues = vr.allowed_values
        .map(_.map(_.noSpaces).mkString(", "))
        .getOrElse(""),
      original = ft
    )
  }

  // ────────────────────────────────── to FieldType ──────────────────────────────────

  def toFieldType(form: FieldTypeForm): FieldType = {
    val api: Option[ApiMatch] = {
      val raw = ApiMatch(
        `extension` = parseExtensionMap(form.apiExtension),
        http_method = parseSoa(form.apiHttpMethod),
        location = parseList(form.apiLocation),
        name = parseSoa(form.apiName),
        operation_id = parseSoa(form.apiOperationId),
        path = parseSoa(form.apiPath),
        required = parseBool(form.apiRequired),
        source = parseSoa(form.apiSource)
      )
      if (form.apiOpen && !isApiEmpty(raw)) Some(raw) else None
    }
    val db: Option[DbMatch] = {
      val raw = DbMatch(
        annotation = parseSoa(form.dbAnnotation),
        column = parseSoa(form.dbColumn),
        comment = parseSoa(form.dbComment),
        db_type = parseSoa(form.dbType),
        domain = parseSoa(form.dbDomain),
        nullable = parseBool(form.dbNullable),
        primary_key = parseBool(form.dbPrimaryKey),
        references = parseSoa(form.dbReferences),
        schema = parseSoa(form.dbSchema),
        source = parseSoa(form.dbSource),
        table = parseSoa(form.dbTable)
      )
      if (form.dbOpen && !isDbEmpty(raw)) Some(raw) else None
    }
    val model: Option[ModelMatch] = {
      val raw = ModelMatch(
        `extension` = parseExtensionMap(form.modelExtension),
        format = parseSoa(form.modelFormat),
        json_path = parseSoa(form.modelJsonPath),
        name = parseSoa(form.modelName),
        required = parseBool(form.modelRequired),
        schema = parseSoa(form.modelSchema),
        schema_type = parseSoa(form.modelSchemaType),
        source = parseSoa(form.modelSource)
      )
      if (form.modelOpen && !isModelEmpty(raw)) Some(raw) else None
    }
    val validation: Option[ValidationRules] = {
      val raw = ValidationRules(
        allowed_values = parseAllowed(form.valAllowedValues),
        exclusive_max = parseDouble(form.valExclusiveMax),
        exclusive_min = parseDouble(form.valExclusiveMin),
        max = parseDouble(form.valMax),
        max_length = parseLong(form.valMaxLength),
        min = parseDouble(form.valMin),
        min_length = parseLong(form.valMinLength),
        pattern = nonEmpty(form.valPattern)
      )
      if (form.validationOpen && !isValEmpty(raw)) Some(raw) else None
    }
    FieldType(
      api = api,
      db = db,
      model = model,
      underlying = nonEmpty(form.underlying),
      validation = validation
    )
  }

  // ────────────────────────────────── primitives ──────────────────────────────────

  private def soaToStr(soa: Option[StringOrArray]): String = soa match {
    case Some(StringOrArrayString(s))  => s
    case Some(StringOrArrayArray(arr)) => arr.mkString(", ")
    case _                             => ""
  }

  private def parseSoa(s: String): Option[StringOrArray] = {
    val trimmed = s.trim
    if (trimmed.isEmpty) None
    else if (trimmed.contains(",")) Some(StringOrArrayArray(trimmed.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList))
    else Some(StringOrArrayString(trimmed))
  }

  private def parseList(s: String): Option[List[String]] = {
    val parts = s.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList
    if (parts.isEmpty) None else Some(parts)
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

  private def mapToStr(m: Option[Map[String, String]]): String =
    m.map(_.toList.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(", ")).getOrElse("")

  private def parseExtensionMap(s: String): Option[Map[String, String]] = {
    val trimmed = s.trim
    if (trimmed.isEmpty) None
    else {
      val pairs = trimmed
        .split(",")
        .iterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap { kv =>
          kv.split("=", 2) match {
            case Array(k, v) => Some(k.trim -> v.trim)
            case _           => None
          }
        }
        .toMap
      if (pairs.isEmpty) None else Some(pairs)
    }
  }

  private def doubleToStr(d: Option[Double]): String = d.map(_.toString).getOrElse("")
  private def longToStr(l: Option[Long]): String = l.map(_.toString).getOrElse("")

  private def parseDouble(s: String): Option[Double] =
    s.trim match {
      case t if t.isEmpty => None
      case t              => scala.util.Try(t.toDouble).toOption
    }

  private def parseLong(s: String): Option[Long] =
    s.trim match {
      case t if t.isEmpty => None
      case t              => scala.util.Try(t.toLong).toOption
    }

  private def nonEmpty(s: String): Option[String] = {
    val t = s.trim
    if (t.isEmpty) None else Some(t)
  }

  /** Treats allowed-values as a comma-separated list of strings. Loses the ability to express numeric / boolean allowed values from the UI (you can still edit those in yaml directly); for the
    * field-type use cases this surfaces ("status in {active,inactive,pending}", etc.) the string variant covers what the editor actually offers.
    */
  private def parseAllowed(s: String): Option[List[io.circe.Json]] = {
    val parts = s.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList
    if (parts.isEmpty) None else Some(parts.map(io.circe.Json.fromString))
  }

  // ────────────────────────────────── empty templates / checks ──────────────────────────────────

  private val emptyApi = ApiMatch(None, None, None, None, None, None, None, None)
  private val emptyDb = DbMatch(None, None, None, None, None, None, None, None, None, None, None)
  private val emptyModel = ModelMatch(None, None, None, None, None, None, None, None)
  private val emptyVal = ValidationRules(None, None, None, None, None, None, None, None)

  private def isApiEmpty(a: ApiMatch): Boolean =
    a.name.isEmpty && a.path.isEmpty && a.http_method.isEmpty && a.location.isEmpty &&
      a.operation_id.isEmpty && a.source.isEmpty && a.required.isEmpty && a.`extension`.isEmpty

  private def isDbEmpty(d: DbMatch): Boolean =
    d.column.isEmpty && d.table.isEmpty && d.schema.isEmpty && d.db_type.isEmpty &&
      d.source.isEmpty && d.domain.isEmpty && d.comment.isEmpty && d.references.isEmpty &&
      d.annotation.isEmpty && d.nullable.isEmpty && d.primary_key.isEmpty

  private def isModelEmpty(m: ModelMatch): Boolean =
    m.name.isEmpty && m.schema.isEmpty && m.schema_type.isEmpty && m.format.isEmpty &&
      m.json_path.isEmpty && m.source.isEmpty && m.required.isEmpty && m.`extension`.isEmpty

  private def isValEmpty(v: ValidationRules): Boolean =
    v.pattern.isEmpty && v.min.isEmpty && v.max.isEmpty && v.exclusive_min.isEmpty &&
      v.exclusive_max.isEmpty && v.min_length.isEmpty && v.max_length.isEmpty && v.allowed_values.isEmpty
}
