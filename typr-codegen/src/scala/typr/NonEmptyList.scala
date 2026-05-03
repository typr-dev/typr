package typr

import scala.reflect.ClassTag

/** This is a list which is guaranteed to be empty
  *
  * We'll keep the dependency count low, so let's avoid cats for now.
  */
case class NonEmptyList[T](head: T, tail: List[T]) {
  def ::(t: T): NonEmptyList[T] = NonEmptyList(t, head :: tail)
  def :::(ts: List[T]): NonEmptyList[T] = {
    val all = ts ::: toList
    NonEmptyList(all.head, all.tail)
  }
  def ++(other: List[T]): NonEmptyList[T] = NonEmptyList(head, tail ++ other)
  def length: Int = tail.length + 1
  def toList: List[T] = head :: tail
  def toArray(implicit CT: ClassTag[T]): Array[T] = toList.toArray
  def sorted(implicit O: Ordering[T]) = {
    val head :: tail = toList.sorted: @unchecked
    NonEmptyList(head, tail)
  }
  def iterator: Iterator[T] =
    Iterator(head) ++ tail.iterator

  def countWhere(f: T => Boolean): Int =
    (if (f(head)) 1 else 0) + tail.count(f)

  def zipWithIndex: NonEmptyList[(T, Int)] =
    NonEmptyList((head, 0), tail.zipWithIndex.map { case (t, i) => (t, i + 1) })

  def find(p: T => Boolean): Option[T] =
    if (p(head)) Some(head) else tail.find(p)

  def exists(p: T => Boolean): Boolean =
    p(head) || tail.exists(p)

  def forall(p: T => Boolean): Boolean =
    p(head) && tail.forall(p)

  def map[U](f: T => U): NonEmptyList[U] =
    NonEmptyList(f(head), tail.map(f))

  def flatMap[U](f: T => NonEmptyList[U]): NonEmptyList[U] =
    f(head) match {
      case NonEmptyList(head2, tail2) =>
        NonEmptyList(head2, tail2 ++ tail.flatMap(f(_).toList))
    }

  def zip[U](that: NonEmptyList[U]): NonEmptyList[(T, U)] =
    NonEmptyList((head, that.head), tail.zip(that.tail).map { case (t, u) => (t, u) })

  def mkString(start: String, sep: String, end: String): String =
    start + toList.mkString(sep) + end

  def mkString(sep: String): String =
    toList.mkString(sep)
}

object NonEmptyList {
  // extension method to convert NEL of tuples to map
  implicit class NonEmptyListOps[K, V](val nel: NonEmptyList[(K, V)]) extends AnyVal {
    def toMap: Map[K, V] = nel.toList.toMap
  }
  // extension method contains
  implicit class NonEmptyListContainsOps[T](val nel: NonEmptyList[T]) extends AnyVal {
    def contains(t: T): Boolean = nel.exists(_ == t)
  }
  def apply[T](head: T, tail: T*): NonEmptyList[T] =
    NonEmptyList(head, tail.toList)

  def fromList[T](ts: List[T]): Option[NonEmptyList[T]] =
    ts match {
      case head :: tail => Some(NonEmptyList(head, tail))
      case Nil =>
        None
    }
}
