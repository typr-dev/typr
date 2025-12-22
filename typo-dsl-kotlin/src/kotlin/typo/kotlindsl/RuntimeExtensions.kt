package typo.kotlindsl

import typo.runtime.*
import java.util.Optional

/**
 * Kotlin extension methods for typo-runtime-java that provide:
 * - Nullable types instead of Optional
 * - Kotlin-friendly lambda syntax
 * - Better generic type handling
 */

// ================================
// PgType Extensions
// ================================

/**
 * Kotlin-friendly nullable version of PgType.opt().
 *
 * Java's PgType.opt() returns PgType<Optional<A>>, but Kotlin code should work with A? instead.
 * This extension wraps a PgType<A> to create a PgType<A?> that converts between Optional and nullable
 * at read/write boundaries.
 *
 * Usage:
 *   val nullableText: PgType<String?> = PgTypes.text.nullable()
 *
 * Instead of:
 *   val optionalText: PgType<Optional<String>> = PgTypes.text.opt()  // Java-style
 *
 * Note: This delegates to the underlying PgType.opt() and converts Optional<A> <-> A? at boundaries.
 */
fun <A : Any> PgType<A>.nullable(): PgType<A?> {
    val underlying = this.opt()

    // Create wrapper components that convert Optional<A> to A? at boundaries
    val pgTypename = underlying.typename().to(optionalToNullable<A>())

    // Use KotlinNullablePgRead which implements DbRead.Nullable marker interface
    // This tells RowParser.opt() that this column is already nullable
    val pgRead: PgRead<A?> = typo.runtime.KotlinNullablePgRead(underlying.read())

    val pgWrite = PgWrite.primitive<A?> { ps, index, value -> underlying.write().set(ps, index, value.toOptional()) }

    val pgText = PgText.instance<A?>(
        { v, sb -> underlying.pgText().unsafeEncode(v.toOptional(), sb) },
        { v, sb -> underlying.pgText().unsafeArrayEncode(v.toOptional(), sb) }
    )

    val pgJson = underlying.pgJson().bimap<A?>(
        SqlFunction { opt -> opt.orNull() },
        { nullable -> nullable.toOptional() }
    )

    return PgType.of(pgTypename, pgRead, pgWrite, pgText, pgJson)
}

// ================================
// DuckDbType Extensions
// ================================

/**
 * Kotlin-friendly nullable version of DuckDbType.opt().
 */
fun <A : Any> DuckDbType<A>.nullable(): DuckDbType<A?> {
    val underlying = this.opt()

    // Create wrapper components that convert Optional<A> to A? at boundaries
    val duckDbTypename = underlying.typename().to(optionalToNullable<A>()) as DuckDbTypename<A?>

    // Use KotlinNullableDuckDbRead which implements DbRead.Nullable marker interface
    // This tells RowParser.opt() that this column is already nullable
    val duckDbRead: DuckDbRead<A?> = typo.runtime.KotlinNullableDuckDbRead(underlying.read())

    val duckDbWrite = DuckDbWrite.primitive<A?> { ps, index, value -> underlying.write().set(ps, index, value.toOptional()) }

    val duckDbStringifier = DuckDbStringifier.instance<A?> { v, sb, i -> underlying.stringifier().unsafeEncode(v.toOptional(), sb, i) }

    val duckDbJson = underlying.duckDbJson().bimap<A?>(
        SqlFunction { opt -> opt.orNull() },
        { nullable -> nullable.toOptional() }
    )

    return DuckDbType(duckDbTypename, duckDbRead, duckDbWrite, duckDbStringifier, duckDbJson)
}

// ================================
// MariaType Extensions
// ================================

/**
 * Kotlin-friendly nullable version of MariaType.opt().
 */
fun <A : Any> MariaType<A>.nullable(): MariaType<A?> {
    val underlying = this.opt()

    // Create wrapper components that convert Optional<A> to A? at boundaries
    val mariaTypename = underlying.typename().to(optionalToNullable<A>()) as MariaTypename<A?>

    // Use KotlinNullableMariaRead which implements DbRead.Nullable marker interface
    // This tells RowParser.opt() that this column is already nullable
    val mariaRead: MariaRead<A?> = typo.runtime.KotlinNullableMariaRead(underlying.read())

    val mariaWrite = MariaWrite.primitive<A?> { ps, index, value -> underlying.write().set(ps, index, value.toOptional()) }

    val mariaText = MariaText.instance<A?> { v, sb -> underlying.mariaText().unsafeEncode(v.toOptional(), sb) }

    val mariaJson = underlying.mariaJson().bimap<A?>(
        SqlFunction { opt -> opt.orNull() },
        { nullable -> nullable.toOptional() }
    )

    return MariaType(mariaTypename, mariaRead, mariaWrite, mariaText, mariaJson)
}

