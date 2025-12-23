package typr.kotlindsl

import typr.runtime.Operation as JavaOperation
import java.sql.Connection
import java.sql.SQLException

/** Kotlin wrapper for typr.runtime.Operation with Kotlin-native APIs.
  *
  * This sealed interface wraps the Java Operation and provides Kotlin-friendly methods.
  */
sealed interface Operation<Out> {
    val underlying: JavaOperation<*>

    @Throws(SQLException::class)
    fun run(conn: Connection): Out

    fun runUnchecked(conn: Connection): Out {
        return try {
            run(conn)
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    /** Query operation that returns a parsed result */
    class Query<Out>(override val underlying: JavaOperation.Query<Out>) : Operation<Out> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): Out = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Out> invoke(query: Fragment, parser: ResultSetParser<Out>): Query<Out> =
                Query(JavaOperation.Query(query.underlying, parser.underlying))
        }
    }

    /** Update operation that returns the number of affected rows */
    class Update(override val underlying: JavaOperation.Update) : Operation<Int> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): Int = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun invoke(query: Fragment): Update =
                Update(JavaOperation.Update(query.underlying))
        }
    }

    /** Update operation with RETURNING clause */
    class UpdateReturning<Out>(override val underlying: JavaOperation.UpdateReturning<Out>) : Operation<Out> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): Out = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Out> invoke(query: Fragment, parser: ResultSetParser<Out>): UpdateReturning<Out> =
                UpdateReturning(JavaOperation.UpdateReturning(query.underlying, parser.underlying))
        }
    }

    /** Update operation that returns generated keys (for Oracle, which doesn't support RETURNING in the same way) */
    class UpdateReturningGeneratedKeys<Out>(override val underlying: JavaOperation.UpdateReturningGeneratedKeys<Out>) : Operation<Out> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): Out = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Out> invoke(query: Fragment, columnNames: Array<String>, parser: ResultSetParser<Out>): UpdateReturningGeneratedKeys<Out> =
                UpdateReturningGeneratedKeys(JavaOperation.UpdateReturningGeneratedKeys(query.underlying, columnNames, parser.underlying))
        }
    }

    /** Batch update operation that returns an array of update counts */
    class UpdateMany<Row>(override val underlying: JavaOperation.UpdateMany<Row>) : Operation<IntArray> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): IntArray = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Row> invoke(query: Fragment, parser: RowParser<Row>, rows: Iterator<Row>): UpdateMany<Row> =
                UpdateMany(JavaOperation.UpdateMany(query.underlying, parser.underlying, rows))
        }
    }

    /** Batch update operation with RETURNING clause that returns a list of rows */
    class UpdateManyReturning<Row>(override val underlying: JavaOperation.UpdateManyReturning<Row>) : Operation<List<Row>> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): List<Row> = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Row> invoke(query: Fragment, parser: RowParser<Row>, rows: Iterator<Row>): UpdateManyReturning<Row> =
                UpdateManyReturning(JavaOperation.UpdateManyReturning(query.underlying, parser.underlying, rows))
        }
    }

    /** Update each row individually with RETURNING clause (for MariaDB) */
    class UpdateReturningEach<Row>(override val underlying: JavaOperation.UpdateReturningEach<Row>) : Operation<List<Row>> {
        @Throws(SQLException::class)
        override fun run(conn: Connection): List<Row> = underlying.run(conn)

        companion object {
            @JvmStatic
            operator fun <Row> invoke(query: Fragment, parser: RowParser<Row>, rows: Iterator<Row>): UpdateReturningEach<Row> =
                UpdateReturningEach(JavaOperation.UpdateReturningEach(query.underlying, parser.underlying, rows))
        }
    }
}
