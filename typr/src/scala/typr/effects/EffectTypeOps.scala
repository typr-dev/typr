package typr.effects

import typr.jvm

/** Abstraction for effect type operations in code generation.
  *
  * Enables generating code for multiple effect systems (IO, Task, Mono, etc.) without duplicating logic.
  */
trait EffectTypeOps {

  /** The effect type itself (e.g., IO, Task, Mono, Uni) */
  def tpe: jvm.Type.Qualified

  /** Map over the effect value: effect.map(f) */
  def map(effect: jvm.Code, f: jvm.Code): jvm.Code

  /** FlatMap over the effect value: effect.flatMap(f) where f returns Effect[B] */
  def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code

  /** Wrap a value in the effect: Effect.pure(value) */
  def pure(value: jvm.Code): jvm.Code

  /** Wrap a CompletionStage supplier in the effect (non-blocking).
    *
    * The supplier is a lambda that returns CompletableFuture/CompletionStage. For Mutiny: Uni.createFrom().completionStage(supplier) For Reactor: Mono.fromCompletionStage(supplier)
    */
  def fromCompletionStage(supplier: jvm.Code): jvm.Code

  /** Traverse an iterable, applying an effectful function to each element and discarding results.
    *
    * Similar to Cats `traverse_` or ZIO `foreachDiscard`.
    *
    * @param iterable
    *   The collection to iterate (java.lang.Iterable)
    * @param elementVar
    *   Variable name for each element
    * @param body
    *   Effect to execute for each element (references elementVar)
    */
  def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code

  /** Additional imports needed by foreachDiscard (wildcard imports like "scala.jdk.CollectionConverters._").
    *
    * These should be added to the File's additionalImports field.
    */
  def foreachDiscardImports: List[String] = Nil

  /** Create an async effect wrapper.
    *
    * @param resultType
    *   The type of the success value (used for explicit type declarations where inference fails)
    * @param bodyBuilder
    *   Function that receives (onSuccess, onFailure) callbacks and returns the body code. onSuccess: value => code that signals success (e.g., em.complete(value)) onFailure: error => code that
    *   signals failure (e.g., em.fail(error))
    * @return
    *   The complete async expression wrapped in the effect type
    */
  def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code
}