// ================================
// OracleType Extensions
// ================================

/**
 * Kotlin-friendly nullable version of OracleType.opt().
 */
fun <A : Any> OracleType<A>.nullable(): OracleType<A?> {
    val underlying = this.opt()

    // Create wrapper components that convert Optional<A> to A? at boundaries
    val oracleTypename = underlying.typename().to(optionalToNullable<A>()) as OracleTypename<A?>

    // Use KotlinNullableOracleRead which implements DbRead.Nullable marker interface
    // This tells RowParser.opt() that this column is already nullable
    val oracleRead: OracleRead<A?> = typo.runtime.KotlinNullableOracleRead(underlying.read())

    val oracleWrite = OracleWrite.primitive<A?> { ps, index, value -> underlying.write().set(ps, index, value.toOptional()) }

    val oracleJson = underlying.oracleJson().bimap<A?>(
        SqlFunction { opt -> opt.orNull() },
        { nullable -> nullable.toOptional() }
    )

    return OracleType(oracleTypename, oracleRead, oracleWrite, oracleJson)
}

// ================================
// Either Extensions (value-level)
// ================================

/**
 * Convert Either<L, R> to R? (nullable right value).
 * Uses .orNull() from OptionalExtensions.
 */
fun <L, R> Either<L, R>.rightOrNull(): R? {
    return this.asOptional().orNull()
}

/**
 * Get left value or null.
 */
fun <L, R> Either<L, R>.leftOrNull(): L? {
    return when (this) {
        is typo.runtime.Either.Left -> this.value()
        else -> null
    }
}

// ================================
// Arr Extensions (value-level)
// ================================

/**
 * Reshape array to new dimensions or return null.
 * Uses .orNull() from OptionalExtensions.
 */
fun <A> Arr<A>.reshapeOrNull(vararg newDims: Int): Arr<A>? {
    return this.reshape(*newDims).orNull()
}

/**
 * Get array element at indices or return null.
 * Uses .orNull() from OptionalExtensions.
 */
fun <A> typo.data.Arr<A>.getOrNull(vararg indices: Int): A? {
    return this.get(*indices).orNull()
}

// ================================
// Range Extensions (value-level)
// ================================

/**
 * Get finite range or null.
 * Uses .orNull() from OptionalExtensions.
 */
fun <T : Comparable<T>> Range<T>.finiteOrNull(): RangeFinite<T>? {
    return this.finite().orNull()
}

// ================================
// Fragment Extensions
// ================================

/**
 * Build a Fragment using Kotlin DSL.
 */
inline fun buildFragment(block: typo.runtime.Fragment.Builder.() -> Unit): Fragment {
    val builder = typo.runtime.Fragment.Builder()
    builder.block()
    return Fragment(builder.done())
}

/**
 * Append a nullable parameter to fragment builder.
 * Converts Kotlin's T? to Java's Optional<T> automatically.
 */
fun <T> typo.runtime.Fragment.Builder.paramNullable(type: PgType<T>, value: T?): typo.runtime.Fragment.Builder {
    return this.param(type.opt(), value.toOptional())
}

/**
 * Kotlin-friendly query method that accepts Kotlin ResultSetParser.
 * Converts Kotlin ResultSetParser to Java ResultSetParser automatically.
 */
fun <Out> typo.runtime.Fragment.query(parser: ResultSetParser<Out>): typo.runtime.Operation<Out> {
    return this.query(parser.underlying)
}

// ================================
// Operation Extensions
// ================================

/**
 * Extension to convert Operation<Optional<T>> results to T? automatically.
 * This handles the common case where Java methods return Optional but Kotlin code expects nullable types.
 */
fun <T> typo.runtime.Operation<java.util.Optional<T>>.runUncheckedOrNull(c: java.sql.Connection): T? {
    return this.runUnchecked(c).orNull()
}
