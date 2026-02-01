package typr.grpc.codegen

import typr.boundaries.framework.SpringFramework
import typr.jvm

/** Spring gRPC framework integration.
  *
  * Server: @GrpcService annotation on BindableService implementations Client: @Autowired for stub injection
  */
object GrpcFrameworkSpring extends GrpcFramework {

  // Spring gRPC annotations
  private val GrpcService: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.grpc.server.service.GrpcService"))

  // Delegate to SpringFramework base
  override def name: String = SpringFramework.name
  override def serviceAnnotation: jvm.Annotation = SpringFramework.serviceAnnotation
  override def constructorAnnotations: List[jvm.Annotation] = SpringFramework.constructorAnnotations

  override def serverAnnotations: List[jvm.Annotation] =
    List(
      jvm.Annotation(GrpcService, Nil),
      serviceAnnotation
    )

  override def clientFieldAnnotations(serviceName: String): List[jvm.Annotation] = Nil
}
