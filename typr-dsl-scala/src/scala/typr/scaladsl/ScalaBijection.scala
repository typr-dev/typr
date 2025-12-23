package typr.scaladsl

import typr.dsl.Bijection

trait ScalaBijection[ScalaType, JavaType] {
  def toJava(s: ScalaType): JavaType
  def toScala(j: JavaType): ScalaType
  def underlying: Bijection[ScalaType, JavaType]
}

object ScalaBijection {
  def apply[S, J](implicit ev: ScalaBijection[S, J]): ScalaBijection[S, J] = ev

  implicit val booleanBijection: ScalaBijection[Boolean, java.lang.Boolean] = new ScalaBijection[Boolean, java.lang.Boolean] {
    def toJava(s: Boolean): java.lang.Boolean = java.lang.Boolean.valueOf(s)
    def toScala(j: java.lang.Boolean): Boolean = j.booleanValue()
    val underlying: Bijection[Boolean, java.lang.Boolean] = Bijection.asBool().asInstanceOf[Bijection[Boolean, java.lang.Boolean]]
  }

  implicit val intBijection: ScalaBijection[Int, java.lang.Integer] = new ScalaBijection[Int, java.lang.Integer] {
    def toJava(s: Int): java.lang.Integer = java.lang.Integer.valueOf(s)
    def toScala(j: java.lang.Integer): Int = j.intValue()
    val underlying: Bijection[Int, java.lang.Integer] = Bijection.identity[Int]().asInstanceOf[Bijection[Int, java.lang.Integer]]
  }

  implicit val longBijection: ScalaBijection[Long, java.lang.Long] = new ScalaBijection[Long, java.lang.Long] {
    def toJava(s: Long): java.lang.Long = java.lang.Long.valueOf(s)
    def toScala(j: java.lang.Long): Long = j.longValue()
    val underlying: Bijection[Long, java.lang.Long] = Bijection.identity[Long]().asInstanceOf[Bijection[Long, java.lang.Long]]
  }

  implicit val shortBijection: ScalaBijection[Short, java.lang.Short] = new ScalaBijection[Short, java.lang.Short] {
    def toJava(s: Short): java.lang.Short = java.lang.Short.valueOf(s)
    def toScala(j: java.lang.Short): Short = j.shortValue()
    val underlying: Bijection[Short, java.lang.Short] = Bijection.identity[Short]().asInstanceOf[Bijection[Short, java.lang.Short]]
  }

  implicit val floatBijection: ScalaBijection[Float, java.lang.Float] = new ScalaBijection[Float, java.lang.Float] {
    def toJava(s: Float): java.lang.Float = java.lang.Float.valueOf(s)
    def toScala(j: java.lang.Float): Float = j.floatValue()
    val underlying: Bijection[Float, java.lang.Float] = Bijection.identity[Float]().asInstanceOf[Bijection[Float, java.lang.Float]]
  }

  implicit val doubleBijection: ScalaBijection[Double, java.lang.Double] = new ScalaBijection[Double, java.lang.Double] {
    def toJava(s: Double): java.lang.Double = java.lang.Double.valueOf(s)
    def toScala(j: java.lang.Double): Double = j.doubleValue()
    val underlying: Bijection[Double, java.lang.Double] = Bijection.identity[Double]().asInstanceOf[Bijection[Double, java.lang.Double]]
  }

  implicit def identityBijection[T]: ScalaBijection[T, T] = new ScalaBijection[T, T] {
    def toJava(s: T): T = s
    def toScala(j: T): T = j
    val underlying: Bijection[T, T] = Bijection.identity[T]()
  }
}
