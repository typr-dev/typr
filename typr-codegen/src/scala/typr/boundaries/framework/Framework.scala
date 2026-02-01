package typr.boundaries.framework

import typr.jvm

/** Base trait for framework integration in code generation.
  *
  * Provides common patterns for dependency injection and service annotations across Spring, Quarkus, and other frameworks. Protocol-specific extensions (HTTP, Messaging, RPC) extend this trait.
  */
trait Framework {

  /** Framework name for documentation and logging */
  def name: String

  /** Annotation to mark a class as a service/bean (e.g., @Service, @ApplicationScoped) */
  def serviceAnnotation: jvm.Annotation

  /** Annotations for constructor-based dependency injection (e.g., @Inject for Quarkus, empty for Spring implicit injection)
    */
  def constructorAnnotations: List[jvm.Annotation]
}
