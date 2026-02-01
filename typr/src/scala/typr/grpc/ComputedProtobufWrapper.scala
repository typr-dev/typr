package typr.grpc

import typr.jvm

/** Computed wrapper type extracted from (typr.wrapper) annotations on Protobuf fields.
  *
  * This follows the Computed* pattern used by database codegen (ComputedDomain, etc.) and Avro codegen (ComputedAvroWrapper).
  */
case class ComputedProtobufWrapper(
    /** Qualified wrapper type name (e.g., com.example.CustomerId) */
    tpe: jvm.Type.Qualified,
    /** Underlying JVM type (e.g., Long, String, java.util.UUID) */
    underlyingJvmType: jvm.Type,
    /** Underlying Proto type (for converter logic) */
    underlyingProtoType: ProtoType
)

object ComputedProtobufWrapper {

  /** Collect all unique wrapper types from messages.
    *
    * Scans all fields with (typr.wrapper) custom option and builds ComputedProtobufWrapper instances.
    *
    * @param messages
    *   All ProtoMessages to scan (should include nested messages flattened)
    * @param typeMapper
    *   Maps Proto types to JVM types
    * @param naming
    *   Naming conventions for generating qualified type names
    * @return
    *   List of unique wrapper types to generate
    */
  def collect(
      messages: List[ProtoMessage],
      typeMapper: typr.grpc.codegen.ProtobufTypeMapper,
      naming: typr.Naming
  ): List[ComputedProtobufWrapper] = {
    messages
      .flatMap { message =>
        message.fields.flatMap { field =>
          field.wrapperType.map { wrapperName =>
            val underlyingProtoType = unwrapOptional(field)
            val underlyingJvmType = typeMapper.mapType(underlyingProtoType)
            val tpe = naming.grpcWrapperTypeName(wrapperName)
            ComputedProtobufWrapper(
              tpe = tpe,
              underlyingJvmType = underlyingJvmType,
              underlyingProtoType = underlyingProtoType
            )
          }
        }
      }
      .distinctBy(_.tpe.value.idents.map(_.value).mkString("."))
  }

  /** Get the base type of a field, unwrapping optional if present */
  private def unwrapOptional(field: ProtoField): ProtoType = {
    if (field.proto3Optional) {
      field.fieldType
    } else {
      ProtoType.unwrapWellKnown(field.fieldType).getOrElse(field.fieldType)
    }
  }
}
