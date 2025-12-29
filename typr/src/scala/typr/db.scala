package typr

import typr.internal.{DebugJson, quote}
import typr.internal.analysis.{DecomposedSql, ParsedName}

import scala.collection.immutable.SortedSet

/** Describes what tables look like in the database
  */
object db {
  sealed trait Type

  // PostgreSQL-specific types
  sealed trait PgType extends Type
  object PgType {
    case class Array(tpe: Type) extends PgType
    case object Boolean extends PgType
    case class Bpchar(maxLength: Option[Int]) extends PgType // blank padded character
    case object Bytea extends PgType
    case object Char extends PgType
    case object Date extends PgType
    case class DomainRef(name: RelationName, underlying: String, underlyingType: Type) extends PgType
    case object Float4 extends PgType
    case object Float8 extends PgType
    case object Hstore extends PgType
    case object Inet extends PgType
    case object Cidr extends PgType
    case object MacAddr extends PgType
    case object MacAddr8 extends PgType
    case object Int2 extends PgType
    case object Int4 extends PgType
    case object Int8 extends PgType
    case object Json extends PgType
    case object Jsonb extends PgType
    case object Name extends PgType
    case object Numeric extends PgType
    case object Oid extends PgType
    case object PGInterval extends PgType
    case object PGbox extends PgType
    case object PGcircle extends PgType
    case object PGline extends PgType
    case object PGlsn extends PgType
    case object PGlseg extends PgType
    case object PGmoney extends PgType
    case object PGpath extends PgType
    case object PGpoint extends PgType
    case object PGpolygon extends PgType
    case object aclitem extends PgType
    case object anyarray extends PgType
    case object int2vector extends PgType
    case object oidvector extends PgType
    case object pg_node_tree extends PgType
    case object record extends PgType
    case object regclass extends PgType
    case object regconfig extends PgType
    case object regdictionary extends PgType
    case object regnamespace extends PgType
    case object regoper extends PgType
    case object regoperator extends PgType
    case object regproc extends PgType
    case object regprocedure extends PgType
    case object regrole extends PgType
    case object regtype extends PgType
    case object xid extends PgType
    case class EnumRef(enm: StringEnum) extends PgType
    case object Text extends PgType
    case object Time extends PgType
    case object TimeTz extends PgType
    case object Timestamp extends PgType
    case object TimestampTz extends PgType
    case object UUID extends PgType
    case object Xml extends PgType
    case class VarChar(maxLength: Option[Int]) extends PgType
    case object Vector extends PgType
    // Composite type (CREATE TYPE name AS (field1 type1, field2 type2, ...))
    case class CompositeType(name: RelationName, fields: List[CompositeField]) extends PgType
    case class CompositeField(name: String, tpe: Type, nullable: Boolean)
  }

  // MariaDB-specific types
  sealed trait MariaType extends Type
  object MariaType {
    // Integer types (signed)
    case object TinyInt extends MariaType
    case object SmallInt extends MariaType
    case object MediumInt extends MariaType
    case object Int extends MariaType
    case object BigInt extends MariaType
    // Integer types (unsigned)
    case object TinyIntUnsigned extends MariaType
    case object SmallIntUnsigned extends MariaType
    case object MediumIntUnsigned extends MariaType
    case object IntUnsigned extends MariaType
    case object BigIntUnsigned extends MariaType
    // Fixed-point
    case class Decimal(precision: Option[Int], scale: Option[Int]) extends MariaType
    // Floating-point
    case object Float extends MariaType
    case object Double extends MariaType
    // Boolean (stored as TINYINT(1))
    case object Boolean extends MariaType
    // Bit
    case class Bit(length: Option[Int]) extends MariaType
    // String types
    case class Char(length: Option[Int]) extends MariaType
    case class VarChar(length: Option[Int]) extends MariaType
    case object TinyText extends MariaType
    case object Text extends MariaType
    case object MediumText extends MariaType
    case object LongText extends MariaType
    // Binary types
    case class Binary(length: Option[Int]) extends MariaType
    case class VarBinary(length: Option[Int]) extends MariaType
    case object TinyBlob extends MariaType
    case object Blob extends MariaType
    case object MediumBlob extends MariaType
    case object LongBlob extends MariaType
    // Date/Time types
    case object Date extends MariaType
    case class Time(fsp: Option[Int]) extends MariaType
    case class DateTime(fsp: Option[Int]) extends MariaType
    case class Timestamp(fsp: Option[Int]) extends MariaType
    case object Year extends MariaType
    // Special string types (inline enum, not a reference like PostgreSQL)
    case class Enum(values: List[String]) extends MariaType
    case class Set(values: List[String]) extends MariaType
    // Network types
    case object Inet4 extends MariaType
    case object Inet6 extends MariaType
    // Spatial types (MariaDB Connector/J bundled types)
    case object Geometry extends MariaType
    case object Point extends MariaType
    case object LineString extends MariaType
    case object Polygon extends MariaType
    case object MultiPoint extends MariaType
    case object MultiLineString extends MariaType
    case object MultiPolygon extends MariaType
    case object GeometryCollection extends MariaType
    // JSON (alias for LONGTEXT in MariaDB, but treated specially)
    case object Json extends MariaType
  }

