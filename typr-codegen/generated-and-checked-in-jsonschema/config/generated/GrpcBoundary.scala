package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** gRPC/Protobuf boundary for service types */
case class GrpcBoundary(
    `type`: Option[String],
    /** Directory containing .proto files */
    proto_path: Option[String],
    /** Multiple proto directories */
    proto_paths: Option[List[String]],
    /** Additional include paths for proto imports */
    include_paths: Option[List[String]],
    /** Path to pre-built FileDescriptorSet file */
    descriptor_set: Option[String],
    /** Generate message classes (clean JVM types) */
    generate_messages: Option[Boolean],
    /** Generate service interfaces */
    generate_services: Option[Boolean],
    /** Generate server adapters (extends grpc-java *ImplBase) */
    generate_servers: Option[Boolean],
    /** Generate client wrappers (wraps grpc-java stubs) */
    generate_clients: Option[Boolean],
    /** Boundary-level type definitions */
    types: Option[Map[String, FieldType]]
)

object GrpcBoundary {
  implicit val decoder: Decoder[GrpcBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.GrpcBoundary]

  implicit val encoder: Encoder[GrpcBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.GrpcBoundary]
}
