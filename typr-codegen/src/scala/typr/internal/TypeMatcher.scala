package typr
package internal

/** Evaluates TypeDefinitions predicates against database columns, model properties, and API parameters. */
object TypeMatcher {

  /** Result of matching a TypeEntry against a source */
  case class MatchResult(
      entry: TypeEntry,
      source: MatchSource,
      dbType: db.Type
  )

  sealed trait MatchSource
  object MatchSource {
    case class DbColumn(database: String, schema: String, table: String, column: String) extends MatchSource
    case class ModelField(spec: String, schema: String, fieldName: String) extends MatchSource
    case class ApiParam(spec: String, path: String, paramName: String, location: ApiLocation) extends MatchSource
  }

  /** Context needed to match a database column */
  case class DbColumnContext(
      databaseName: String,
      schema: String,
      table: String,
      col: db.Col,
      isPrimaryKey: Boolean,
      foreignKeys: List[db.ForeignKey]
  ) {

    /** Get foreign key references for this column as "table.column" strings */
    def references: List[String] =
      foreignKeys.flatMap { fk =>
        fk.cols.toList.zip(fk.otherCols.toList).collectFirst {
          case (colName, otherColName) if colName == col.name =>
            s"${fk.otherTable.value}.${otherColName.value}"
        }
      }
  }

  object DbColumnContext {

    /** Construct from ComputedTable and column */
    def from(databaseName: String, table: db.Table, col: db.Col): DbColumnContext = {
      val isPk = table.primaryKey.exists(_.colNames.toList.contains(col.name))
      DbColumnContext(
        databaseName = databaseName,
        schema = table.name.schema.getOrElse(""),
        table = table.name.name,
        col = col,
        isPrimaryKey = isPk,
        foreignKeys = table.foreignKeys
      )
    }
  }

  /** Find all TypeEntry matches for a database column */
  def findDbMatches(
      definitions: TypeDefinitions,
      ctx: DbColumnContext
  ): List[MatchResult] =
    definitions.entries
      .filter { entry =>
        entry.db != DbMatch.Empty && matchesDb(entry.db, ctx)
      }
      .map { entry =>
        MatchResult(
          entry = entry,
          source = MatchSource.DbColumn(ctx.databaseName, ctx.schema, ctx.table, ctx.col.name.value),
          dbType = ctx.col.tpe
        )
      }

  /** Evaluate a DbMatch predicate against a column context */
  def matchesDb(
      m: DbMatch,
      ctx: DbColumnContext
  ): Boolean = {
    val col = ctx.col

    matchesPatterns(m.database, ctx.databaseName) &&
    matchesPatterns(m.schema, ctx.schema) &&
    matchesPatterns(m.table, ctx.table) &&
    matchesPatterns(m.column, col.name.value) &&
    matchesPatterns(m.dbType, typeName(col.tpe)) &&
    matchesPatterns(m.domain, domainName(col.tpe)) &&
    matchesOpt(m.primaryKey, ctx.isPrimaryKey) &&
    matchesOpt(m.nullable, col.nullability == Nullability.Nullable) &&
    matchesPatterns(m.references, ctx.references.mkString(",")) &&
    matchesPatterns(m.comment, col.comment.getOrElse("")) &&
    matchesPatterns(m.annotation, extractAnnotations(col.comment).mkString(","))
  }