  // DuckDB-specific types
  sealed trait DuckDbType extends Type
  object DuckDbType {
    // Integer types (signed)
    case object TinyInt extends DuckDbType
    case object SmallInt extends DuckDbType
    case object Integer extends DuckDbType
    case object BigInt extends DuckDbType
    case object HugeInt extends DuckDbType
    // Integer types (unsigned)
    case object UTinyInt extends DuckDbType
    case object USmallInt extends DuckDbType
    case object UInteger extends DuckDbType
    case object UBigInt extends DuckDbType
    case object UHugeInt extends DuckDbType
    // Floating-point
    case object Float extends DuckDbType
    case object Double extends DuckDbType
    // Fixed-point
    case class Decimal(precision: Option[Int], scale: Option[Int]) extends DuckDbType
    // Boolean
    case object Boolean extends DuckDbType
    // String types
    case class VarChar(maxLength: Option[Int]) extends DuckDbType
    case class Char(length: Option[Int]) extends DuckDbType
    case object Text extends DuckDbType
    // Binary
    case object Blob extends DuckDbType
    // Bit string
    case class Bit(length: Option[Int]) extends DuckDbType
    // Date/Time types
    case object Date extends DuckDbType
    case object Time extends DuckDbType
    case object Timestamp extends DuckDbType
    case object TimestampTz extends DuckDbType
    case object TimestampS extends DuckDbType
    case object TimestampMS extends DuckDbType
    case object TimestampNS extends DuckDbType
    case object TimeTz extends DuckDbType
    case object Interval extends DuckDbType
    // UUID
    case object UUID extends DuckDbType
    // JSON
    case object Json extends DuckDbType
    // Enum type
    case class Enum(name: String, values: List[String]) extends DuckDbType
    // Nested/Complex types
    case class ListType(elementType: Type) extends DuckDbType
    case class ArrayType(elementType: Type, size: Option[Int]) extends DuckDbType
    case class MapType(keyType: Type, valueType: Type) extends DuckDbType
    case class StructType(fields: List[(String, Type)]) extends DuckDbType
    case class UnionType(members: List[(String, Type)]) extends DuckDbType
  }

  // Oracle-specific types
  sealed trait OracleType extends Type
  object OracleType {
    // Numeric types
    case class Number(precision: Option[Int], scale: Option[Int]) extends OracleType
    case object BinaryFloat extends OracleType
    case object BinaryDouble extends OracleType
    case class Float(binaryPrecision: Option[Int]) extends OracleType
    // Character types
    case class Varchar2(maxLength: Option[Int]) extends OracleType
    case class NVarchar2(maxLength: Option[Int]) extends OracleType
    case class Char(length: Option[Int]) extends OracleType
    case class NChar(length: Option[Int]) extends OracleType
    case object Clob extends OracleType
    case object NClob extends OracleType
    case object Long extends OracleType // deprecated
    // Binary types
    case class Raw(maxLength: Option[Int]) extends OracleType
    case object Blob extends OracleType
    case object LongRaw extends OracleType // deprecated
    // Date/Time types
    case object Date extends OracleType // DATE includes time in Oracle
    case class Timestamp(fractionalSecondsPrecision: Option[Int]) extends OracleType
    case class TimestampWithTimeZone(fractionalSecondsPrecision: Option[Int]) extends OracleType
    case class TimestampWithLocalTimeZone(fractionalSecondsPrecision: Option[Int]) extends OracleType
    case class IntervalYearToMonth(yearPrecision: Option[Int]) extends OracleType
    case class IntervalDayToSecond(dayPrecision: Option[Int], fractionalSecondsPrecision: Option[Int]) extends OracleType
    // ROWID types
    case object RowId extends OracleType
    case class URowId(maxLength: Option[Int]) extends OracleType
    // XML/JSON types
    case object XmlType extends OracleType
    case object Json extends OracleType // 21c+
    // Boolean (23c+)
    case object Boolean extends OracleType

