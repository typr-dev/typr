package adventureworks

case class Reverse[T](t: T) extends AnyVal
object Reverse {
  implicit def ordering[T: Ordering]: Ordering[Reverse[T]] = Ordering[T].reverse.on(_.t)
}
