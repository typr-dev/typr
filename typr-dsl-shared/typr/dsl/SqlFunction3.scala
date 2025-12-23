package typr.dsl

case class SqlFunction3[I1, I2, I3, O](name: String, eval: (I1, I2, I3) => O)

object SqlFunction3 {
  def substring[T]()(implicit B: Bijection[T, String]) =
    new SqlFunction3[T, Int, Int, T](
      "substring",
      (stringish, from0, count) =>
        B.map(stringish) { str =>
          val from = from0 - 1
          str.substring(from, Math.min(str.length, from + count))
        }
    )
}
