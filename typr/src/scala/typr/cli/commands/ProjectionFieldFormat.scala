package typr.cli.commands

import typr.avro.AvroType
import typr.grpc.ProtoFieldLabel
import typr.grpc.ProtoFile
import typr.grpc.ProtoMessage
import typr.grpc.ProtoType

/** Utility formatters for Avro and Proto types, used when describing source entities to the user.
  */
object ProjectionFieldFormat {

  def flattenMessages(file: ProtoFile): List[ProtoMessage] = {
    def flatten(messages: List[ProtoMessage]): List[ProtoMessage] =
      messages.flatMap(msg => msg :: flatten(msg.nestedMessages))
    flatten(file.messages)
  }

  def formatProtoType(tpe: ProtoType, label: ProtoFieldLabel, proto3Optional: Boolean): String = {
    val baseType = tpe match {
      case ProtoType.Double   => "double"
      case ProtoType.Float    => "float"
      case ProtoType.Int32    => "int32"
      case ProtoType.Int64    => "int64"
      case ProtoType.UInt32   => "uint32"
      case ProtoType.UInt64   => "uint64"
      case ProtoType.SInt32   => "sint32"
      case ProtoType.SInt64   => "sint64"
      case ProtoType.Fixed32  => "fixed32"
      case ProtoType.Fixed64  => "fixed64"
      case ProtoType.SFixed32 => "sfixed32"
      case ProtoType.SFixed64 => "sfixed64"
      case ProtoType.Bool     => "bool"
      case ProtoType.String   => "string"
      case ProtoType.Bytes    => "bytes"

      case ProtoType.Message(fullName) => fullName.split("\\.").last
      case ProtoType.Enum(fullName)    => fullName.split("\\.").last

      case ProtoType.Map(keyType, valueType) =>
        s"map<${formatProtoType(keyType, ProtoFieldLabel.Optional, proto3Optional = false)}, ${formatProtoType(valueType, ProtoFieldLabel.Optional, proto3Optional = false)}>"

      case ProtoType.Timestamp   => "Timestamp"
      case ProtoType.Duration    => "Duration"
      case ProtoType.StringValue => "StringValue"
      case ProtoType.Int32Value  => "Int32Value"
      case ProtoType.Int64Value  => "Int64Value"
      case ProtoType.UInt32Value => "UInt32Value"
      case ProtoType.UInt64Value => "UInt64Value"
      case ProtoType.FloatValue  => "FloatValue"
      case ProtoType.DoubleValue => "DoubleValue"
      case ProtoType.BoolValue   => "BoolValue"
      case ProtoType.BytesValue  => "BytesValue"
      case ProtoType.Any         => "Any"
      case ProtoType.Struct      => "Struct"
      case ProtoType.Empty       => "Empty"
    }

    if (label == ProtoFieldLabel.Repeated && !tpe.isInstanceOf[ProtoType.Map]) {
      s"[$baseType]"
    } else if (proto3Optional) {
      s"$baseType?"
    } else {
      baseType
    }
  }

  def formatAvroType(tpe: AvroType): String = tpe match {
    case AvroType.Null    => "null"
    case AvroType.Boolean => "boolean"
    case AvroType.Int     => "int"
    case AvroType.Long    => "long"
    case AvroType.Float   => "float"
    case AvroType.Double  => "double"
    case AvroType.Bytes   => "bytes"
    case AvroType.String  => "string"

    case AvroType.Array(items) => s"[${formatAvroType(items)}]"
    case AvroType.Map(values)  => s"map<${formatAvroType(values)}>"

    case AvroType.Union(members) =>
      val nonNull = members.filterNot(_ == AvroType.Null)
      if (nonNull.length == 1 && members.contains(AvroType.Null)) {
        s"${formatAvroType(nonNull.head)}?"
      } else {
        members.map(formatAvroType).mkString(" | ")
      }

    case AvroType.Named(fullName) => fullName.split("\\.").last

    case AvroType.Record(r)   => r.name
    case AvroType.EnumType(e) => e.name
    case AvroType.Fixed(f)    => f.name

    case AvroType.UUID                  => "uuid"
    case AvroType.Date                  => "date"
    case AvroType.TimeMillis            => "time-millis"
    case AvroType.TimeMicros            => "time-micros"
    case AvroType.TimeNanos             => "time-nanos"
    case AvroType.TimestampMillis       => "timestamp-millis"
    case AvroType.TimestampMicros       => "timestamp-micros"
    case AvroType.TimestampNanos        => "timestamp-nanos"
    case AvroType.LocalTimestampMillis  => "local-timestamp-millis"
    case AvroType.LocalTimestampMicros  => "local-timestamp-micros"
    case AvroType.LocalTimestampNanos   => "local-timestamp-nanos"
    case AvroType.DecimalBytes(p, s)    => s"decimal($p,$s)"
    case AvroType.DecimalFixed(p, s, _) => s"decimal($p,$s)"
    case AvroType.Duration              => "duration"
  }
}
