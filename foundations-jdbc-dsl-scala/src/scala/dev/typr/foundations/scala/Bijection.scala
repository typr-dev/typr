package dev.typr.foundations.scala

/** Bijection companion object with factory methods for creating bijections. */
object Bijection {
  def apply[T, TT](unwrap: T => TT)(wrap: TT => T): dev.typr.foundations.dsl.Bijection[T, TT] =
    dev.typr.foundations.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

  def of[T, TT](unwrap: T => TT, wrap: TT => T): dev.typr.foundations.dsl.Bijection[T, TT] =
    dev.typr.foundations.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

  def identity[T](): dev.typr.foundations.dsl.Bijection[T, T] =
    dev.typr.foundations.dsl.Bijection.identity[T]()

  def asString: dev.typr.foundations.dsl.Bijection[String, String] =
    dev.typr.foundations.dsl.Bijection.asString()

  def asBool: dev.typr.foundations.dsl.Bijection[java.lang.Boolean, java.lang.Boolean] =
    dev.typr.foundations.dsl.Bijection.asBool()
}
