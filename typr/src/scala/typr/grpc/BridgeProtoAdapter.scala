package typr.grpc

import typr.grpc.codegen.ProtobufTypeMapper
import typr.internal.codegen.FileBridgeProjectionMapper.{ExternalField, ExternalRecord}
import typr.Naming

/** Adapts Protobuf types to the generic ExternalRecord format used by Bridge mappers. */
object BridgeProtoAdapter {

  /** Convert a ProtoMessage to ExternalRecord for Bridge mapper generation.
    *
    * @param message
    *   The proto message definition
    * @param naming
    *   Naming utilities for type generation
    * @param typeMapper
    *   Type mapper for proto to JVM type conversion
    * @return
    *   An ExternalRecord with the same fields
    */
  def toExternalRecord(
      message: ProtoMessage,
      naming: Naming,
      typeMapper: ProtobufTypeMapper
  ): ExternalRecord = {
    val tpe = naming.grpcMessageTypeName(message.fullName)

    val fields = message.fields.filter(_.oneofIndex.isEmpty).map { field =>
      val jvmType = typeMapper.mapFieldType(field)
      ExternalField(
        name = naming.grpcFieldName(field.name),
        protoName = field.name,
        jvmType = jvmType,
        isNullable = field.proto3Optional || field.label == ProtoFieldLabel.Optional
      )
    }

    ExternalRecord(
      tpe = tpe,
      fields = fields
    )
  }

  /** Create a lookup function from proto files for Bridge mapper generation.
    *
    * The lookup matches by full name (package.MessageName) or simple name.
    *
    * @param protoFiles
    *   List of parsed proto files
    * @param naming
    *   Naming utilities for type generation
    * @param typeMapper
    *   Type mapper for proto to JVM type conversion
    * @return
    *   A lookup function that finds records by entity path
    */
  def createLookup(
      protoFiles: List[ProtoFile],
      naming: Naming,
      typeMapper: ProtobufTypeMapper
  ): String => Option[ExternalRecord] = {
    val allMessages = protoFiles.flatMap(f => flattenMessages(f.messages))

    val byFullName = allMessages.map(m => m.fullName -> toExternalRecord(m, naming, typeMapper)).toMap
    val bySimpleName = allMessages.map(m => m.name -> toExternalRecord(m, naming, typeMapper)).toMap

    entityPath => byFullName.get(entityPath).orElse(bySimpleName.get(entityPath))
  }

  /** Flatten all messages including nested ones */
  private def flattenMessages(messages: List[ProtoMessage]): List[ProtoMessage] = {
    messages.flatMap { msg =>
      msg :: flattenMessages(msg.nestedMessages)
    }
  }
}
