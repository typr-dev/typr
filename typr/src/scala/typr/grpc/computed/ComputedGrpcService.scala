package typr.grpc.computed

import typr.grpc.{GrpcOptions, ProtoService, ProtoType}
import typr.grpc.codegen.{GrpcFramework, ProtobufTypeMapper}
import typr.{jvm, Lang, Naming}

/** Computed representation of a gRPC service for code generation.
  *
  * Separates computation (type mapping, naming) from file generation. The computation phase analyzes the proto definition and resolves all types, while the file generation phase focuses on code
  * structure.
  *
  * @param proto
  *   The original proto service definition
  * @param naming
  *   Naming utilities for type generation
  * @param typeMapper
  *   Type mapper for proto to JVM type conversion
  * @param serviceTpe
  *   The qualified type for the service interface
  * @param serverTpe
  *   The qualified type for the server adapter
  * @param clientTpe
  *   The qualified type for the client wrapper
  * @param methods
  *   Computed methods for this service
  * @param framework
  *   Optional framework integration (Spring/Quarkus)
  */
case class ComputedGrpcService(
    proto: ProtoService,
    naming: Naming,
    typeMapper: ProtobufTypeMapper,
    serviceTpe: jvm.Type.Qualified,
    serverTpe: jvm.Type.Qualified,
    clientTpe: jvm.Type.Qualified,
    methods: List[ComputedGrpcMethod],
    framework: Option[GrpcFramework]
) {

  /** The proto service name */
  def protoName: String = proto.name

  /** The fully qualified proto service name (package.ServiceName) */
  def fullName: String = proto.fullName
}

object ComputedGrpcService {

  /** Create a computed service from a proto definition */
  def from(
      proto: ProtoService,
      naming: Naming,
      lang: Lang,
      typeMapper: ProtobufTypeMapper,
      framework: Option[GrpcFramework]
  ): ComputedGrpcService = {
    val serviceTpe = naming.grpcServiceTypeName(proto.name)
    val serverTpe = naming.grpcServerTypeName(proto.name)
    val clientTpe = naming.grpcClientTypeName(proto.name)

    val methods = proto.methods.map { method =>
      val methodName = naming.grpcMethodName(method.name)
      val inputType = typeMapper.mapType(ProtoType.Message(method.inputType))
      val outputType = typeMapper.mapType(ProtoType.Message(method.outputType))
      val protoInputType = naming.grpcMessageTypeName(method.inputType)
      val protoOutputType = naming.grpcMessageTypeName(method.outputType)

      ComputedGrpcMethod(
        proto = method,
        name = methodName,
        inputType = inputType,
        outputType = outputType,
        protoInputType = protoInputType,
        protoOutputType = protoOutputType
      )
    }

    ComputedGrpcService(
      proto = proto,
      naming = naming,
      typeMapper = typeMapper,
      serviceTpe = serviceTpe,
      serverTpe = serverTpe,
      clientTpe = clientTpe,
      methods = methods,
      framework = framework
    )
  }
}