    // ═══ OBJECT-RELATIONAL TYPES ═══

    /** User-defined object type (CREATE TYPE AS OBJECT) */
    case class ObjectType(
        name: RelationName,
        attributes: List[ObjectAttribute],
        isFinal: Boolean,
        isInstantiable: Boolean,
        supertype: Option[RelationName]
    ) extends OracleType

    case class ObjectAttribute(
        name: String,
        tpe: Type,
        position: Int
    )

    /** VARRAY(n) OF type - Fixed-size array */
    case class VArray(
        name: RelationName,
        maxSize: Int,
        elementType: Type
    ) extends OracleType

    /** TABLE OF type - Nested table (variable-length collection) */
    case class NestedTable(
        name: RelationName,
        elementType: Type,
        storageTable: Option[String]
    ) extends OracleType

    /** REF type_name - Object reference */
    case class RefType(
        objectType: RelationName
    ) extends OracleType

    /** SDO_GEOMETRY - Oracle Spatial types */
    case object SdoGeometry extends OracleType
    case object SdoPoint extends OracleType

    /** ANYDATA - Dynamic type container (rarely used) */
    case object AnyData extends OracleType
  }

  // SQL Server-specific types
  sealed trait SqlServerType extends Type
  object SqlServerType {
    // ==================== Integer Types ====================
    case object TinyInt extends SqlServerType // 0-255 (UNSIGNED in SQL Server!)
    case object SmallInt extends SqlServerType // -32768 to 32767
    case object Int extends SqlServerType // -2^31 to 2^31-1
    case object BigInt extends SqlServerType // -2^63 to 2^63-1

    // ==================== Fixed-Point Types ====================
    case class Decimal(precision: Option[Int], scale: Option[Int]) extends SqlServerType
    case class Numeric(precision: Option[Int], scale: Option[Int]) extends SqlServerType
    case object Money extends SqlServerType // -922337203685477.5808 to 922337203685477.5807
    case object SmallMoney extends SqlServerType // -214748.3648 to 214748.3647

    // ==================== Floating-Point Types ====================
    case object Float extends SqlServerType // -1.79E+308 to 1.79E+308
    case object Real extends SqlServerType // -3.40E+38 to 3.40E+38

    // ==================== Boolean Type ====================
    case object Bit extends SqlServerType // 0 or 1 (boolean)

    // ==================== String Types (Non-Unicode) ====================
    case class Char(length: Option[Int]) extends SqlServerType // Fixed-length, max 8000
    case class VarChar(length: Option[Int]) extends SqlServerType // Variable-length, max 8000 or MAX
    case object Text extends SqlServerType // Deprecated, use VARCHAR(MAX)

    // ==================== String Types (Unicode) ====================
    case class NChar(length: Option[Int]) extends SqlServerType // Fixed-length Unicode, max 4000
    case class NVarChar(length: Option[Int]) extends SqlServerType // Variable-length Unicode, max 4000 or MAX
    case object NText extends SqlServerType // Deprecated, use NVARCHAR(MAX)

    // ==================== Binary Types ====================
    case class Binary(length: Option[Int]) extends SqlServerType // Fixed-length binary, max 8000
    case class VarBinary(length: Option[Int]) extends SqlServerType // Variable-length binary, max 8000 or MAX
    case object Image extends SqlServerType // Deprecated, use VARBINARY(MAX)

    // ==================== Date/Time Types ====================
    case object Date extends SqlServerType // 0001-01-01 to 9999-12-31
    case class Time(scale: Option[Int]) extends SqlServerType // Fractional seconds precision 0-7
    case object DateTime extends SqlServerType // 1753-01-01 to 9999-12-31 (legacy, 3.33ms precision)
    case object SmallDateTime extends SqlServerType // 1900-01-01 to 2079-06-06 (minute precision)
    case class DateTime2(scale: Option[Int]) extends SqlServerType // 0001-01-01 to 9999-12-31, 100ns precision
    case class DateTimeOffset(scale: Option[Int]) extends SqlServerType // DateTime2 with time zone offset

