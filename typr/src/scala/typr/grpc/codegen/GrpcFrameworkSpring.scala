package typr.grpc.codegen

import typr.jvm

/** Spring gRPC framework integration.
  *
  * Server: @GrpcService annotation on BindableService implementations Client: @Autowired for stub injection
  */
object GrpcFrameworkSpring extends GrpcFramework {

  // Spring gRPC annotations
  private val GrpcService: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.grpc.server.service.GrpcService"))
  private val Service: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.stereotype.Service"))

  override def serverAnnotations: List[jvm.Annotation] =
    List(
      jvm.Annotation(GrpcService, Nil),
      jvm.Annotation(Service, Nil)
    )

  override def clientFieldAnnotations(serviceName: String): List[jvm.Annotation] = Nil

  override def constructorAnnotations: List[jvm.Annotation] = Nil
}
