package typr.internal

object forget {
  def apply[T](t: T): Unit = (t, ())._2
}
