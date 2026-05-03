package typr.internal.analysis

/** A type that represents whether a query returns rows or not.
  */
sealed trait MaybeReturnsRows[+T] {
  def map[U](f: T => U): MaybeReturnsRows[U] = this match {
    case MaybeReturnsRows.Query(v) => MaybeReturnsRows.Query(f(v))
    case MaybeReturnsRows.Update   => MaybeReturnsRows.Update
  }
  def toOption: Option[T] = this match {
    case MaybeReturnsRows.Query(v) => Some(v)
    case MaybeReturnsRows.Update   => None
  }
}
object MaybeReturnsRows {
  case class Query[T](v: T) extends MaybeReturnsRows[T]
  case object Update extends MaybeReturnsRows[Nothing]
}
