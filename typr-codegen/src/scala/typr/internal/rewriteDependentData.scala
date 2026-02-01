package typr
package internal

import scala.collection.immutable.SortedMap

// this exists to be able to lazily process things with dependencies
//  where processing one thing requires having processed its dependencies first
object rewriteDependentData {
  @FunctionalInterface
  trait Eval[K, V] { self =>
    def apply(key: K): Lazy[V]
    def asMaybe: EvalMaybe[K, V] = key => Some(apply(key))
  }
  @FunctionalInterface
  trait EvalMaybe[K, V] {
    def apply(key: K): Option[Lazy[V]]
  }

  def apply[K, V](in: Map[K, Lazy[V]]) = new Api(in)

  final class Api[K, V](in: Map[K, Lazy[V]]) {
    def eager[VV](f: (K, V, Eval[K, VV]) => VV)(implicit O: Ordering[K]): SortedMap[K, VV] =
      apply(f).map { case (k, v) => (k, v.forceGet) }

    def startFrom[VV](include: K => Boolean)(f: (K, V, Eval[K, VV]) => VV)(implicit O: Ordering[K]): SortedMap[K, VV] = {
      val lazyMap = apply(f)
      lazyMap.foreach {
        case (k, lazyV) if include(k) => lazyV.forceGet
        case _                        => ()
      }
      // this will include dependencies of what was chosen in `pred`
      lazyMap.flatMap { case (k, v) => v.getIfEvaluated.map(vv => (k, vv)) }
    }

    def apply[VV](f: (K, V, Eval[K, VV]) => VV)(implicit O: Ordering[K]): SortedMap[K, Lazy[VV]] = {
      // sorted to ensure consistency
      val sortedIn = SortedMap.empty[K, Lazy[V]] ++ in

      lazy val eval: Eval[K, VV] = rewritten.apply

      lazy val rewritten: SortedMap[K, Lazy[VV]] =
        sortedIn.map { case (k, lazyV) => k -> lazyV.map(v => f(k, v, eval)) }

      rewritten
    }
  }
}
