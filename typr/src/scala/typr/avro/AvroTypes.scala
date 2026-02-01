package typr.avro

/** Internal representation of parsed Avro schemas.
  *
  * These types represent the Avro schema structure after parsing from .avsc files or Schema Registry. They abstract over the Apache Avro library's Schema class to provide a cleaner API for code
  * generation.
  */

/** Indicates whether a schema is for a key or value in Kafka topics */
sealed trait SchemaRole

object SchemaRole {

  /** Schema is for the topic value (default) */
  case object Value extends SchemaRole

  /** Schema is for the topic key */
  case object Key extends SchemaRole
}

/** A complete Avro schema file, potentially containing multiple types */
case class AvroSchemaFile(
    /** Primary schema (the root record/enum) */
    primarySchema: AvroSchema,
    /** Additional schemas defined inline (nested records, enums) */
    inlineSchemas: List[AvroSchema],
    /** Source file path (if loaded from file) */
    sourcePath: Option[String],
    /** Directory group name (for directory-based sum types). If a schema is in a subdirectory, this contains the directory name (e.g., "order-events" for schemas/order-events/OrderPlaced.avsc).
      * Schemas in the root directory have None.
      */
    directoryGroup: Option[String],
    /** Role of this schema (key or value). Default is Value. */
    schemaRole: SchemaRole,
    /** Schema version from Schema Registry (if fetched with version info) */
    version: Option[Int]
)

/** Base trait for all Avro schema types */
sealed trait AvroSchema {
  def name: String
  def namespace: Option[String]
  def doc: Option[String]

  /** Full qualified name including namespace */
  def fullName: String = namespace.map(ns => s"$ns.$name").getOrElse(name)
}

/** Avro record type - the primary complex type */
case class AvroRecord(
    name: String,
    namespace: Option[String],
    doc: Option[String],
    fields: List[AvroField],
    /** Aliases for this record (for schema evolution) */
    aliases: List[String]
) extends AvroSchema

/** Avro enum type */
case class AvroEnum(
    name: String,
    namespace: Option[String],
    doc: Option[String],
    symbols: List[String],
    /** Default value (for schema evolution) */
    defaultSymbol: Option[String],
    /** Aliases for this enum */
    aliases: List[String]
) extends AvroSchema

/** Avro fixed type (fixed-length byte array) */
case class AvroFixed(
    name: String,
    namespace: Option[String],
    doc: Option[String],
    size: Int,
    /** Aliases for this fixed type */
    aliases: List[String]
) extends AvroSchema

/** A field within an Avro record */
case class AvroField(
    name: String,
    doc: Option[String],
    fieldType: AvroType,
    /** Default value (JSON-encoded) */
    defaultValue: Option[String],
    /** Field ordering hint for sorting */
    order: FieldOrder,
    /** Aliases for this field (for schema evolution) */
    aliases: List[String],
    /** Wrapper type name from x-typr-wrapper attribute (e.g., "CustomerId", "Email") */
    wrapperType: Option[String]
) {

  /** Whether this field is optional (nullable with null as first union member or has default) */
  def isOptional: Boolean = fieldType match {
    case AvroType.Union(members) => members.headOption.contains(AvroType.Null)
    case _                       => defaultValue.isDefined
  }

  /** Whether this field is required (not nullable and no default) */
  def isRequired: Boolean = !isOptional && defaultValue.isEmpty
}

/** Field ordering for binary comparison */
sealed trait FieldOrder

object FieldOrder {
  case object Ascending extends FieldOrder
  case object Descending extends FieldOrder
  case object Ignore extends FieldOrder
}

/** Avro type (field types, array elements, map values, etc.) */
sealed trait AvroType

object AvroType {

  // Primitive types
  case object Null extends AvroType
  case object Boolean extends AvroType
  case object Int extends AvroType
  case object Long extends AvroType
  case object Float extends AvroType
  case object Double extends AvroType
  case object Bytes extends AvroType
  case object String extends AvroType

  // Complex types
  case class Array(items: AvroType) extends AvroType
  case class Map(values: AvroType) extends AvroType
  case class Union(members: List[AvroType]) extends AvroType

  /** Reference to a named type (record, enum, fixed) */
  case class Named(fullName: String) extends AvroType

  /** Inline record definition */
  case class Record(record: AvroRecord) extends AvroType

  /** Inline enum definition */
  case class EnumType(avroEnum: AvroEnum) extends AvroType

  /** Inline fixed definition */
  case class Fixed(fixed: AvroFixed) extends AvroType

  // Logical types (represented as primitives with additional semantics)
  sealed trait LogicalType extends AvroType {
    def underlyingType: AvroType
  }

  /** UUID represented as string */
  case object UUID extends LogicalType {
    def underlyingType: AvroType = String
  }

  /** Date (days since epoch) represented as int */
  case object Date extends LogicalType {
    def underlyingType: AvroType = Int
  }

  /** Time in milliseconds represented as int */
  case object TimeMillis extends LogicalType {
    def underlyingType: AvroType = Int
  }

