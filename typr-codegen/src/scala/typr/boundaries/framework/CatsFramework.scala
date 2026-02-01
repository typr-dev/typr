package typr.boundaries.framework

import typr.jvm

/** Cats/Typelevel framework integration base.
  *
  * The Typelevel stack doesn't use DI annotations - instead it relies on constructor parameters and explicit wiring. This generates pure functional code with cats.effect.IO.
  */
object CatsFramework extends Framework {

  override def name: String = "Cats"

  override def serviceAnnotation: jvm.Annotation =
    jvm.Annotation(FrameworkTypes.Scala.Unused, Nil)

  override def constructorAnnotations: List[jvm.Annotation] = Nil
}
