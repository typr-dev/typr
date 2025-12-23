package typr.kotlindsl

import java.sql.ResultSet

/**
 * Kotlin wrapper for typr.runtime.RowParser that provides Kotlin-native methods.
 *
 * This class has the same API surface as the Java RowParser but returns Kotlin types (T?)
 * instead of Java types (Optional<T>).
 */
class RowParser<Row>(val underlying: typr.runtime.RowParser<Row>) {

    /**
     * Parse all rows from a ResultSet.
     * Returns Kotlin List instead of java.util.List.
     */
    fun all(): ResultSetParser<List<Row>> {
        val javaParser = underlying.all()
        return ResultSetParser(typr.runtime.ResultSetParser { rs -> javaParser.apply(rs).toList() })
    }

    /**
     * Parse exactly one row from a ResultSet.
     * Returns Row directly (throws if not exactly one row).
     */
    fun exactlyOne(): ResultSetParser<Row> {
        return ResultSetParser(underlying.exactlyOne())
    }

    /**
     * Parse the first row from a ResultSet or null if empty.
     * Returns Row? instead of Optional<Row>.
     */
    fun first(): ResultSetParser<Row?> {
        val javaParser = typr.runtime.ResultSetParser.First(underlying)
        return ResultSetParser(typr.runtime.ResultSetParser { rs -> javaParser.apply(rs).orNull() })
    }

    /**
     * Parse the first row from a ResultSet or null if empty.
     * Alias for first() to match Java API.
     */
    fun firstOrNull(): ResultSetParser<Row?> = first()

    /**
     * Parse at most one row from a ResultSet or null.
     * Returns Row? instead of Optional<Row>.
     */
    fun maxOne(): ResultSetParser<Row?> {
        val javaParser = typr.runtime.ResultSetParser.MaxOne(underlying)
        return ResultSetParser(typr.runtime.ResultSetParser { rs -> javaParser.apply(rs).orNull() })
    }

    /**
     * Parse at most one row from a ResultSet or null.
     * Alias for maxOne() to match Kotlin conventions.
     */
    fun maxOneOrNull(): ResultSetParser<Row?> = maxOne()

    /**
     * Parse a single row from the current position in ResultSet.
     */
    fun parse(rs: ResultSet): Row = underlying.parse(rs)
}
