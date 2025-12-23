package typr.dsl

case class SqlFunction1[I1, O](name: String, eval: I1 => O)

object SqlFunction1 {
  def length[T](implicit B: Bijection[T, String]) =
    new SqlFunction1[T, Int]("length", i1 => B.underlying(i1).length)
  def lower[T](implicit B: Bijection[T, String]) =
    new SqlFunction1[T, T]("lower", i1 => B.map(i1)(_.toLowerCase))
  def upper[T](implicit B: Bijection[T, String]) =
    new SqlFunction1[T, T]("upper", i1 => B.map(i1)(_.toUpperCase))
  def reverse[T](implicit B: Bijection[T, String]) =
    new SqlFunction1[T, T]("reverse", i1 => B.map(i1)(_.reverse))
}
