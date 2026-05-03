package typr.boundaries.framework

import typr.jvm

/** RPC protocol framework extension for gRPC code generation.
  *
  * Provides gRPC-specific code generation patterns on top of the base Framework trait for gRPC services.
  */
trait RpcFramework extends Framework {

  /** Annotations for gRPC server implementations (e.g., @GrpcService) */
  def serverAnnotations: List[jvm.Annotation]

  /** Annotations to inject a gRPC client (e.g., @GrpcClient) */
  def clientInjectionAnnotations(serviceName: String): List[jvm.Annotation]

  /** The blocking stub type for a service */
  def blockingStubType(serviceName: String): jvm.Type

  /** The async stub type for a service */
  def asyncStubType(serviceName: String): jvm.Type
}