  /** Extract the type name for matching */
  private def typeName(tpe: db.Type): String = tpe match {
    // PostgreSQL types
    case db.PgType.Array(inner)           => s"${typeName(inner)}[]"
    case db.PgType.Boolean                => "boolean"
    case db.PgType.Bpchar(len)            => len.fold("bpchar")(l => s"bpchar($l)")
    case db.PgType.Bytea                  => "bytea"
    case db.PgType.Char                   => "char"
    case db.PgType.Date                   => "date"
    case db.PgType.DomainRef(name, _, _)  => name.value
    case db.PgType.Float4                 => "float4"
    case db.PgType.Float8                 => "float8"
    case db.PgType.Hstore                 => "hstore"
    case db.PgType.Inet                   => "inet"
    case db.PgType.Cidr                   => "cidr"
    case db.PgType.MacAddr                => "macaddr"
    case db.PgType.MacAddr8               => "macaddr8"
    case db.PgType.Int2                   => "int2"
    case db.PgType.Int4                   => "int4"
    case db.PgType.Int8                   => "int8"
    case db.PgType.Json                   => "json"
    case db.PgType.Jsonb                  => "jsonb"
    case db.PgType.Name                   => "name"
    case db.PgType.Numeric                => "numeric"
    case db.PgType.Oid                    => "oid"
    case db.PgType.PGInterval             => "interval"
    case db.PgType.PGbox                  => "box"
    case db.PgType.PGcircle               => "circle"
    case db.PgType.PGline                 => "line"
    case db.PgType.PGlsn                  => "pg_lsn"
    case db.PgType.PGlseg                 => "lseg"
    case db.PgType.PGmoney                => "money"
    case db.PgType.PGpath                 => "path"
    case db.PgType.PGpoint                => "point"
    case db.PgType.PGpolygon              => "polygon"
    case db.PgType.aclitem                => "aclitem"
    case db.PgType.anyarray               => "anyarray"
    case db.PgType.int2vector             => "int2vector"
    case db.PgType.oidvector              => "oidvector"
    case db.PgType.pg_node_tree           => "pg_node_tree"
    case db.PgType.record                 => "record"
    case db.PgType.regclass               => "regclass"
    case db.PgType.regconfig              => "regconfig"
    case db.PgType.regdictionary          => "regdictionary"
    case db.PgType.regnamespace           => "regnamespace"
    case db.PgType.regoper                => "regoper"
    case db.PgType.regoperator            => "regoperator"
    case db.PgType.regproc                => "regproc"
    case db.PgType.regprocedure           => "regprocedure"
    case db.PgType.regrole                => "regrole"
    case db.PgType.regtype                => "regtype"
    case db.PgType.xid                    => "xid"
    case db.PgType.EnumRef(enm)           => enm.name.value
    case db.PgType.Text                   => "text"
    case db.PgType.Time                   => "time"
    case db.PgType.TimeTz                 => "timetz"
    case db.PgType.Timestamp              => "timestamp"
    case db.PgType.TimestampTz            => "timestamptz"
    case db.PgType.UUID                   => "uuid"
    case db.PgType.Xml                    => "xml"
    case db.PgType.VarChar(len)           => len.fold("varchar")(l => s"varchar($l)")
    case db.PgType.Vector                 => "vector"
    case db.PgType.CompositeType(name, _) => name.value

    // MariaDB types
    case db.MariaType.TinyInt            => "tinyint"
    case db.MariaType.SmallInt           => "smallint"
    case db.MariaType.MediumInt          => "mediumint"
    case db.MariaType.Int                => "int"
    case db.MariaType.BigInt             => "bigint"
    case db.MariaType.TinyIntUnsigned    => "tinyint unsigned"
    case db.MariaType.SmallIntUnsigned   => "smallint unsigned"
    case db.MariaType.MediumIntUnsigned  => "mediumint unsigned"
    case db.MariaType.IntUnsigned        => "int unsigned"
    case db.MariaType.BigIntUnsigned     => "bigint unsigned"
    case db.MariaType.Decimal(_, _)      => "decimal"
    case db.MariaType.Float              => "float"
    case db.MariaType.Double             => "double"
    case db.MariaType.Boolean            => "boolean"
    case db.MariaType.Bit(_)             => "bit"
    case db.MariaType.Char(_)            => "char"
    case db.MariaType.VarChar(_)         => "varchar"
    case db.MariaType.TinyText           => "tinytext"
    case db.MariaType.Text               => "text"
    case db.MariaType.MediumText         => "mediumtext"
    case db.MariaType.LongText           => "longtext"
    case db.MariaType.Binary(_)          => "binary"
    case db.MariaType.VarBinary(_)       => "varbinary"
    case db.MariaType.TinyBlob           => "tinyblob"
    case db.MariaType.Blob               => "blob"
    case db.MariaType.MediumBlob         => "mediumblob"
    case db.MariaType.LongBlob           => "longblob"
    case db.MariaType.Date               => "date"
    case db.MariaType.Time(_)            => "time"
    case db.MariaType.DateTime(_)        => "datetime"
    case db.MariaType.Timestamp(_)       => "timestamp"
    case db.MariaType.Year               => "year"
    case db.MariaType.Enum(_)            => "enum"
    case db.MariaType.Set(_)             => "set"
    case db.MariaType.Inet4              => "inet4"
    case db.MariaType.Inet6              => "inet6"
    case db.MariaType.Geometry           => "geometry"
    case db.MariaType.Point              => "point"
    case db.MariaType.LineString         => "linestring"
    case db.MariaType.Polygon            => "polygon"
    case db.MariaType.MultiPoint         => "multipoint"
    case db.MariaType.MultiLineString    => "multilinestring"
    case db.MariaType.MultiPolygon       => "multipolygon"
    case db.MariaType.GeometryCollection => "geometrycollection"
    case db.MariaType.Json               => "json"

    // DuckDB types
    case db.DuckDbType.TinyInt             => "tinyint"
    case db.DuckDbType.SmallInt            => "smallint"
    case db.DuckDbType.Integer             => "integer"
    case db.DuckDbType.BigInt              => "bigint"
    case db.DuckDbType.HugeInt             => "hugeint"
    case db.DuckDbType.UTinyInt            => "utinyint"
    case db.DuckDbType.USmallInt           => "usmallint"
    case db.DuckDbType.UInteger            => "uinteger"
    case db.DuckDbType.UBigInt             => "ubigint"
    case db.DuckDbType.UHugeInt            => "uhugeint"
    case db.DuckDbType.Float               => "float"
    case db.DuckDbType.Double              => "double"
    case db.DuckDbType.Decimal(_, _)       => "decimal"
    case db.DuckDbType.Boolean             => "boolean"
    case db.DuckDbType.VarChar(_)          => "varchar"
    case db.DuckDbType.Char(_)             => "char"
    case db.DuckDbType.Text                => "text"
    case db.DuckDbType.Blob                => "blob"
    case db.DuckDbType.Bit(_)              => "bit"
    case db.DuckDbType.Date                => "date"
    case db.DuckDbType.Time                => "time"
    case db.DuckDbType.Timestamp           => "timestamp"
    case db.DuckDbType.TimestampTz         => "timestamptz"
    case db.DuckDbType.TimestampS          => "timestamp_s"
    case db.DuckDbType.TimestampMS         => "timestamp_ms"
    case db.DuckDbType.TimestampNS         => "timestamp_ns"
    case db.DuckDbType.TimeTz              => "timetz"
    case db.DuckDbType.Interval            => "interval"
    case db.DuckDbType.UUID                => "uuid"
    case db.DuckDbType.Json                => "json"
    case db.DuckDbType.Enum(name, _)       => name
    case db.DuckDbType.ListType(inner)     => s"${typeName(inner)}[]"
    case db.DuckDbType.ArrayType(inner, _) => s"${typeName(inner)}[]"
    case db.DuckDbType.MapType(k, v)       => s"map(${typeName(k)},${typeName(v)})"
    case db.DuckDbType.StructType(_)       => "struct"
    case db.DuckDbType.UnionType(_)        => "union"

    // Oracle types
    case db.OracleType.Number(_, _)                  => "number"
    case db.OracleType.BinaryFloat                   => "binary_float"
    case db.OracleType.BinaryDouble                  => "binary_double"
    case db.OracleType.Float(_)                      => "float"
    case db.OracleType.Varchar2(_)                   => "varchar2"
    case db.OracleType.NVarchar2(_)                  => "nvarchar2"
    case db.OracleType.Char(_)                       => "char"
    case db.OracleType.NChar(_)                      => "nchar"
    case db.OracleType.Clob                          => "clob"
    case db.OracleType.NClob                         => "nclob"
    case db.OracleType.Long                          => "long"
    case db.OracleType.Raw(_)                        => "raw"
    case db.OracleType.Blob                          => "blob"
    case db.OracleType.LongRaw                       => "long raw"
    case db.OracleType.Date                          => "date"
    case db.OracleType.Timestamp(_)                  => "timestamp"
    case db.OracleType.TimestampWithTimeZone(_)      => "timestamp with time zone"
    case db.OracleType.TimestampWithLocalTimeZone(_) => "timestamp with local time zone"
    case db.OracleType.IntervalYearToMonth(_)        => "interval year to month"
    case db.OracleType.IntervalDayToSecond(_, _)     => "interval day to second"
    case db.OracleType.RowId                         => "rowid"
    case db.OracleType.URowId(_)                     => "urowid"
    case db.OracleType.XmlType                       => "xmltype"
    case db.OracleType.Json                          => "json"
    case db.OracleType.Boolean                       => "boolean"
    case db.OracleType.ObjectType(name, _, _, _, _)  => name.value
    case db.OracleType.VArray(name, _, _)            => name.value
    case db.OracleType.NestedTable(name, _, _)       => name.value
    case db.OracleType.RefType(objTpe)               => s"ref ${objTpe.value}"
    case db.OracleType.SdoGeometry                   => "sdo_geometry"
    case db.OracleType.SdoPoint                      => "sdo_point"
    case db.OracleType.AnyData                       => "anydata"

    // SQL Server types
    case db.SqlServerType.TinyInt                     => "tinyint"
    case db.SqlServerType.SmallInt                    => "smallint"
    case db.SqlServerType.Int                         => "int"
    case db.SqlServerType.BigInt                      => "bigint"
    case db.SqlServerType.Decimal(_, _)               => "decimal"
    case db.SqlServerType.Numeric(_, _)               => "numeric"
    case db.SqlServerType.Money                       => "money"
    case db.SqlServerType.SmallMoney                  => "smallmoney"
    case db.SqlServerType.Float                       => "float"
    case db.SqlServerType.Real                        => "real"
    case db.SqlServerType.Bit                         => "bit"
    case db.SqlServerType.Char(_)                     => "char"
    case db.SqlServerType.VarChar(_)                  => "varchar"
    case db.SqlServerType.Text                        => "text"
    case db.SqlServerType.NChar(_)                    => "nchar"
    case db.SqlServerType.NVarChar(_)                 => "nvarchar"
    case db.SqlServerType.NText                       => "ntext"
    case db.SqlServerType.Binary(_)                   => "binary"
    case db.SqlServerType.VarBinary(_)                => "varbinary"
    case db.SqlServerType.Image                       => "image"
    case db.SqlServerType.Date                        => "date"
    case db.SqlServerType.Time(_)                     => "time"
    case db.SqlServerType.DateTime                    => "datetime"
    case db.SqlServerType.SmallDateTime               => "smalldatetime"
    case db.SqlServerType.DateTime2(_)                => "datetime2"
    case db.SqlServerType.DateTimeOffset(_)           => "datetimeoffset"
    case db.SqlServerType.UniqueIdentifier            => "uniqueidentifier"
    case db.SqlServerType.Xml                         => "xml"
    case db.SqlServerType.Json                        => "json"
    case db.SqlServerType.Vector                      => "vector"
    case db.SqlServerType.Geography                   => "geography"
    case db.SqlServerType.Geometry                    => "geometry"
    case db.SqlServerType.RowVersion                  => "rowversion"
    case db.SqlServerType.HierarchyId                 => "hierarchyid"
    case db.SqlServerType.SqlVariant                  => "sql_variant"
    case db.SqlServerType.TableTypeRef(name, _)       => name.value
    case db.SqlServerType.AliasTypeRef(name, _, _, _) => name.value
    case db.SqlServerType.ClrTypeRef(name, _, _)      => name.value

    // DB2 types
    case db.DB2Type.SmallInt              => "smallint"
    case db.DB2Type.Integer               => "integer"
    case db.DB2Type.BigInt                => "bigint"
    case db.DB2Type.Decimal(_, _)         => "decimal"
    case db.DB2Type.DecFloat(_)           => "decfloat"
    case db.DB2Type.Real                  => "real"
    case db.DB2Type.Double                => "double"
    case db.DB2Type.Boolean               => "boolean"
    case db.DB2Type.Char(_)               => "char"
    case db.DB2Type.VarChar(_)            => "varchar"
    case db.DB2Type.Clob                  => "clob"
    case db.DB2Type.Long                  => "long"
    case db.DB2Type.Graphic(_)            => "graphic"
    case db.DB2Type.VarGraphic(_)         => "vargraphic"
    case db.DB2Type.DbClob                => "dbclob"
    case db.DB2Type.LongVarGraphic        => "long vargraphic"
    case db.DB2Type.Binary(_)             => "binary"
    case db.DB2Type.VarBinary(_)          => "varbinary"
    case db.DB2Type.Blob                  => "blob"
    case db.DB2Type.Date                  => "date"
    case db.DB2Type.Time                  => "time"
    case db.DB2Type.Timestamp(_)          => "timestamp"
    case db.DB2Type.Xml                   => "xml"
    case db.DB2Type.RowId                 => "rowid"
    case db.DB2Type.DistinctType(name, _) => name.value

    // Unknown
    case db.Unknown(sqlType) => sqlType
  }

