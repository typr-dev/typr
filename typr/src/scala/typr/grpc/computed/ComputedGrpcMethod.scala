package typr.grpc.computed

import typr.grpc.{ProtoMethod, RpcPattern}
import typr.jvm

/** Computed representation of a gRPC method for code generation.
  *
  * Separates computation (type mapping, method naming) from file generation.
  *
  * @param proto
  *   The original proto method definition
  * @param name
  *   The computed JVM method name (e.g., "getUser")
  * @param inputType
  *   The mapped JVM input type
  * @param outputType
  *   The mapped JVM output type
  * @param protoInputType
  *   The JVM type for the protobuf message (for marshalling)
  * @param protoOutputType
  *   The JVM type for the protobuf response message (for marshalling)
  */
case class ComputedGrpcMethod(
    proto: ProtoMethod,
    name: jvm.Ident,
    inputType: jvm.Type,
    outputType: jvm.Type,
    protoInputType: jvm.Type.Qualified,
    protoOutputType: jvm.Type.Qualified
) {

  /** The RPC pattern (unary, server streaming, etc.) */
  def rpcPattern: RpcPattern = proto.rpcPattern

  /** The original proto method name */
  def protoName: String = proto.name

  /** Full method name for gRPC: "package.ServiceName/MethodName" */
  def fullMethodName(serviceFullName: String): String =
    s"$serviceFullName/${proto.name}"

  /** Constant field name for the MethodDescriptor (e.g., "GET_USER") */
  def descriptorFieldName: String = {
    val name = proto.name
    val sb = new StringBuilder
    name.foreach { c =>
      if (c.isUpper && sb.nonEmpty) sb.append('_')
      sb.append(c.toUpper)
    }
    sb.toString()
  }

  /** Whether this is a streaming RPC (any direction) */
  def isStreaming: Boolean = rpcPattern != RpcPattern.Unary
}
