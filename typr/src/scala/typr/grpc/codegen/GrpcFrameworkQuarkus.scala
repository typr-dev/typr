package typr.grpc.codegen

import typr.jvm
import typr.internal.codegen._

/** Quarkus gRPC framework integration.
  *
  * Server: @GrpcService + @Singleton annotations Client: @GrpcClient("service-name") field injection
  */
object GrpcFrameworkQuarkus extends GrpcFramework {

  // Quarkus/CDI annotations
  private val GrpcServiceAnnotation: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.quarkus.grpc.GrpcService"))
  private val Singleton: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("jakarta.inject.Singleton"))
  private val GrpcClient: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.quarkus.grpc.GrpcClient"))
  private val Inject: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("jakarta.inject.Inject"))

  override def serverAnnotations: List[jvm.Annotation] =
    List(
      jvm.Annotation(GrpcServiceAnnotation, Nil),
      jvm.Annotation(Singleton, Nil)
    )

  override def clientFieldAnnotations(serviceName: String): List[jvm.Annotation] =
    List(jvm.Annotation(GrpcClient, List(jvm.Annotation.Arg.Positional(jvm.StrLit(serviceName).code))))

  override def constructorAnnotations: List[jvm.Annotation] =
    List(jvm.Annotation(Inject, Nil))
}
