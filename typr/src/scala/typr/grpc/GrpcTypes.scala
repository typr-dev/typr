package typr.grpc

/** Internal representation of parsed Protobuf schemas.
  *
  * These types represent the Protobuf schema structure after parsing from .proto files via protoc descriptor sets. They abstract over the protobuf-java library's descriptor classes to provide a
  * cleaner API for code generation.
  */

/** A complete .proto file, potentially containing multiple types and services */
case class ProtoFile(
    /** Package name from the .proto file */
    protoPackage: Option[String],
    /** Java package from option java_package, if set */
    javaPackage: Option[String],
    /** All top-level message types defined in this file */
    messages: List[ProtoMessage],
    /** All top-level enum types defined in this file */
    enums: List[ProtoEnum],
    /** All services defined in this file */
    services: List[ProtoService],
    /** Source file path (if loaded from file) */
    sourcePath: Option[String],
    /** Syntax version (proto2 or proto3) */
    syntax: ProtoSyntax
)

/** Protobuf syntax version */
sealed trait ProtoSyntax

object ProtoSyntax {
  case object Proto2 extends ProtoSyntax
  case object Proto3 extends ProtoSyntax
}

/** A protobuf message type */
case class ProtoMessage(
    /** Simple name of the message (e.g., "CustomerOrder") */
    name: String,
    /** Full qualified name including package (e.g., "com.example.CustomerOrder") */
    fullName: String,
    /** Fields in this message */
    fields: List[ProtoField],
    /** Nested message types defined inside this message */
    nestedMessages: List[ProtoMessage],
    /** Nested enum types defined inside this message */
    nestedEnums: List[ProtoEnum],
    /** Oneof groups defined in this message */
    oneofs: List[ProtoOneof],
    /** Whether this is a map entry type (auto-generated for map fields) */
    isMapEntry: Boolean
)

/** A field within a protobuf message */
case class ProtoField(
    /** Field name as defined in .proto */
    name: String,
    /** Field number */
    number: Int,
    /** Field type */
    fieldType: ProtoType,
    /** Field label (optional, repeated, required, or implicit for proto3) */
    label: ProtoFieldLabel,
    /** Wrapper type name from (typr.wrapper) custom option */
    wrapperType: Option[String],
    /** Default value (proto2 only, JSON-encoded) */
    defaultValue: Option[String],
    /** Whether this field is part of a oneof */
    oneofIndex: Option[Int],
    /** Whether this field uses proto3 optional keyword */
    proto3Optional: Boolean
) {

  /** Whether this field is optional (proto3 optional, or nullable wrapper type) */
  def isOptional: Boolean = proto3Optional

  /** Whether this field is repeated (list or map) */
  def isRepeated: Boolean = label == ProtoFieldLabel.Repeated && !isMapField

  /** Whether this field represents a map */
  def isMapField: Boolean = fieldType match {
    case ProtoType.Map(_, _) => true
    case _                   => false
  }
}

/** Field label in protobuf */
sealed trait ProtoFieldLabel

object ProtoFieldLabel {
  case object Optional extends ProtoFieldLabel
  case object Required extends ProtoFieldLabel
  case object Repeated extends ProtoFieldLabel
}

/** A oneof group within a message */
case class ProtoOneof(
    /** Name of the oneof group */
    name: String,
    /** Fields that are part of this oneof */
    fields: List[ProtoField]
)

/** A protobuf enum type */
case class ProtoEnum(
    /** Simple name of the enum */
    name: String,
    /** Full qualified name including package */
    fullName: String,
    /** Enum values */
    values: List[ProtoEnumValue],
    /** Whether the enum allows aliases (allow_alias option) */
    allowAlias: Boolean
)

/** A single enum value */
case class ProtoEnumValue(
    /** Name of the enum value (e.g., "ORDER_STATUS_PENDING") */
    name: String,
    /** Numeric value */
    number: Int
)

/** A protobuf service definition */
case class ProtoService(
    /** Simple name of the service */
    name: String,
    /** Full qualified name including package */
    fullName: String,
    /** RPC methods defined in this service */
    methods: List[ProtoMethod]
)

/** An RPC method in a protobuf service */
case class ProtoMethod(
    /** Method name (e.g., "GetCustomer") */
    name: String,
    /** Input message type (full name) */
    inputType: String,
    /** Output message type (full name) */
    outputType: String,
    /** Whether client sends a stream */
    clientStreaming: Boolean,
    /** Whether server sends a stream */
    serverStreaming: Boolean
) {

  /** RPC pattern based on streaming flags */
  def rpcPattern: RpcPattern = (clientStreaming, serverStreaming) match {
    case (false, false) => RpcPattern.Unary
    case (false, true)  => RpcPattern.ServerStreaming
    case (true, false)  => RpcPattern.ClientStreaming
    case (true, true)   => RpcPattern.BidiStreaming
  }
}