  /** Time in microseconds represented as long */
  case object TimeMicros extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Timestamp in milliseconds represented as long */
  case object TimestampMillis extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Timestamp in microseconds represented as long */
  case object TimestampMicros extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Local timestamp in milliseconds (no timezone) represented as long */
  case object LocalTimestampMillis extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Local timestamp in microseconds (no timezone) represented as long */
  case object LocalTimestampMicros extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Time in nanoseconds represented as long (Avro 1.11+) */
  case object TimeNanos extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Timestamp in nanoseconds represented as long (Avro 1.11+) */
  case object TimestampNanos extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Local timestamp in nanoseconds (no timezone) represented as long (Avro 1.11+) */
  case object LocalTimestampNanos extends LogicalType {
    def underlyingType: AvroType = Long
  }

  /** Decimal with precision and scale, represented as bytes */
  case class DecimalBytes(precision: Int, scale: Int) extends LogicalType {
    def underlyingType: AvroType = Bytes
  }

  /** Decimal with precision and scale, represented as fixed */
  case class DecimalFixed(precision: Int, scale: Int, fixedSize: Int) extends LogicalType {
    def underlyingType: AvroType = Bytes
  }

  /** Duration (12-byte fixed) - months, days, milliseconds */
  case object Duration extends LogicalType {
    def underlyingType: AvroType = Bytes
  }

  /** Helper to unwrap nullable types (unions with null) */
  def unwrapNullable(tpe: AvroType): Option[AvroType] = tpe match {
    case Union(List(Null, inner)) => Some(inner)
    case Union(List(inner, Null)) => Some(inner)
    case _                        => None
  }

  /** Check if a type is nullable */
  def isNullable(tpe: AvroType): Boolean = tpe match {
    case Union(members) => members.contains(Null)
    case _              => false
  }

  /** Get non-null members of a union */
  def nonNullMembers(tpe: AvroType): List[AvroType] = tpe match {
    case Union(members) => members.filterNot(_ == Null)
    case other          => List(other)
  }

  /** Check if a union is "complex" (multiple non-null members) */
  def isComplexUnion(union: Union): Boolean = {
    val nonNull = union.members.filterNot(_ == Null)
    nonNull.size >= 2
  }

  /** Check if any type contains a complex union (recursive) */
  def containsComplexUnion(tpe: AvroType): Boolean = tpe match {
    case u: Union if isComplexUnion(u) => true
    case Union(members)                => members.exists(containsComplexUnion)
    case Array(items)                  => containsComplexUnion(items)
    case Map(values)                   => containsComplexUnion(values)
    case _                             => false
  }

  /** Extract all complex unions from a type (recursive) */
  def extractComplexUnions(tpe: AvroType): Set[Union] = tpe match {
    case u: Union if isComplexUnion(u) =>
      Set(u) ++ u.members.flatMap(extractComplexUnions)
    case Union(members) =>
      members.flatMap(extractComplexUnions).toSet
    case Array(items) =>
      extractComplexUnions(items)
    case Map(values) =>
      extractComplexUnions(values)
    case _ =>
      Set.empty
  }
}

/** Metadata about a schema from Schema Registry */
case class RegistryMetadata(
    subject: String,
    version: Int,
    id: Int
)

/** An event group representing a sum type / sealed hierarchy.
  *
  * Used for topics that contain multiple event types (e.g., OrderPlaced, OrderUpdated, OrderCancelled all on "order-events" topic).
  *
  * Generates: - A sealed trait/interface that all member records extend - A dispatcher `fromGenericRecord` that routes to the correct subtype based on schema name
  */
case class AvroEventGroup(
    /** Name of the event group (e.g., "OrderEvent") */
    name: String,
    /** Namespace for the sealed type */
    namespace: Option[String],
    /** Documentation for the event group */
    doc: Option[String],
    /** Member records that belong to this group */
    members: List[AvroRecord]
) {

  /** Full qualified name including namespace */
  def fullName: String = namespace.map(ns => s"$ns.$name").getOrElse(name)
}

/** Avro protocol definition (.avpr files).
  *
  * Protocols define RPC interfaces with typed messages, request/response schemas, and error handling.
  */
case class AvroProtocol(
    /** Name of the protocol */
    name: String,
    /** Namespace for the protocol */
    namespace: Option[String],
    /** Documentation for the protocol */
    doc: Option[String],
    /** Named types defined in this protocol (records, enums, errors) */
    types: List[AvroSchema],
    /** RPC messages defined in this protocol */
    messages: List[AvroMessage]
) {

  /** Full qualified name including namespace */
  def fullName: String = namespace.map(ns => s"$ns.$name").getOrElse(name)
}

/** An RPC message in an Avro protocol.
  *
  * Messages define request parameters, response type, and possible errors.
  */
case class AvroMessage(
    /** Name of the message (method name) */
    name: String,
    /** Documentation for the message */
    doc: Option[String],
    /** Request parameters (like record fields) */
    request: List[AvroField],
    /** Response type (use Null for void) */
    response: AvroType,
    /** Error types that this message can throw */
    errors: List[AvroType],
    /** Whether this is a one-way message (fire-and-forget, no response) */
    oneWay: Boolean
)

/** Avro error type - like a record but represents an exception.
  *
  * Errors are transmitted as part of the error union in RPC responses.
  */
case class AvroError(
    name: String,
    namespace: Option[String],
    doc: Option[String],
    fields: List[AvroField],
    aliases: List[String]
) extends AvroSchema