  /** Extract domain name if this is a domain reference type */
  private def domainName(tpe: db.Type): String = tpe match {
    case db.PgType.DomainRef(name, _, _)              => name.value
    case db.DB2Type.DistinctType(name, _)             => name.value
    case db.SqlServerType.AliasTypeRef(name, _, _, _) => name.value
    case _                                            => ""
  }

  /** Check if a value matches patterns with support for negation (!pattern).
    *
    * Patterns starting with ! are exclusions. Match logic:
    *   1. Empty list → match any 2. Split patterns into includes (no !) and excludes (with !) 3. If includes exist, value must match at least one include 4. Value must not match any exclude
    */
  private def matchesPatterns(patterns: List[String], value: String): Boolean = {
    if (patterns.isEmpty) return true

    val (excludes, includes) = patterns.partition(_.startsWith("!"))
    val excludePatterns = excludes.map(_.stripPrefix("!"))

    val matchesInclude = includes.isEmpty || includes.exists(p => globMatch(p, value))
    val matchesExclude = excludePatterns.exists(p => globMatch(p, value))

    matchesInclude && !matchesExclude
  }

  /** Check if an optional constraint matches (if constraint is specified). None means "match any".
    */
  private def matchesOpt[A](constraint: Option[A], value: A): Boolean =
    constraint.isEmpty || constraint.contains(value)

