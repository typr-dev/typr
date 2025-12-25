package dev.typr.foundations.scala

import java.sql.ResultSet

/** Scala wrapper for dev.typr.foundations.ResultSetParser that provides Scala-native methods.
  *
  * Wraps the Java ResultSetParser to provide interop with Java APIs.
  */
class ResultSetParser[Out](val underlying: dev.typr.foundations.ResultSetParser[Out]) {
  def apply(rs: ResultSet): Out = underlying.apply(rs)
}
