package typr
package internal
package pg

import typr.generated.information_schema.columns.ColumnsViewRow

import scala.util.matching.Regex

/** Pattern to extract precision from types like VARCHAR(30), DECIMAL(9, 4). Also handles trailing whitespace. */
private object TypeWithPrecision {
  private val pattern: Regex = """^([A-Z ]+)\((\d+)(?:,\s*\d+)?\)\s*$""".r

  def unapply(typeName: String): Option[(String, String)] = typeName match {
    case pattern(base, precision) => Some((base.trim, precision))
    case _                        => None
  }
}

/** PostgreSQL type mapper from database metadata to db.PgType */
case class PgTypeMapperDb(enums: List[db.StringEnum], domains: List[db.Domain], compositeTypes: List[PgCompositeType] = Nil) extends TypeMapperDb {
  val domainsByName: Map[String, db.Domain] = domains.flatMap(e => List((e.name.name, e), (e.name.value, e))).toMap
  val enumsByName = enums.flatMap(e => List((e.name.name, e), (e.name.value, e))).toMap
  val compositesByName: Map[String, db.PgType.CompositeType] =
    compositeTypes.flatMap(c => List((c.compositeType.name.name, c.compositeType), (c.compositeType.name.value, c.compositeType))).toMap

  def col(c: ColumnsViewRow)(logWarning: () => Unit): db.PgType = {
    val fromDomain: Option[db.PgType.DomainRef] =
      c.domainName.map { domainName =>
        val domainRelationName = db.RelationName(c.domainSchema, domainName)
        val d = domainsByName(domainRelationName.value)
        db.PgType.DomainRef(d.name, d.originalType, dbTypeFrom(d.originalType, c.characterMaximumLength)(logWarning))
      }

    fromDomain.getOrElse(dbTypeFrom(c.udtName.get, c.characterMaximumLength)(logWarning))
  }
  override def dbTypeFrom(udtName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.PgType = {
    // Strip precision/scale from types like "numeric(12,4)" or "varchar(255)"
    // The regex matches: type_name followed by optional (precision) or (precision,scale)
    val baseTypeName = udtName.replaceAll("\"", "").replaceAll("""\([0-9,\s]+\)""", "")
    baseTypeName match {
      case "bool"                              => db.PgType.Boolean
      case "box"                               => db.PgType.PGbox
      case "bpchar"                            => db.PgType.Bpchar(characterMaximumLength)
      case "bytea"                             => db.PgType.Bytea
      case "char"                              => db.PgType.Char
      case "cid"                               => db.PgType.Int4 // command identifier
      case "circle"                            => db.PgType.PGcircle
      case "date"                              => db.PgType.Date
      case "float4"                            => db.PgType.Float4
      case "float8"                            => db.PgType.Float8
      case "hstore"                            => db.PgType.Hstore
      case "inet"                              => db.PgType.Inet
      case "int2" | "smallint" | "smallserial" => db.PgType.Int2
      case "int4" | "integer" | "serial"       => db.PgType.Int4
      case "int8" | "bigint" | "bigserial"     => db.PgType.Int8
      case "interval"                          => db.PgType.PGInterval
      case "json"                              => db.PgType.Json
      case "jsonb"                             => db.PgType.Jsonb
      case "line"                              => db.PgType.PGline
      case "lseg"                              => db.PgType.PGlseg
      case "money"                             => db.PgType.PGmoney
      case "name"                              => db.PgType.Name
      case "numeric" | "decimal"               => db.PgType.Numeric
      case "oid"                               => db.PgType.Oid // numeric object identifier
      case "path"                              => db.PgType.PGpath
      case "pg_lsn"                            => db.PgType.PGlsn
      case "point"                             => db.PgType.PGpoint
      case "polygon"                           => db.PgType.PGpolygon
      case "text"                              => db.PgType.Text
      case "time"                              => db.PgType.Time
      case "timetz"                            => db.PgType.TimeTz
      case "timestamp"                         => db.PgType.Timestamp
      case "timestamptz"                       => db.PgType.TimestampTz
      case "uuid"                              => db.PgType.UUID
      case "varchar"                           => db.PgType.VarChar(characterMaximumLength)
      case "xml"                               => db.PgType.Xml
      case "aclitem"                           => db.PgType.aclitem
      case "anyarray"                          => db.PgType.anyarray
      case "int2vector"                        => db.PgType.int2vector
      case "oidvector"                         => db.PgType.oidvector
      case "pg_node_tree"                      => db.PgType.pg_node_tree
      case "record"                            => db.PgType.record
      case "regclass"                          => db.PgType.regclass
      case "regconfig"                         => db.PgType.regconfig
      case "regdictionary"                     => db.PgType.regdictionary
      case "regnamespace"                      => db.PgType.regnamespace
      case "regoper"                           => db.PgType.regoper
      case "regoperator"                       => db.PgType.regoperator
      case "regproc"                           => db.PgType.regproc
      case "regprocedure"                      => db.PgType.regprocedure
      case "regrole"                           => db.PgType.regrole
      case "regtype"                           => db.PgType.regtype
      case "xid"                               => db.PgType.xid
      case "vector"                            => db.PgType.Vector
      case ArrayName(underlying) =>
        db.PgType.Array(dbTypeFrom(underlying, characterMaximumLength)(logWarning))
      case typeName =>
        enumsByName
          .get(typeName)
          .map(db.PgType.EnumRef.apply)
          .orElse(domainsByName.get(typeName).map(domain => db.PgType.DomainRef(domain.name, domain.originalType, domain.tpe)))
          .orElse(compositesByName.get(typeName))
          .getOrElse {
            logWarning()
            db.Unknown(udtName)
          }
    }
  }

  /** Map from sqlglot normalized type names (uppercase) to PostgreSQL types. sqlglot normalizes types like int4 -> INT, varchar -> VARCHAR. This method handles those normalized forms. Also handles
    * types with precision like VARCHAR(30), NUMERIC(10,2) by extracting the precision.
    */
  def dbTypeFromSqlglot(typeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.PgType = {
    // Extract precision from types like VARCHAR(30), NUMERIC(10,2)
    val (baseType, extractedLength) = typeName match {
      case TypeWithPrecision(base, precision) => (base, Some(precision.toInt))
      case other                              => (other, None)
    }
    val effectiveLength = extractedLength.orElse(characterMaximumLength)

    baseType match {
      // Integer types (sqlglot normalizes to uppercase, but may also return native PG types)
      case "INT" | "INTEGER" | "INT4" => db.PgType.Int4
      case "SMALLINT" | "INT2"        => db.PgType.Int2
      case "BIGINT" | "INT8"          => db.PgType.Int8
      case "TINYINT"                  => db.PgType.Int2 // No tinyint in PG, use smallint

      // Floating point types
      case "REAL" | "FLOAT" | "FLOAT4"              => db.PgType.Float4
      case "DOUBLE PRECISION" | "DOUBLE" | "FLOAT8" => db.PgType.Float8

      // String types
      case "VARCHAR" | "CHAR VARYING" | "CHARACTER VARYING" => db.PgType.VarChar(effectiveLength)
      case "CHAR" | "CHARACTER"                             => db.PgType.Char
      case "TEXT"                                           => db.PgType.Text
      case "BPCHAR"                                         => db.PgType.Bpchar(effectiveLength)

      // Boolean
      case "BOOLEAN" | "BOOL" => db.PgType.Boolean

      // Date/Time types
      case "DATE"                                     => db.PgType.Date
      case "TIME"                                     => db.PgType.Time
      case "TIMETZ" | "TIME WITH TIME ZONE"           => db.PgType.TimeTz
      case "TIMESTAMP"                                => db.PgType.Timestamp
      case "TIMESTAMPTZ" | "TIMESTAMP WITH TIME ZONE" => db.PgType.TimestampTz
      case "INTERVAL"                                 => db.PgType.PGInterval

      // Numeric types
      case "NUMERIC" | "DECIMAL" => db.PgType.Numeric
      case "MONEY"               => db.PgType.PGmoney

      // Binary types
      case "BYTEA" => db.PgType.Bytea

      // JSON types
      case "JSON"  => db.PgType.Json
      case "JSONB" => db.PgType.Jsonb

      // UUID
      case "UUID" => db.PgType.UUID

      // XML
      case "XML" => db.PgType.Xml

      // Network types
      case "INET" => db.PgType.Inet

      // Geometric types
      case "BOX"     => db.PgType.PGbox
      case "CIRCLE"  => db.PgType.PGcircle
      case "LINE"    => db.PgType.PGline
      case "LSEG"    => db.PgType.PGlseg
      case "PATH"    => db.PgType.PGpath
      case "POINT"   => db.PgType.PGpoint
      case "POLYGON" => db.PgType.PGpolygon

      // Other PostgreSQL types
      case "HSTORE"    => db.PgType.Hstore
      case "OID"       => db.PgType.Oid
      case "NAME"      => db.PgType.Name
      case "VECTOR"    => db.PgType.Vector
      case "VARBINARY" => db.PgType.Bytea // sqlglot may report VARBINARY for binary data

      // Record type (PostgreSQL anonymous records from ROW(...))
      // Can be simple "RECORD" or typed "RECORD<field1:type1,field2:type2>"
      case "RECORD"                                                                   => db.PgType.record
      case recordType if recordType.startsWith("RECORD<") && recordType.endsWith(">") =>
        // TODO: Parse the field structure when we support typed records
        // For now, just use the untyped record type
        db.PgType.record

      // Unknown type - lowercase it to match existing behavior
      case "UNKNOWN" =>
        logWarning()
        db.Unknown("unknown")

      // Array types - sqlglot uses ARRAY<element_type> or element_type[]
      // Note: element type can contain nested <> (e.g., ARRAY<RECORD<a:INT,b:TEXT>>)
      // so we need to find the matching > for the ARRAY< prefix
      case arrayType if arrayType.startsWith("ARRAY<") && arrayType.endsWith(">") =>
        val inner = arrayType.stripPrefix("ARRAY<")
        // Find the matching closing > for ARRAY<
        val elementType = inner.dropRight(1) // remove the final >
        db.PgType.Array(dbTypeFromSqlglot(elementType, effectiveLength)(logWarning))
      case arrayType if arrayType.endsWith("[]") =>
        val elementType = arrayType.stripSuffix("[]")
        db.PgType.Array(dbTypeFromSqlglot(elementType, effectiveLength)(logWarning))

      // Fall back to trying the PostgreSQL native type mapper (for enums, domains, and lowercase types)
      case other =>
        dbTypeFrom(other, effectiveLength)(logWarning)
    }
  }
}
