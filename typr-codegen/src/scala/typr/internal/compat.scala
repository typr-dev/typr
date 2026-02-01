package typr.internal

import scala.collection.mutable

object compat {
  // compat with scala 2.12
  implicit class ListOps[A](as: List[A]) {
    def distinctByCompat[B, C](f: A => B): List[A] = {
      if (as.lengthCompare(1) <= 0) as
      else {
        val builder = List.newBuilder[A]
        val seen = mutable.HashSet.empty[B]
        val it = as.iterator
        var different = false
        while (it.hasNext) {
          val next = it.next()
          if (seen.add(f(next))) builder += next else different = true
        }
        if (different) builder.result() else as
      }
    }
  }
}
