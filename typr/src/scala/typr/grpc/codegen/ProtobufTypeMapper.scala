package typr.grpc.codegen

import typr.grpc._
import typr.{jvm, Lang, Naming, TypesJava}

/** Maps Protobuf types to JVM types.
  *
  * @param lang
  *   Target language
  * @param naming
  *   Naming conventions
  * @param wrapperTypeMap
  *   Map from wrapperName to generated wrapper types
  */
class ProtobufTypeMapper(
    lang: Lang,
    naming: Naming,
    wrapperTypeMap: Map[String, jvm.Type.Qualified]
) {

  private val ByteStringType = jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.ByteString"))
  private val AnyType = jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.Any"))

  def mapType(protoType: ProtoType): jvm.Type = protoType match {
    // Scalar types
    case ProtoType.Double   => lang.Double
    case ProtoType.Float    => lang.Float
    case ProtoType.Int32    => lang.Int
    case ProtoType.Int64    => lang.Long
    case ProtoType.UInt32   => lang.Int
    case ProtoType.UInt64   => lang.Long
    case ProtoType.SInt32   => lang.Int
    case ProtoType.SInt64   => lang.Long
    case ProtoType.Fixed32  => lang.Int
    case ProtoType.Fixed64  => lang.Long
    case ProtoType.SFixed32 => lang.Int
    case ProtoType.SFixed64 => lang.Long
    case ProtoType.Bool     => lang.Boolean
    case ProtoType.String   => lang.String
    case ProtoType.Bytes    => ByteStringType

    // Well-known types
    case ProtoType.Timestamp => TypesJava.Instant
    case ProtoType.Duration  => TypesJava.Duration

    // Well-known wrapper types -> Optional of the underlying scalar
    case ProtoType.StringValue => lang.Optional.tpe(lang.String)
    case ProtoType.Int32Value  => lang.Optional.tpe(lang.Int)
    case ProtoType.Int64Value  => lang.Optional.tpe(lang.Long)
    case ProtoType.UInt32Value => lang.Optional.tpe(lang.Int)
    case ProtoType.UInt64Value => lang.Optional.tpe(lang.Long)
    case ProtoType.FloatValue  => lang.Optional.tpe(lang.Float)
    case ProtoType.DoubleValue => lang.Optional.tpe(lang.Double)
    case ProtoType.BoolValue   => lang.Optional.tpe(lang.Boolean)
    case ProtoType.BytesValue  => lang.Optional.tpe(ByteStringType)

    // Any -> pass-through
    case ProtoType.Any => AnyType

    // Struct -> Map<String, Object>
    case ProtoType.Struct => lang.MapOps.tpe.of(lang.String, lang.topType)

    // Empty -> Void
    case ProtoType.Empty => jvm.Type.Void

    // Message reference -> generated type
    case ProtoType.Message(fullName) =>
      naming.grpcMessageTypeName(fullName)

    // Enum reference -> generated type
    case ProtoType.Enum(fullName) =>
      naming.grpcEnumTypeName(fullName)

    // Map<K, V>
    case ProtoType.Map(keyType, valueType) =>
      lang.MapOps.tpe.of(mapType(keyType), mapType(valueType))
  }

  /** Map a field type, respecting wrapper types and optional fields */
  def mapFieldType(field: ProtoField): jvm.Type = {
    field.wrapperType match {
      case Some(wrapperName) =>
        val wrapperType = wrapperTypeMap.getOrElse(
          wrapperName,
          sys.error(s"Wrapper type $wrapperName not found")
        )
        if (field.proto3Optional) {
          lang.Optional.tpe(wrapperType)
        } else {
          wrapperType
        }
      case None =>
        val baseType = mapType(field.fieldType)
        if (field.isRepeated) {
          lang.ListType.tpe.of(baseType)
        } else if (field.proto3Optional) {
          lang.Optional.tpe(baseType)
        } else {
          baseType
        }
    }
  }
}
