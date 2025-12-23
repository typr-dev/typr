package typr.dsl

trait Bijection[W, U] {
  def underlying(w: W): U
  def map(w: W)(f: U => U): W
}

object Bijection {
  def id[T]: Bijection[T, T] = apply[T, T](identity)(identity)
  implicit val idBoolean: Bijection[Boolean, Boolean] = id[Boolean]
  implicit val idString: Bijection[String, String] = id[String]
  implicit val idByte: Bijection[Byte, Byte] = id[Byte]
  implicit val idShort: Bijection[Short, Short] = id[Short]
  implicit val idInt: Bijection[Int, Int] = id[Int]
  implicit val idLong: Bijection[Long, Long] = id[Long]
  implicit val idFloat: Bijection[Float, Float] = id[Float]
  implicit val idDouble: Bijection[Double, Double] = id[Double]

  implicit def array[T]: Bijection[Array[T], Array[T]] =
    new Bijection[Array[T], Array[T]] {
      override def underlying(w: Array[T]): Array[T] = w
      override def map(w: Array[T])(f: Array[T] => Array[T]): Array[T] = f(w)
    }

  def apply[W, U](_underlying: W => U)(_wrap: U => W): Bijection[W, U] =
    new Bijection[W, U] {
      override def underlying(w: W): U = _underlying(w)
      override def map(w: W)(f: U => U): W = _wrap(f(_underlying(w)))
    }
}
