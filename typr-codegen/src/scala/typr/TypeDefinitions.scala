package typr

/** Defines shared wrapper types that can be used across databases and OpenAPI specs.
  *
  * Types are matched using predicates:
  *   - DbMatch for database columns
  *   - ModelMatch for schema/model properties (OpenAPI schemas and JSON Schema)
  *   - ApiMatch for OpenAPI parameters only (header, query, path, cookie)
  *
  * When a column or field matches, it uses the named type instead of the default generated type.
  *
  * Match semantics:
  *   - Empty list means "match any" for that field
  *   - Non-empty list means "match any of these patterns" (OR within field)
  *   - All non-empty fields must match (AND across fields)
  *   - String patterns support glob syntax (*, ?)
  *   - Patterns starting with ! are exclusions (e.g., ["*email*", "!email_process"]) Exclusions are applied after inclusions: first match positive patterns, then exclude negatives
  */
case class TypeDefinitions(
    entries: List[TypeEntry]
) {
  def isEmpty: Boolean = entries.isEmpty

  def ++(other: TypeDefinitions): TypeDefinitions =
    TypeDefinitions(entries ++ other.entries)

  def filterNot(pred: TypeEntry => Boolean): TypeDefinitions =
    TypeDefinitions(entries.filterNot(pred))
}

object TypeDefinitions {
  val Empty: TypeDefinitions = TypeDefinitions(Nil)

  def apply(entries: TypeEntry*): TypeDefinitions =
    TypeDefinitions(entries.toList)
}

/** A single type definition - underlying type is inferred from matches */
case class TypeEntry(
    /** The type name (e.g., "UserId", "IsAdmin") */
    name: String,
    /** Predicate for matching database columns */
    db: DbMatch = DbMatch.Empty,
    /** Predicate for matching schema/model properties (OpenAPI schemas and JSON Schema) */
    model: ModelMatch = ModelMatch.Empty,
    /** Predicate for matching OpenAPI parameters (header, query, path, cookie) */
    api: ApiMatch = ApiMatch.Empty
)

object TypeEntry {
  def apply(name: String): TypeEntry =
    TypeEntry(name, DbMatch.Empty, ModelMatch.Empty, ApiMatch.Empty)

  def db(name: String, db: DbMatch): TypeEntry =
    TypeEntry(name, db, ModelMatch.Empty, ApiMatch.Empty)

  def model(name: String, model: ModelMatch): TypeEntry =
    TypeEntry(name, DbMatch.Empty, model, ApiMatch.Empty)

  def api(name: String, api: ApiMatch): TypeEntry =
    TypeEntry(name, DbMatch.Empty, ModelMatch.Empty, api)
}

/** Predicate for matching database columns.
  *
  * All non-empty fields must match (AND). Within each field's list, any match suffices (OR). Empty list means "match any".
  */
case class DbMatch(
    /** Match specific database names (for multi-database setups) */
    database: List[String] = Nil,
    /** Match schema names (glob patterns supported) */
    schema: List[String] = Nil,
    /** Match table/view names (glob patterns supported) */
    table: List[String] = Nil,
    /** Match column names (glob patterns supported) */
    column: List[String] = Nil,
    /** Match database type names (e.g., "int4", "varchar") */
    dbType: List[String] = Nil,
    /** Match domain names */
    domain: List[String] = Nil,
    /** Match only primary key columns */
    primaryKey: Option[Boolean] = None,
    /** Match nullability */
    nullable: Option[Boolean] = None,
    /** Match columns that reference specific tables (glob patterns) */
    references: List[String] = Nil,
    /** Match column comments (glob patterns) */
    comment: List[String] = Nil,
    /** Match extracted annotations from comments */
    annotation: List[String] = Nil
)

object DbMatch {
  val Empty: DbMatch = DbMatch()

  def column(patterns: String*): DbMatch =
    Empty.copy(column = patterns.toList)

  def table(patterns: String*): DbMatch =
    Empty.copy(table = patterns.toList)

