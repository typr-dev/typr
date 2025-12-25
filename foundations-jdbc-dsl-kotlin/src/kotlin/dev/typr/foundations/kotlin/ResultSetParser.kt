package dev.typr.foundations.kotlin

import java.sql.ResultSet

/**
 * Kotlin wrapper for dev.typr.foundations.ResultSetParser that provides Kotlin-native methods.
 *
 * Wraps the Java ResultSetParser to provide interop with Java APIs.
 */
class ResultSetParser<Out>(val underlying: dev.typr.foundations.ResultSetParser<Out>) {
    fun apply(rs: ResultSet): Out = underlying.apply(rs)
}

/**
 * Convert a Java ResultSetParser to a Kotlin ResultSetParser.
 */
fun <Out> dev.typr.foundations.ResultSetParser<Out>.asKotlin(): ResultSetParser<Out> {
    return ResultSetParser(this)
}

/**
 * Convert a Kotlin ResultSetParser to a Java ResultSetParser.
 */
fun <Out> ResultSetParser<Out>.asJava(): dev.typr.foundations.ResultSetParser<Out> {
    return underlying
}
