package typr.boundaries.framework

import typr.jvm

/** Spring framework integration base.
  *
  * Spring uses implicit constructor injection (no @Inject annotation needed) and @Service for marking service beans.
  */
object SpringFramework extends Framework {

  override def name: String = "Spring"

  override def serviceAnnotation: jvm.Annotation =
    jvm.Annotation(FrameworkTypes.Spring.Service, Nil)

  override def constructorAnnotations: List[jvm.Annotation] = Nil
}
