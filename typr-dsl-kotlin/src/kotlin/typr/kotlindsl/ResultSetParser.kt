package typr.kotlindsl

import java.sql.ResultSet

/**
 * Kotlin wrapper for typr.runtime.ResultSetParser that provides Kotlin-native methods.
 *
 * Wraps the Java ResultSetParser to provide interop with Java APIs.
 */
class ResultSetParser<Out>(val underlying: typr.runtime.ResultSetParser<Out>) {
    fun apply(rs: ResultSet): Out = underlying.apply(rs)
}

/**
 * Convert a Java ResultSetParser to a Kotlin ResultSetParser.
 */
fun <Out> typr.runtime.ResultSetParser<Out>.asKotlin(): ResultSetParser<Out> {
    return ResultSetParser(this)
}

/**
 * Convert a Kotlin ResultSetParser to a Java ResultSetParser.
 */
fun <Out> ResultSetParser<Out>.asJava(): typr.runtime.ResultSetParser<Out> {
    return underlying
}
