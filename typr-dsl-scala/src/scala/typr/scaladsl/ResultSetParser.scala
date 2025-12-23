package typr.scaladsl

import java.sql.ResultSet

/** Scala wrapper for typr.runtime.ResultSetParser that provides Scala-native methods.
  *
  * Wraps the Java ResultSetParser to provide interop with Java APIs.
  */
class ResultSetParser[Out](val underlying: typr.runtime.ResultSetParser[Out]) {
  def apply(rs: ResultSet): Out = underlying.apply(rs)
}