    // ==================== Special Types ====================
    case object UniqueIdentifier extends SqlServerType // GUID (UUID)
    case object Xml extends SqlServerType // XML documents
    case object Json extends SqlServerType // JSON (SQL Server 2016+)
    case object Vector extends SqlServerType // Vector data (SQL Server 2025)

    // ==================== Spatial Types ====================
    case object Geography extends SqlServerType // Earth-centric spatial data (lat/long)
    case object Geometry extends SqlServerType // Planar spatial data (x/y coordinates)

    // ==================== Special System Types ====================
    case object RowVersion extends SqlServerType // Timestamp/version number (8-byte binary)
    case object HierarchyId extends SqlServerType // Hierarchical data (tree structures)
    case object SqlVariant extends SqlServerType // Can store values of various types

    // ==================== Table-Valued Types (SQL Server Unique!) ====================
    /** Reference to user-defined table type */
    case class TableTypeRef(name: RelationName, columns: List[TableTypeColumn]) extends SqlServerType

    case class TableTypeColumn(name: String, tpe: Type, nullable: Nullability)

    // ==================== User-Defined Alias Types ====================
    /** User-defined type alias with optional constraint */
    case class AliasTypeRef(name: RelationName, underlying: String, underlyingType: Type, hasConstraint: Boolean) extends SqlServerType

    // ==================== CLR Types (Limited Support) ====================
    /** CLR user-defined type - we'll read as byte[] or String */
    case class ClrTypeRef(name: RelationName, assemblyName: String, className: String) extends SqlServerType
  }

  // DB2-specific types
  sealed trait DB2Type extends Type
  object DB2Type {
    // Integer types
    case object SmallInt extends DB2Type // 16-bit
    case object Integer extends DB2Type // 32-bit
    case object BigInt extends DB2Type // 64-bit

    // Fixed-point
    case class Decimal(precision: Option[Int], scale: Option[Int]) extends DB2Type
    case class DecFloat(precision: Option[Int]) extends DB2Type // DB2-specific: 16 or 34 digits

    // Floating-point
    case object Real extends DB2Type // Single precision
    case object Double extends DB2Type // Double precision

    // Boolean (DB2 11.1+)
    case object Boolean extends DB2Type

    // Character types (SBCS)
    case class Char(length: Option[Int]) extends DB2Type
    case class VarChar(length: Option[Int]) extends DB2Type
    case object Clob extends DB2Type
    case object Long extends DB2Type // Deprecated VARCHAR

    // Character types (DBCS/Graphic) - DB2-specific for double-byte character sets
    case class Graphic(length: Option[Int]) extends DB2Type
    case class VarGraphic(length: Option[Int]) extends DB2Type
    case object DbClob extends DB2Type
    case object LongVarGraphic extends DB2Type // Deprecated VARGRAPHIC

    // Binary types
    case class Binary(length: Option[Int]) extends DB2Type // CHAR FOR BIT DATA equivalent
    case class VarBinary(length: Option[Int]) extends DB2Type // VARCHAR FOR BIT DATA equivalent
    case object Blob extends DB2Type

    // Date/Time types
    case object Date extends DB2Type
    case object Time extends DB2Type
    case class Timestamp(precision: Option[Int]) extends DB2Type // 0-12 fractional seconds

    // XML type
    case object Xml extends DB2Type

    // Row ID types
    case object RowId extends DB2Type // DB2-specific: internal row identifier

    // Distinct types (user-defined type aliases, similar to PostgreSQL domains)
    case class DistinctType(name: RelationName, sourceType: Type) extends DB2Type
  }

  // Shared/unknown type - extends PgType, MariaType, and OracleType for pattern matching
  case class Unknown(sqlType: String) extends PgType with MariaType with DuckDbType with OracleType with SqlServerType with DB2Type

  case class Domain(name: RelationName, tpe: Type, originalType: String, isNotNull: Nullability, hasDefault: Boolean, constraintDefinition: Option[String])
  case class StringEnum(name: RelationName, values: NonEmptyList[String])
  case class ColName(value: String) extends AnyVal
  object ColName {
    implicit val ordering: Ordering[ColName] = Ordering.by(_.value)
  }

