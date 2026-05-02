package typr.grpc.codegen

import typr.boundaries.framework.{FrameworkTypes, QuarkusFramework}
import typr.internal.codegen._
import typr.jvm

/** Quarkus gRPC framework integration.
  *
  * Server: @GrpcService + @Singleton annotations Client: @GrpcClient("service-name") field injection
  */
object GrpcFrameworkQuarkus extends GrpcFramework {

  // Quarkus gRPC annotations
  private val GrpcServiceAnnotation: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.quarkus.grpc.GrpcService"))
  private val GrpcClient: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.quarkus.grpc.GrpcClient"))

  // Delegate to QuarkusFramework base
  override def name: String = QuarkusFramework.name
  override def serviceAnnotation: jvm.Annotation = QuarkusFramework.serviceAnnotation
  override def constructorAnnotations: List[jvm.Annotation] = QuarkusFramework.constructorAnnotations

  override def serverAnnotations: List[jvm.Annotation] =
    List(
      jvm.Annotation(GrpcServiceAnnotation, Nil),
      jvm.Annotation(FrameworkTypes.CDI.Singleton, Nil)
    )

  override def clientFieldAnnotations(serviceName: String): List[jvm.Annotation] =
    List(jvm.Annotation(GrpcClient, List(jvm.Annotation.Arg.Positional(jvm.StrLit(serviceName).code))))
}