  /** Simple glob matching: * matches any sequence, ? matches single char */
  def globMatch(pattern: String, value: String): Boolean = {
    val regex = pattern
      .replace(".", "\\.")
      .replace("*", ".*")
      .replace("?", ".")
    value.matches(s"(?i)$regex") // case-insensitive
  }

  /** Extract @annotations from column comments. Format: @annotation or @annotation(value)
    */
  private def extractAnnotations(comment: Option[String]): List[String] =
    comment match {
      case None => Nil
      case Some(text) =>
        val pattern = """@(\w+)(?:\(([^)]*)\))?""".r
        pattern
          .findAllMatchIn(text)
          .map { m =>
            val name = m.group(1)
            val value = Option(m.group(2))
            value.fold(name)(v => s"$name($v)")
          }
          .toList
    }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Model Property Matching (for OpenAPI schemas and JSON Schema)
  // ═══════════════════════════════════════════════════════════════════════════════

  /** Context needed to match a model property */
  case class ModelFieldContext(
      specName: String,
      schemaName: String,
      fieldName: String,
      jsonPath: String,
      typeInfo: openapi.TypeInfo,
      required: Boolean,
      extensions: Map[String, String]
  )

  /** Find all TypeEntry matches for a model property */
  def findModelMatches(
      definitions: TypeDefinitions,
      ctx: ModelFieldContext
  ): List[MatchResult] =
    definitions.entries
      .filter { entry =>
        entry.model != ModelMatch.Empty && matchesModel(entry.model, ctx)
      }
      .map { entry =>
        MatchResult(
          entry = entry,
          source = MatchSource.ModelField(ctx.specName, ctx.schemaName, ctx.fieldName),
          dbType = typeInfoToDbType(ctx.typeInfo)
        )
      }

