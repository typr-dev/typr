package typr.dsl.internal

object DummyOrdering extends Ordering[Any] {
  override def compare(x: Any, y: Any): Int =
    (x, y) match {
      case (x: Comparable[Any] @unchecked, y: Comparable[Any] @unchecked) =>
        x.compareTo(y)
      case (x: Option[Any] @unchecked, y: Option[Any] @unchecked) =>
        (x, y) match {
          case (None, None)       => 0
          case (None, _)          => -1
          case (_, None)          => 1
          case (Some(x), Some(y)) => DummyOrdering.compare(x, y)
        }
      case _ =>
        x.toString.compareTo(y.toString)
    }

  implicit def ord[T]: Ordering[T] = DummyOrdering.asInstanceOf[Ordering[T]]
}