  def schema(patterns: String*): DbMatch =
    Empty.copy(schema = patterns.toList)

  def database(names: String*): DbMatch =
    Empty.copy(database = names.toList)

  def domain(patterns: String*): DbMatch =
    Empty.copy(domain = patterns.toList)

  def primaryKey: DbMatch =
    Empty.copy(primaryKey = Some(true))

  def references(patterns: String*): DbMatch =
    Empty.copy(references = patterns.toList)

  def annotation(patterns: String*): DbMatch =
    Empty.copy(annotation = patterns.toList)
}

/** Where an OpenAPI parameter appears (not request/response bodies - use ModelMatch for those) */
sealed trait ApiLocation

object ApiLocation {
  case object Path extends ApiLocation
  case object Query extends ApiLocation
  case object Header extends ApiLocation
  case object Cookie extends ApiLocation
}

/** Predicate for matching schema/model properties.
  *
  * Works for both OpenAPI schemas and JSON Schema. Use this for request/response body fields. All non-empty fields must match (AND). Within each field's list, any match suffices (OR). Empty list
  * means "match any". Patterns starting with ! are exclusions.
  */
case class ModelMatch(
    /** Match specific spec names (OpenAPI or JSON Schema file names) */
    spec: List[String] = Nil,
    /** Match schema names / $ref (glob patterns) */
    schema: List[String] = Nil,
    /** Match property names (glob patterns) */
    name: List[String] = Nil,
    /** Match JSON path within schema */
    jsonPath: List[String] = Nil,
    /** Match schema type (string, integer, object, array, etc.) */
    schemaType: List[String] = Nil,
    /** Match format (uuid, date-time, email, etc.) */
    format: List[String] = Nil,
    /** Match required status */
    required: Option[Boolean] = None,
    /** Match x-* extension values */
    extension: Map[String, String] = Map.empty
)

object ModelMatch {
  val Empty: ModelMatch = ModelMatch()

  def name(patterns: String*): ModelMatch =
    Empty.copy(name = patterns.toList)

  def schema(patterns: String*): ModelMatch =
    Empty.copy(schema = patterns.toList)

  def format(formats: String*): ModelMatch =
    Empty.copy(format = formats.toList)

  def schemaType(types: String*): ModelMatch =
    Empty.copy(schemaType = types.toList)

  def spec(specs: String*): ModelMatch =
    Empty.copy(spec = specs.toList)

  def extension(key: String, value: String): ModelMatch =
    Empty.copy(extension = Map(key -> value))
}

/** Predicate for matching OpenAPI parameters (not request/response bodies - use ModelMatch for those).
  *
  * All non-empty fields must match (AND). Within each field's list, any match suffices (OR). Empty list means "match any". Patterns starting with ! are exclusions.
  */
case class ApiMatch(
    /** Match parameter location (header, query, path, cookie) */
    location: List[ApiLocation] = Nil,
    /** Match specific OpenAPI spec names (for multi-spec setups) */
    spec: List[String] = Nil,
    /** Match operation IDs (glob patterns) */
    operationId: List[String] = Nil,
    /** Match HTTP methods (GET, POST, etc.) */
    httpMethod: List[String] = Nil,
    /** Match URL paths (glob patterns) */
    path: List[String] = Nil,
    /** Match parameter names (glob patterns) */
    name: List[String] = Nil,
    /** Match required status */
    required: Option[Boolean] = None,
    /** Match x-* extension values */
    extension: Map[String, String] = Map.empty
)

object ApiMatch {
  val Empty: ApiMatch = ApiMatch()

  def name(patterns: String*): ApiMatch =
    Empty.copy(name = patterns.toList)

  def path(patterns: String*): ApiMatch =
    Empty.copy(path = patterns.toList)

  def operationId(patterns: String*): ApiMatch =
    Empty.copy(operationId = patterns.toList)

  def location(locations: ApiLocation*): ApiMatch =
    Empty.copy(location = locations.toList)

  def extension(key: String, value: String): ApiMatch =
    Empty.copy(extension = Map(key -> value))
}
