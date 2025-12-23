package typr.kotlindsl

import typr.runtime.DbType
import typr.runtime.Fragment as JavaFragment
import typr.runtime.Operation as JavaOperation
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicInteger

/** Kotlin wrapper for typr.runtime.Fragment with Kotlin-native APIs.
  *
  * This class wraps the Java Fragment interface and provides Kotlin-friendly methods.
  */
class Fragment(val underlying: JavaFragment) {

    fun render(): String = underlying.render()

    fun render(sb: StringBuilder) = underlying.render(sb)

    fun set(stmt: PreparedStatement) = underlying.set(stmt)

    fun set(stmt: PreparedStatement, idx: AtomicInteger) = underlying.set(stmt, idx)

    fun append(other: Fragment): Fragment = Fragment(underlying.append(other.underlying))

    operator fun plus(other: Fragment): Fragment = append(other)

    fun <T> query(parser: ResultSetParser<T>): Operation.Query<T> =
        Operation.Query(this, parser)

    fun update(): Operation.Update =
        Operation.Update(this)

    fun <T> updateReturning(parser: ResultSetParser<T>): Operation.UpdateReturning<T> =
        Operation.UpdateReturning(this, parser)

    fun <T> updateReturningGeneratedKeys(columnNames: Array<String>, parser: ResultSetParser<T>): Operation.UpdateReturningGeneratedKeys<T> =
        Operation.UpdateReturningGeneratedKeys(this, columnNames, parser)

    fun <Row> updateMany(parser: RowParser<Row>, rows: Iterator<Row>): Operation.UpdateMany<Row> =
        Operation.UpdateMany(this, parser, rows)

    fun <Row> updateManyReturning(parser: RowParser<Row>, rows: Iterator<Row>): Operation.UpdateManyReturning<Row> =
        Operation.UpdateManyReturning(this, parser, rows)

    fun <Row> updateReturningEach(parser: RowParser<Row>, rows: Iterator<Row>): Operation.UpdateReturningEach<Row> =
        Operation.UpdateReturningEach(this, parser, rows)

    companion object {
        @JvmField
        val EMPTY: Fragment = Fragment(JavaFragment.EMPTY)

        @JvmStatic
        fun lit(value: String): Fragment = Fragment(JavaFragment.lit(value))

        @JvmStatic
        fun empty(): Fragment = EMPTY

        @JvmStatic
        fun quotedDouble(value: String): Fragment = Fragment(JavaFragment.quotedDouble(value))

        @JvmStatic
        fun quotedSingle(value: String): Fragment = Fragment(JavaFragment.quotedSingle(value))

        @JvmStatic
        fun <A> value(value: A, dbType: DbType<A>): Fragment =
            Fragment(JavaFragment.value(value, dbType))

        /** Encode a value into a SQL fragment using the provided database type. */
        @JvmStatic
        fun <A> encode(dbType: DbType<A>, value: A): Fragment =
            Fragment(JavaFragment.encode(dbType, value))

        @JvmStatic
        fun and(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.and(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun and(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.and(fragments.map { it.underlying }))

        @JvmStatic
        fun or(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.or(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun or(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.or(fragments.map { it.underlying }))

        @JvmStatic
        fun whereAnd(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.whereAnd(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun whereAnd(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.whereAnd(fragments.map { it.underlying }))

        @JvmStatic
        fun whereOr(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.whereOr(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun whereOr(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.whereOr(fragments.map { it.underlying }))

        @JvmStatic
        fun set(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.set(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun set(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.set(fragments.map { it.underlying }))

        @JvmStatic
        fun parentheses(fragment: Fragment): Fragment =
            Fragment(JavaFragment.parentheses(fragment.underlying))

        @JvmStatic
        fun comma(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.comma(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun comma(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.comma(fragments.map { it.underlying }))

        @JvmStatic
        fun orderBy(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.orderBy(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun orderBy(fragments: List<Fragment>): Fragment =
            Fragment(JavaFragment.orderBy(fragments.map { it.underlying }))

        @JvmStatic
        fun join(fragments: List<Fragment>, separator: Fragment): Fragment =
            Fragment(JavaFragment.join(fragments.map { it.underlying }, separator.underlying))

        @JvmStatic
        fun concat(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.concat(*fragments.map { it.underlying }.toTypedArray()))

        @JvmStatic
        fun <T> `in`(column: String, values: Array<T>, dbType: DbType<T>): Fragment =
            Fragment(JavaFragment.`in`(column, values, dbType))

        @JvmStatic
        fun compositeIn(columns: Array<String>, tuples: List<Array<Any?>>, types: Array<DbType<*>>): Fragment =
            Fragment(JavaFragment.compositeIn(columns, tuples, types))

        @JvmStatic
        fun interpolate(initial: String): Builder =
            Builder(JavaFragment.interpolate(initial))

        @JvmStatic
        fun interpolate(vararg fragments: Fragment): Fragment =
            Fragment(JavaFragment.interpolate(*fragments.map { it.underlying }.toTypedArray()))
    }

    /** Builder for creating Fragments with a fluent API */
    class Builder(private val underlying: JavaFragment.Builder) {
        fun sql(s: String): Builder {
            underlying.sql(s)
            return this
        }

        fun <T> param(dbType: DbType<T>, value: T): Builder {
            underlying.param(dbType, value)
            return this
        }

        fun param(fragment: Fragment): Builder {
            underlying.param(fragment.underlying)
            return this
        }

        fun done(): Fragment = Fragment(underlying.done())
    }
}
