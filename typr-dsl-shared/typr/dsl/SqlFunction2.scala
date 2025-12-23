package typr.dsl

case class SqlFunction2[I1, I2, O](name: String, eval: (I1, I2) => O)

object SqlFunction2 {
  def strpos[T](implicit B: Bijection[T, String]) =
    new SqlFunction2[T, String, Int]("strpos", (i1, i2) => B.underlying(i1).indexOf(i2))
}
