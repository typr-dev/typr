package typr.scaladsl

import typr.dsl.Bijection
import java.util.Optional
import scala.jdk.OptionConverters._

object Bijections {

  // ================================
  // Optional<T> ↔ Option[T]
  // ================================

  /** Bijection between Java Optional[T] and Scala Option[T]. Used for type-safe phantom type conversion in PgTypename/MariaTypename.
    *
    * Usage: val typename: PgTypename[Option[String]] = pgType.opt().typename().to(optionalToOption[String])
    */
  def optionalToOption[T]: Bijection[Optional[T], Option[T]] = {
    Bijection.of[Optional[T], Option[T]](
      (opt: Optional[T]) => opt.toScala,
      (option: Option[T]) => option.toJava
    )
  }

  /** Bijection between Scala Option[T] and Java Optional[T]. Inverse of optionalToOption.
    */
  def optionToOptional[T]: Bijection[Option[T], Optional[T]] = optionalToOption[T].inverse()

  // ================================
  // Scala ↔ Java primitive type conversions
  // ================================

  // Create Bijection instances for Scala → Java type conversions
  val scalaBooleanToJavaBoolean: Bijection[Boolean, java.lang.Boolean] = {
    Bijection.of[Boolean, java.lang.Boolean](
      (b: Boolean) => java.lang.Boolean.valueOf(b),
      (jb: java.lang.Boolean) => jb.booleanValue()
    )
  }

  // Reverse direction: Java → Scala
  val javaBooleanToScalaBoolean: Bijection[java.lang.Boolean, Boolean] = {
    Bijection.of[java.lang.Boolean, Boolean](
      (jb: java.lang.Boolean) => jb.booleanValue(),
      (b: Boolean) => java.lang.Boolean.valueOf(b)
    )
  }

  val scalaIntToJavaInteger: Bijection[Int, java.lang.Integer] = {
    Bijection.of[Int, java.lang.Integer](
      (i: Int) => java.lang.Integer.valueOf(i),
      (ji: java.lang.Integer) => ji.intValue()
    )
  }

  val scalaLongToJavaLong: Bijection[Long, java.lang.Long] = {
    Bijection.of[Long, java.lang.Long](
      (l: Long) => java.lang.Long.valueOf(l),
      (jl: java.lang.Long) => jl.longValue()
    )
  }

  val scalaShortToJavaShort: Bijection[Short, java.lang.Short] = {
    Bijection.of[Short, java.lang.Short](
      (s: Short) => java.lang.Short.valueOf(s),
      (js: java.lang.Short) => js.shortValue()
    )
  }

  val scalaFloatToJavaFloat: Bijection[Float, java.lang.Float] = {
    Bijection.of[Float, java.lang.Float](
      (f: Float) => java.lang.Float.valueOf(f),
      (jf: java.lang.Float) => jf.floatValue()
    )
  }

  val scalaDoubleToJavaDouble: Bijection[Double, java.lang.Double] = {
    Bijection.of[Double, java.lang.Double](
      (d: Double) => java.lang.Double.valueOf(d),
      (jd: java.lang.Double) => jd.doubleValue()
    )
  }

  // Identity bijections for types that don't need conversion
  def identity[T]: Bijection[T, T] = Bijection.identity[T]()
}