  case class Constraint(name: String, columns: SortedSet[ColName], checkClause: String)

  sealed trait Generated {
    def ALWAYS: Boolean
    def `BY DEFAULT`: Boolean
    def asString: String
  }

  object Generated {
    case class IsGenerated(generation: String, expression: Option[String]) extends Generated {
      override def ALWAYS: Boolean = generation == "ALWAYS"
      override def `BY DEFAULT`: Boolean = generation == "BY DEFAULT"

      override def asString: String =
        List(
          Some(s"Generated $generation"),
          expression.map("expression: " + _)
        ).flatten.mkString(", ")
    }

    case class Identity(
        identityGeneration: String,
        identityStart: Option[String],
        identityIncrement: Option[String],
        identityMaximum: Option[String],
        identityMinimum: Option[String]
    ) extends Generated {
      override def ALWAYS: Boolean = identityGeneration == "ALWAYS"
      override def `BY DEFAULT`: Boolean = identityGeneration == "BY DEFAULT"
      override def asString: String =
        List(
          Some(s"Identity $identityGeneration"),
          identityStart.map("identityStart: " + _),
          identityIncrement.map("identityIncrement: " + _),
          identityMaximum.map("identityMaximum: " + _),
          identityMinimum.map("identityMinimum: " + _)
        ).flatten.mkString(", ")
    }

    /** MariaDB/MySQL AUTO_INCREMENT columns */
    case object AutoIncrement extends Generated {
      override def ALWAYS: Boolean = true
      override def `BY DEFAULT`: Boolean = false
      override def asString: String = "AUTO_INCREMENT"
    }

    /** SQL Server IDENTITY(seed, increment) columns */
    case class SqlServerIdentity(seed: Long, increment: Long) extends Generated {
      override def ALWAYS: Boolean = true
      override def `BY DEFAULT`: Boolean = false
      override def asString: String = s"IDENTITY($seed, $increment)"
    }

    /** SQL Server ROWVERSION/TIMESTAMP columns - auto-generated on every insert/update */
    case object SqlServerRowVersion extends Generated {
      override def ALWAYS: Boolean = true
      override def `BY DEFAULT`: Boolean = false
      override def asString: String = "ROWVERSION"
    }
  }

  case class Col(
      parsedName: ParsedName,
      tpe: Type,
      udtName: Option[String],
      nullability: Nullability,
      columnDefault: Option[String],
      maybeGenerated: Option[Generated],
      comment: Option[String],
      constraints: List[Constraint],
      jsonDescription: DebugJson
  ) {
    def isDefaulted = columnDefault.nonEmpty || maybeGenerated.exists(_.`BY DEFAULT`)
    def name = parsedName.name
  }
  case class RelationName(schema: Option[String], name: String) {
    def value: String =
      schema.foldLeft(name)((acc, s) => s"$s.$acc")
    def quotedValue: String =
      schema.foldLeft(quote.double(name))((acc, s) => s"${quote.double(s)}.$acc")
  }
  object RelationName {
    implicit val ordering: Ordering[RelationName] = Ordering.by(_.value)
  }

  case class PrimaryKey(colNames: NonEmptyList[ColName], constraintName: RelationName)
  case class ForeignKey(cols: NonEmptyList[ColName], otherTable: RelationName, otherCols: NonEmptyList[ColName], constraintName: RelationName) {
    require(cols.length == otherCols.length)
  }
  case class UniqueKey(cols: NonEmptyList[ColName], constraintName: RelationName)

  sealed trait Relation {
    def name: RelationName
  }

  case class Table(
      name: RelationName,
      comment: Option[String],
      cols: NonEmptyList[Col],
      primaryKey: Option[PrimaryKey],
      uniqueKeys: List[UniqueKey],
      foreignKeys: List[ForeignKey]
  ) extends Relation

  case class View(
      name: RelationName,
      comment: Option[String],
      decomposedSql: DecomposedSql,
      cols: NonEmptyList[(db.Col, ParsedName)],
      deps: Map[db.ColName, List[(db.RelationName, db.ColName)]],
      isMaterialized: Boolean
  ) extends Relation
}