/** The four gRPC RPC patterns */
sealed trait RpcPattern

object RpcPattern {
  case object Unary extends RpcPattern
  case object ServerStreaming extends RpcPattern
  case object ClientStreaming extends RpcPattern
  case object BidiStreaming extends RpcPattern
}

/** Protobuf type (field types, etc.) */
sealed trait ProtoType

object ProtoType {

  // Scalar types
  case object Double extends ProtoType
  case object Float extends ProtoType
  case object Int32 extends ProtoType
  case object Int64 extends ProtoType
  case object UInt32 extends ProtoType
  case object UInt64 extends ProtoType
  case object SInt32 extends ProtoType
  case object SInt64 extends ProtoType
  case object Fixed32 extends ProtoType
  case object Fixed64 extends ProtoType
  case object SFixed32 extends ProtoType
  case object SFixed64 extends ProtoType
  case object Bool extends ProtoType
  case object String extends ProtoType
  case object Bytes extends ProtoType

  // Complex types

  /** Reference to a named message type */
  case class Message(fullName: String) extends ProtoType

  /** Reference to a named enum type */
  case class Enum(fullName: String) extends ProtoType

  /** Map type (auto-generated from map<K,V> syntax) */
  case class Map(keyType: ProtoType, valueType: ProtoType) extends ProtoType

  // Well-known types (google.protobuf.*)

  /** google.protobuf.Timestamp -> java.time.Instant */
  case object Timestamp extends ProtoType

  /** google.protobuf.Duration -> java.time.Duration */
  case object Duration extends ProtoType

  /** google.protobuf.StringValue -> optional String */
  case object StringValue extends ProtoType

  /** google.protobuf.Int32Value -> optional Int */
  case object Int32Value extends ProtoType

  /** google.protobuf.Int64Value -> optional Long */
  case object Int64Value extends ProtoType

  /** google.protobuf.UInt32Value -> optional Int */
  case object UInt32Value extends ProtoType

  /** google.protobuf.UInt64Value -> optional Long */
  case object UInt64Value extends ProtoType

  /** google.protobuf.FloatValue -> optional Float */
  case object FloatValue extends ProtoType

  /** google.protobuf.DoubleValue -> optional Double */
  case object DoubleValue extends ProtoType

  /** google.protobuf.BoolValue -> optional Boolean */
  case object BoolValue extends ProtoType

  /** google.protobuf.BytesValue -> optional ByteString */
  case object BytesValue extends ProtoType

  /** google.protobuf.Any -> pass-through */
  case object Any extends ProtoType

  /** google.protobuf.Struct -> Map<String, Object> */
  case object Struct extends ProtoType

  /** google.protobuf.Empty -> Void */
  case object Empty extends ProtoType

  /** Check if a type is a well-known wrapper type */
  def isWrapperType(tpe: ProtoType): Boolean = tpe match {
    case StringValue | Int32Value | Int64Value | UInt32Value | UInt64Value | FloatValue | DoubleValue | BoolValue | BytesValue =>
      true
    case _ => false
  }

  /** Get the underlying scalar type for a well-known wrapper type */
  def unwrapWellKnown(tpe: ProtoType): Option[ProtoType] = tpe match {
    case StringValue => Some(String)
    case Int32Value  => Some(Int32)
    case Int64Value  => Some(Int64)
    case UInt32Value => Some(UInt32)
    case UInt64Value => Some(UInt64)
    case FloatValue  => Some(Float)
    case DoubleValue => Some(Double)
    case BoolValue   => Some(Bool)
    case BytesValue  => Some(Bytes)
    case _           => None
  }

  /** Well-known type full names for detection during parsing */
  val wellKnownTypes: scala.collection.immutable.Map[scala.Predef.String, ProtoType] = scala.collection.immutable.Map(
    "google.protobuf.Timestamp" -> Timestamp,
    "google.protobuf.Duration" -> Duration,
    "google.protobuf.StringValue" -> StringValue,
    "google.protobuf.Int32Value" -> Int32Value,
    "google.protobuf.Int64Value" -> Int64Value,
    "google.protobuf.UInt32Value" -> UInt32Value,
    "google.protobuf.UInt64Value" -> UInt64Value,
    "google.protobuf.FloatValue" -> FloatValue,
    "google.protobuf.DoubleValue" -> DoubleValue,
    "google.protobuf.BoolValue" -> BoolValue,
    "google.protobuf.BytesValue" -> BytesValue,
    "google.protobuf.Any" -> Any,
    "google.protobuf.Struct" -> Struct,
    "google.protobuf.Empty" -> Empty
  )
}
