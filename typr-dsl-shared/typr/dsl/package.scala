package typr

package object dsl {
  // Note: RelationStructure is now a top-level interface defined in Java
  // No typealias needed - Scala code will use the Java interface directly

  type Required[T] = T

  /** This is a tuple, but it's possible to construct and deconstruct it without all the parentheses.
    *
    * The trick works because you can use infix notation for types in scala, so you can write `A ~ B` instead of `(A, B)`.
    *
    * The same can be with any binary type constructor, like `A Either B`.
    *
    * It's very rarely a good idea, but for representing joins in typo it was very liberating to get rid of the deeply nested tuples.
    *
    * {{{
    *   1 ~ 2 ~ 3 match {
    *     case a ~ b ~ c => println(a + b + c)
    *   }
    * }}}
    *
    * For scala 3 there are better ways to do this, but this is a simple way to do it which works in both intellij and scala 2
    */
  type ~[A, B] = (A, B)

  object ~ {
    def unapply[A, B](t: (A, B)): (A, B) = t
  }

  implicit class ToTupleOps[A](private val a: A) extends AnyVal {
    def ~[B](b: B): A ~ B = (a, b)
  }
}
