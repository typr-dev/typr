package typo

import typo.internal.{DebugJson, quote}
import typo.internal.analysis.{DecomposedSql, ParsedName}

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

  // Shared/unknown type - extends both PgType and MariaType so it can be used in pattern matching for either
  case class Unknown(sqlType: String) extends PgType with MariaType

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
