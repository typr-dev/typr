package typr.runtime

import scala.collection.mutable.ListBuffer

/** Scala string interpolator for creating SQL Fragments.
  *
  * Usage:
  * {{{
  * import typr.runtime.FragmentInterpolator.interpolate
  *
  * val name = PgTypes.text.encode("Alice")
  * val query = interpolate"SELECT * FROM users WHERE name = $name"
  * }}}
  */
object FragmentInterpolator {
  extension (sc: StringContext) {
    def interpolate(args: Fragment*): Fragment = {
      val parts = sc.parts.iterator
      val frags = new ListBuffer[Fragment]()

      // Add first string part
      if (parts.hasNext) {
        val first = parts.next()
        if (first.nonEmpty) {
          frags += Fragment.lit(first)
        }
      }

      // Interleave remaining parts with args
      val argsIt = args.iterator
      while (parts.hasNext && argsIt.hasNext) {
        frags += argsIt.next()
        val part = parts.next()
        if (part.nonEmpty) {
          frags += Fragment.lit(part)
        }
      }

      // Handle any remaining args (shouldn't happen with valid interpolation)
      while (argsIt.hasNext) {
        frags += argsIt.next()
      }

      frags.result() match {
        case Nil           => Fragment.empty()
        case single :: Nil => single
        case multiple =>
          val javaList = new java.util.ArrayList[Fragment](multiple.size)
          multiple.foreach(javaList.add)
          Fragment.Concat(javaList)
      }
    }
  }
}