  /** Evaluate a ModelMatch predicate against a model field context */
  def matchesModel(
      m: ModelMatch,
      ctx: ModelFieldContext
  ): Boolean = {
    matchesPatterns(m.spec, ctx.specName) &&
    matchesPatterns(m.schema, ctx.schemaName) &&
    matchesPatterns(m.name, ctx.fieldName) &&
    matchesPatterns(m.jsonPath, ctx.jsonPath) &&
    matchesPatterns(m.schemaType, schemaTypeName(ctx.typeInfo)) &&
    matchesPatterns(m.format, schemaFormat(ctx.typeInfo)) &&
    matchesOpt(m.required, ctx.required) &&
    matchesExtensions(m.extension, ctx.extensions)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // API Parameter Matching (for OpenAPI parameters only)
  // ═══════════════════════════════════════════════════════════════════════════════

  /** Context needed to match an API parameter */
  case class ApiParamContext(
      specName: String,
      operationId: String,
      httpMethod: String,
      path: String,
      location: ApiLocation,
      paramName: String,
      typeInfo: openapi.TypeInfo,
      required: Boolean,
      extensions: Map[String, String]
  )

  object ApiParamContext {

    /** Create context for a path/query/header/cookie parameter */
    def fromParameter(
        specName: String,
        method: openapi.ApiMethod,
        param: openapi.ApiParameter
    ): ApiParamContext = {
      val location = param.in match {
        case openapi.ParameterIn.Path   => ApiLocation.Path
        case openapi.ParameterIn.Query  => ApiLocation.Query
        case openapi.ParameterIn.Header => ApiLocation.Header
        case openapi.ParameterIn.Cookie => ApiLocation.Cookie
      }
      ApiParamContext(
        specName = specName,
        operationId = method.name,
        httpMethod = httpMethodString(method.httpMethod),
        path = method.path,
        location = location,
        paramName = param.name,
        typeInfo = param.typeInfo,
        required = param.required,
        extensions = Map.empty
      )
    }

    private def httpMethodString(method: openapi.HttpMethod): String = method match {
      case openapi.HttpMethod.Get     => "GET"
      case openapi.HttpMethod.Post    => "POST"
      case openapi.HttpMethod.Put     => "PUT"
      case openapi.HttpMethod.Delete  => "DELETE"
      case openapi.HttpMethod.Patch   => "PATCH"
      case openapi.HttpMethod.Head    => "HEAD"
      case openapi.HttpMethod.Options => "OPTIONS"
    }
  }

  /** Find all TypeEntry matches for an API parameter */
  def findApiMatches(
      definitions: TypeDefinitions,
      ctx: ApiParamContext
  ): List[MatchResult] =
    definitions.entries
      .filter { entry =>
        entry.api != ApiMatch.Empty && matchesApi(entry.api, ctx)
      }
      .map { entry =>
        MatchResult(
          entry = entry,
          source = MatchSource.ApiParam(ctx.specName, ctx.path, ctx.paramName, ctx.location),
          dbType = typeInfoToDbType(ctx.typeInfo)
        )
      }

  /** Evaluate an ApiMatch predicate against an API parameter context */
  def matchesApi(
      m: ApiMatch,
      ctx: ApiParamContext
  ): Boolean = {
    matchesLocation(m.location, ctx.location) &&
    matchesPatterns(m.spec, ctx.specName) &&
    matchesPatterns(m.operationId, ctx.operationId) &&
    matchesPatterns(m.httpMethod, ctx.httpMethod) &&
    matchesPatterns(m.path, ctx.path) &&
    matchesPatterns(m.name, ctx.paramName) &&
    matchesOpt(m.required, ctx.required) &&
    matchesExtensions(m.extension, ctx.extensions)
  }

  /** Check if a location matches any of the allowed locations */
  private def matchesLocation(patterns: List[ApiLocation], value: ApiLocation): Boolean =
    patterns.isEmpty || patterns.contains(value)

  /** Check if all required extensions match */
  private def matchesExtensions(required: Map[String, String], actual: Map[String, String]): Boolean =
    required.forall { case (key, pattern) =>
      actual.get(key).exists(value => globMatch(pattern, value))
    }

  /** Extract OpenAPI schema type name for matching */
  private def schemaTypeName(typeInfo: openapi.TypeInfo): String = typeInfo match {
    case openapi.TypeInfo.Primitive(prim) => primitiveTypeName(prim)
    case openapi.TypeInfo.ListOf(_)       => "array"
    case openapi.TypeInfo.Optional(inner) => schemaTypeName(inner)
    case openapi.TypeInfo.MapOf(_, _)     => "object"
    case openapi.TypeInfo.Ref(_)          => "object"
    case openapi.TypeInfo.Any             => "any"
    case openapi.TypeInfo.InlineEnum(_)   => "string"
  }

  private def primitiveTypeName(prim: openapi.PrimitiveType): String = prim match {
    case openapi.PrimitiveType.String     => "string"
    case openapi.PrimitiveType.Int32      => "integer"
    case openapi.PrimitiveType.Int64      => "integer"
    case openapi.PrimitiveType.Float      => "number"
    case openapi.PrimitiveType.Double     => "number"
    case openapi.PrimitiveType.Boolean    => "boolean"
    case openapi.PrimitiveType.Date       => "string"
    case openapi.PrimitiveType.DateTime   => "string"
    case openapi.PrimitiveType.Time       => "string"
    case openapi.PrimitiveType.UUID       => "string"
    case openapi.PrimitiveType.URI        => "string"
    case openapi.PrimitiveType.Email      => "string"
    case openapi.PrimitiveType.Binary     => "string"
    case openapi.PrimitiveType.Byte       => "string"
    case openapi.PrimitiveType.BigDecimal => "number"
  }

  /** Extract OpenAPI format for matching */
  private def schemaFormat(typeInfo: openapi.TypeInfo): String = typeInfo match {
    case openapi.TypeInfo.Primitive(prim) => primitiveFormat(prim)
    case openapi.TypeInfo.Optional(inner) => schemaFormat(inner)
    case _                                => ""
  }

  private def primitiveFormat(prim: openapi.PrimitiveType): String = prim match {
    case openapi.PrimitiveType.String     => ""
    case openapi.PrimitiveType.Int32      => "int32"
    case openapi.PrimitiveType.Int64      => "int64"
    case openapi.PrimitiveType.Float      => "float"
    case openapi.PrimitiveType.Double     => "double"
    case openapi.PrimitiveType.Boolean    => ""
    case openapi.PrimitiveType.Date       => "date"
    case openapi.PrimitiveType.DateTime   => "date-time"
    case openapi.PrimitiveType.Time       => "time"
    case openapi.PrimitiveType.UUID       => "uuid"
    case openapi.PrimitiveType.URI        => "uri"
    case openapi.PrimitiveType.Email      => "email"
    case openapi.PrimitiveType.Binary     => "binary"
    case openapi.PrimitiveType.Byte       => "byte"
    case openapi.PrimitiveType.BigDecimal => "decimal"
  }

  /** Convert OpenAPI TypeInfo to a db.Type for MatchResult. This creates a synthetic db.Type for type compatibility checking.
    */
  private def typeInfoToDbType(typeInfo: openapi.TypeInfo): db.Type = typeInfo match {
    case openapi.TypeInfo.Primitive(prim) => primitiveToDbType(prim)
    case openapi.TypeInfo.ListOf(inner)   => db.PgType.Array(typeInfoToDbType(inner))
    case openapi.TypeInfo.Optional(inner) => typeInfoToDbType(inner)
    case openapi.TypeInfo.MapOf(_, _)     => db.PgType.Jsonb
    case openapi.TypeInfo.Ref(_)          => db.PgType.Jsonb
    case openapi.TypeInfo.Any             => db.PgType.Jsonb
    case openapi.TypeInfo.InlineEnum(_)   => db.PgType.Text
  }

  private def primitiveToDbType(prim: openapi.PrimitiveType): db.Type = prim match {
    case openapi.PrimitiveType.String     => db.PgType.Text
    case openapi.PrimitiveType.Int32      => db.PgType.Int4
    case openapi.PrimitiveType.Int64      => db.PgType.Int8
    case openapi.PrimitiveType.Float      => db.PgType.Float4
    case openapi.PrimitiveType.Double     => db.PgType.Float8
    case openapi.PrimitiveType.Boolean    => db.PgType.Boolean
    case openapi.PrimitiveType.Date       => db.PgType.Date
    case openapi.PrimitiveType.DateTime   => db.PgType.TimestampTz
    case openapi.PrimitiveType.Time       => db.PgType.Time
    case openapi.PrimitiveType.UUID       => db.PgType.UUID
    case openapi.PrimitiveType.URI        => db.PgType.Text
    case openapi.PrimitiveType.Email      => db.PgType.Text
    case openapi.PrimitiveType.Binary     => db.PgType.Bytea
    case openapi.PrimitiveType.Byte       => db.PgType.Bytea
    case openapi.PrimitiveType.BigDecimal => db.PgType.Numeric
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Integration Helpers
  // ═══════════════════════════════════════════════════════════════════════════════

  /** Result of scanning OpenAPI spec for TypeDefinitions matches */
  case class OpenApiScanResult(
      /** All matches found, grouped by TypeEntry */
      matchesByEntry: Map[TypeEntry, List[MatchResult]],
      /** Compatibility check results for validation */
      compatibilityResults: List[TypeCompatibilityChecker.CheckResult],
      /** Errors found during compatibility checking */
      errors: List[String]
  )

  /** Scan an OpenAPI spec for TypeDefinitions matches.
    *
    * @param definitions
    *   The TypeDefinitions to match against
    * @param specName
    *   Name of the spec (for multi-spec matching)
    * @param spec
    *   The parsed OpenAPI spec
    * @return
    *   OpenApiScanResult with matches, compatibility results, and errors
    */
  def scanOpenApi(
      definitions: TypeDefinitions,
      specName: String,
      spec: openapi.ParsedSpec
  ): OpenApiScanResult = {
    val hasModelMatchers = definitions.entries.exists(_.model != ModelMatch.Empty)
    val hasApiMatchers = definitions.entries.exists(_.api != ApiMatch.Empty)

    if (definitions.isEmpty || (!hasModelMatchers && !hasApiMatchers)) {
      return OpenApiScanResult(Map.empty, Nil, Nil)
    }

    // Collect matches from model properties
    val modelMatches: List[MatchResult] = if (hasModelMatchers) {
      spec.models.flatMap {
        case obj: openapi.ModelClass.ObjectType =>
          obj.properties.flatMap { prop =>
            val ctx = ModelFieldContext(
              specName = specName,
              schemaName = obj.name,
              fieldName = prop.name,
              jsonPath = s"${obj.name}.${prop.name}",
              typeInfo = prop.typeInfo,
              required = prop.required,
              extensions = Map.empty
            )
            findModelMatches(definitions, ctx)
          }
        case _ => Nil
      }
    } else Nil

    // Collect matches from API parameters
    val paramMatches: List[MatchResult] = if (hasApiMatchers) {
      spec.apis.flatMap { api =>
        api.methods.flatMap { method =>
          method.parameters.flatMap { param =>
            val ctx = ApiParamContext.fromParameter(specName, method, param)
            findApiMatches(definitions, ctx)
          }
        }
      }
    } else Nil

    val allMatches = modelMatches ++ paramMatches

    // Group by TypeEntry
    val matchesByEntry: Map[TypeEntry, List[MatchResult]] =
      allMatches.groupBy(_.entry)

    // Check compatibility
    val compatibilityResults = definitions.entries.map { entry =>
      TypeCompatibilityChecker.check(entry, matchesByEntry.getOrElse(entry, Nil))
    }

    // Collect errors
    val errors = compatibilityResults.collect { case e: TypeCompatibilityChecker.CheckResult.Incompatible =>
      e.errorMessage
    }

    OpenApiScanResult(matchesByEntry, compatibilityResults, errors)
  }

  /** Result of scanning tables for TypeDefinitions matches */
  case class ScanResult(
      /** All matches found, grouped by TypeEntry */
      matchesByEntry: Map[TypeEntry, List[MatchResult]],
      /** TypeOverride to use during code generation */
      typeOverride: TypeOverride,
      /** Compatibility check results for validation */
      compatibilityResults: List[TypeCompatibilityChecker.CheckResult],
      /** Errors found during compatibility checking */
      errors: List[String]
  )

  /** Scan all tables for TypeDefinitions matches and build a TypeOverride.
    *
    * @param definitions
    *   The TypeDefinitions to match against
    * @param databaseName
    *   Name of the database (for multi-database matching)
    * @param tables
    *   All tables to scan
    * @param sharedTypesPackage
    *   Package where shared types will be generated
    * @return
    *   ScanResult with matches, TypeOverride, and any errors
    */
  def scanTables(
      definitions: TypeDefinitions,
      databaseName: String,
      tables: List[db.Table],
      sharedTypesPackage: jvm.QIdent
  ): ScanResult = {
    if (definitions.isEmpty) {
      return ScanResult(Map.empty, TypeOverride.Empty, Nil, Nil)
    }

    // Collect all matches
    val allMatches: List[MatchResult] = tables.flatMap { table =>
      table.cols.toList.flatMap { col =>
        val ctx = DbColumnContext.from(databaseName, table, col)
        findDbMatches(definitions, ctx)
      }
    }

    // Group by TypeEntry
    val matchesByEntry: Map[TypeEntry, List[MatchResult]] =
      allMatches.groupBy(_.entry)

    // Check compatibility
    val compatibilityResults = definitions.entries.map { entry =>
      TypeCompatibilityChecker.check(entry, matchesByEntry.getOrElse(entry, Nil))
    }

    // Collect errors
    val errors = compatibilityResults.collect { case e: TypeCompatibilityChecker.CheckResult.Incompatible =>
      e.errorMessage
    }

    // Build TypeOverride for compatible entries
    val typeOverrideMap: Map[(db.RelationName, db.ColName), String] = {
      val compatible = compatibilityResults.collect { case c: TypeCompatibilityChecker.CheckResult.Compatible =>
        c
      }

      compatible.flatMap { result =>
        val typeName = s"${sharedTypesPackage.dotName}.${result.entry.name}"
        result.matches.flatMap { m =>
          m.source match {
            case MatchSource.DbColumn(_, schema, table, column) =>
              val relName = db.RelationName(if (schema.isEmpty) None else Some(schema), table)
              Some((relName, db.ColName(column)) -> typeName)
            case _ => None
          }
        }
      }.toMap
    }

    val typeOverride: TypeOverride = (relation, colName) => typeOverrideMap.get((relation, colName))

    ScanResult(matchesByEntry, typeOverride, compatibilityResults, errors)
  }
}
