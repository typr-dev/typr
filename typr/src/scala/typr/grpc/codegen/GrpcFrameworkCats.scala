package typr.grpc.codegen

import typr.boundaries.framework.CatsFramework
import typr.jvm

/** Cats/Typelevel fs2-grpc framework integration.
  *
  * Uses pure functional patterns with cats.effect.IO and trait-based service interfaces.
  *
  * Key differences from Spring/Quarkus:
  *   - No DI annotations (constructor params for dependencies)
  *   - Server implementations are traits with IO methods
  *   - Client wrappers provide IO-based method signatures
  */
object GrpcFrameworkCats extends GrpcFramework {

  // Delegate to CatsFramework base
  override def name: String = CatsFramework.name
  override def serviceAnnotation: jvm.Annotation = CatsFramework.serviceAnnotation
  override def constructorAnnotations: List[jvm.Annotation] = CatsFramework.constructorAnnotations

  override def serverAnnotations: List[jvm.Annotation] = Nil

  override def clientFieldAnnotations(serviceName: String): List[jvm.Annotation] = Nil
}
