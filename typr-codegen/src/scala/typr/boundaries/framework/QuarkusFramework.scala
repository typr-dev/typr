package typr.boundaries.framework

import typr.jvm

/** Quarkus / CDI framework integration base.
  *
  * Quarkus uses @ApplicationScoped for bean scoping and @Inject for explicit constructor injection.
  */
object QuarkusFramework extends Framework {

  override def name: String = "Quarkus"

  override def serviceAnnotation: jvm.Annotation =
    jvm.Annotation(FrameworkTypes.CDI.ApplicationScoped, Nil)

  override def constructorAnnotations: List[jvm.Annotation] =
    List(jvm.Annotation(FrameworkTypes.CDI.Inject, Nil))
}
