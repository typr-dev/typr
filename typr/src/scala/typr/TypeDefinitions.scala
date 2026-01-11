package typr

/** Defines shared wrapper types that can be used across databases and OpenAPI specs.
  *
  * Types are matched using predicates (DbMatch for databases, ApiMatch for OpenAPI). When a column or field matches, it uses the named type instead of the default generated type.
  *
  * Match semantics:
  *   - Empty list means "match any" for that field
  *   - Non-empty list means "match any of these patterns" (OR within field)
  *   - All non-empty fields must match (AND across fields)
  *   - String patterns support glob syntax (*, ?)
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
    db: DbMatch,
    /** Predicate for matching OpenAPI fields */
    api: ApiMatch
)

object TypeEntry {
  def apply(name: String): TypeEntry =
    TypeEntry(name, DbMatch.Empty, ApiMatch.Empty)

  def db(name: String, db: DbMatch): TypeEntry =
    TypeEntry(name, db, ApiMatch.Empty)

  def api(name: String, api: ApiMatch): TypeEntry =
    TypeEntry(name, DbMatch.Empty, api)
}

/** Predicate for matching database columns.
  *
  * All non-empty fields must match (AND). Within each field's list, any match suffices (OR). Empty list means "match any".
  */
case class DbMatch(
    /** Match specific database names (for multi-database setups) */
    database: List[String],
    /** Match schema names (glob patterns supported) */
    schema: List[String],
    /** Match table/view names (glob patterns supported) */
    table: List[String],
    /** Match column names (glob patterns supported) */
    column: List[String],
    /** Match database type names (e.g., "int4", "varchar") */
    dbType: List[String],
    /** Match domain names */
    domain: List[String],
    /** Match only primary key columns */
    primaryKey: Option[Boolean],
    /** Match nullability */
    nullable: Option[Boolean],
    /** Match columns that reference specific tables (glob patterns) */
    references: List[String],
    /** Match column comments (glob patterns) */
    comment: List[String],
    /** Match extracted annotations from comments */
    annotation: List[String]
)

object DbMatch {
  val Empty: DbMatch = DbMatch(
    database = Nil,
    schema = Nil,
    table = Nil,
    column = Nil,
    dbType = Nil,
    domain = Nil,
    primaryKey = None,
    nullable = None,
    references = Nil,
    comment = Nil,
    annotation = Nil
  )

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

/** Where an OpenAPI field appears in the API */
sealed trait ApiLocation

object ApiLocation {
  case object PathParam extends ApiLocation
  case object QueryParam extends ApiLocation
  case object HeaderParam extends ApiLocation
  case object CookieParam extends ApiLocation
  case object RequestBody extends ApiLocation
  case object ResponseBody extends ApiLocation

  // Convenience groupings
  case object AnyParam extends ApiLocation // Matches PathParam, QueryParam, HeaderParam, CookieParam
  case object AnyBody extends ApiLocation // Matches RequestBody, ResponseBody
}

/** Predicate for matching OpenAPI fields.
  *
  * All non-empty fields must match (AND). Within each field's list, any match suffices (OR). Empty list means "match any".
  */
case class ApiMatch(
    /** Match field location in API */
    location: List[ApiLocation],
    /** Match specific OpenAPI spec names (for multi-spec setups) */
    spec: List[String],
    /** Match operation IDs (glob patterns) */
    operationId: List[String],
    /** Match HTTP methods (GET, POST, etc.) */
    httpMethod: List[String],
    /** Match URL paths (glob patterns) */
    path: List[String],
    /** Match field/parameter names (glob patterns) */
    name: List[String],
    /** Match JSON path within request/response body */
    jsonPath: List[String],
    /** Match OpenAPI schema type (string, integer, etc.) */
    schemaType: List[String],
    /** Match OpenAPI format (uuid, date-time, etc.) */
    format: List[String],
    /** Match schema $ref names */
    schemaRef: List[String],
    /** Match required status */
    required: Option[Boolean],
    /** Match x-* extension values */
    extension: Map[String, String]
)

object ApiMatch {
  val Empty: ApiMatch = ApiMatch(
    location = Nil,
    spec = Nil,
    operationId = Nil,
    httpMethod = Nil,
    path = Nil,
    name = Nil,
    jsonPath = Nil,
    schemaType = Nil,
    format = Nil,
    schemaRef = Nil,
    required = None,
    extension = Map.empty
  )

  def name(patterns: String*): ApiMatch =
    Empty.copy(name = patterns.toList)

  def path(patterns: String*): ApiMatch =
    Empty.copy(path = patterns.toList)

  def operationId(patterns: String*): ApiMatch =
    Empty.copy(operationId = patterns.toList)

  def format(formats: String*): ApiMatch =
    Empty.copy(format = formats.toList)

  def schemaType(types: String*): ApiMatch =
    Empty.copy(schemaType = types.toList)

  def location(locations: ApiLocation*): ApiMatch =
    Empty.copy(location = locations.toList)

  def schemaRef(refs: String*): ApiMatch =
    Empty.copy(schemaRef = refs.toList)

  def extension(key: String, value: String): ApiMatch =
    Empty.copy(extension = Map(key -> value))
}
