package typr.grpc.codegen

import typr.jvm

/** Framework integration for generating gRPC server/client with framework-specific annotations.
  *
  * Implementations provide framework-specific types and annotations for Spring gRPC and Quarkus gRPC.
  */
trait GrpcFramework {

  /** Server service class annotation (e.g., @GrpcService, @Singleton) */
  def serverAnnotations: List[jvm.Annotation]

  /** Client injection annotation */
  def clientFieldAnnotations(serviceName: String): List[jvm.Annotation]

  /** Constructor injection annotations */
  def constructorAnnotations: List[jvm.Annotation]
}
