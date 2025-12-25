package dev.typr.foundations.kotlin

import dev.typr.foundations.SqlFunction

/**
 * Kotlin-friendly exports of static members from Java classes.
 * Type aliases don't export static methods, so we need object wrappers.
 */

// ================================
// PgTypes - Main type registry
// ================================

/**
 * Access to all PostgreSQL type descriptors.
 * Delegates to dev.typr.foundations.PgTypes.
 */
object Types {
    // Primitive types
    val bool: PgType<Boolean> get() = dev.typr.foundations.PgTypes.bool
    val int2: PgType<Short> get() = dev.typr.foundations.PgTypes.int2
    val int4: PgType<Int> get() = dev.typr.foundations.PgTypes.int4
    val int8: PgType<Long> get() = dev.typr.foundations.PgTypes.int8
    val float4: PgType<Float> get() = dev.typr.foundations.PgTypes.float4
    val float8: PgType<Double> get() = dev.typr.foundations.PgTypes.float8
    val numeric: PgType<java.math.BigDecimal> get() = dev.typr.foundations.PgTypes.numeric
    val text: PgType<String> get() = dev.typr.foundations.PgTypes.text
    val bytea: PgType<ByteArray> get() = dev.typr.foundations.PgTypes.bytea
    val uuid: PgType<java.util.UUID> get() = dev.typr.foundations.PgTypes.uuid

    // Date/time types (using java.time)
    val date: PgType<java.time.LocalDate> get() = dev.typr.foundations.PgTypes.date
    val time: PgType<java.time.LocalTime> get() = dev.typr.foundations.PgTypes.time
    val timestamp: PgType<java.time.LocalDateTime> get() = dev.typr.foundations.PgTypes.timestamp
    val timestamptz: PgType<java.time.Instant> get() = dev.typr.foundations.PgTypes.timestamptz
    val interval: PgType<org.postgresql.util.PGInterval> get() = dev.typr.foundations.PgTypes.interval

    // JSON types
    val json: PgType<Json> get() = dev.typr.foundations.PgTypes.json
    val jsonb: PgType<Jsonb> get() = dev.typr.foundations.PgTypes.jsonb

    // Other types
    val xml: PgType<Xml> get() = dev.typr.foundations.PgTypes.xml
    val money: PgType<Money> get() = dev.typr.foundations.PgTypes.money
    val inet: PgType<Inet> get() = dev.typr.foundations.PgTypes.inet
    val vector: PgType<Vector> get() = dev.typr.foundations.PgTypes.vector
    val xid: PgType<Xid> get() = dev.typr.foundations.PgTypes.xid

    // Reg* types
    val regclass: PgType<Regclass> get() = dev.typr.foundations.PgTypes.regclass
    val regconfig: PgType<Regconfig> get() = dev.typr.foundations.PgTypes.regconfig
    val regdictionary: PgType<Regdictionary> get() = dev.typr.foundations.PgTypes.regdictionary
    val regnamespace: PgType<Regnamespace> get() = dev.typr.foundations.PgTypes.regnamespace
    val regoper: PgType<Regoper> get() = dev.typr.foundations.PgTypes.regoper
    val regoperator: PgType<Regoperator> get() = dev.typr.foundations.PgTypes.regoperator
    val regproc: PgType<Regproc> get() = dev.typr.foundations.PgTypes.regproc
    val regprocedure: PgType<Regprocedure> get() = dev.typr.foundations.PgTypes.regprocedure
    val regrole: PgType<Regrole> get() = dev.typr.foundations.PgTypes.regrole
    val regtype: PgType<Regtype> get() = dev.typr.foundations.PgTypes.regtype

    // Factory methods
    fun <E : Enum<E>> ofEnum(name: String, fromString: (String) -> E): PgType<E> {
        return dev.typr.foundations.PgTypes.ofEnum(name, fromString)
    }

    fun <T> ofPgObject(
        sqlType: String,
        constructor: SqlFunction<String, T>,
        extractor: (T) -> String,
        json: dev.typr.foundations.PgJson<T>
    ): PgType<T> {
        return dev.typr.foundations.PgTypes.ofPgObject(sqlType, constructor, extractor, json)
    }

    fun ofPgObject(sqlType: String): PgType<String> {
        return dev.typr.foundations.PgTypes.ofPgObject(
            sqlType,
            SqlFunction { it },
            { it },
            dev.typr.foundations.PgJson.text
        )
    }

    fun bpchar(precision: Int): PgType<String> {
        return dev.typr.foundations.PgTypes.bpchar(precision)
    }

    fun record(typename: String): PgType<dev.typr.foundations.data.Record> {
        return dev.typr.foundations.PgTypes.record(typename)
    }
}

// ================================
// Fragment - SQL fragment construction
// ================================

/**
 * Fragment factory methods.
 */
object Fragments {
    val EMPTY: Fragment get() = Fragment(dev.typr.foundations.Fragment.EMPTY)

    fun lit(value: String): Fragment {
        return Fragment(dev.typr.foundations.Fragment.lit(value))
    }

    fun empty(): Fragment {
        return Fragment(dev.typr.foundations.Fragment.empty())
    }

    fun quotedDouble(value: String): Fragment {
        return Fragment(dev.typr.foundations.Fragment.quotedDouble(value))
    }

    fun quotedSingle(value: String): Fragment {
        return Fragment(dev.typr.foundations.Fragment.quotedSingle(value))
    }

    fun <A> value(value: A, type: PgType<A>): Fragment {
        return Fragment(dev.typr.foundations.Fragment.value(value, type))
    }

    fun and(vararg fragments: Fragment): Fragment {
        return Fragment(dev.typr.foundations.Fragment.and(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun and(fragments: List<Fragment>): Fragment {
        return Fragment(dev.typr.foundations.Fragment.and(fragments.map { it.underlying }))
    }

    fun or(vararg fragments: Fragment): Fragment {
        return Fragment(dev.typr.foundations.Fragment.or(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun or(fragments: List<Fragment>): Fragment {
        return Fragment(dev.typr.foundations.Fragment.or(fragments.map { it.underlying }))
    }

    fun join(fragments: List<Fragment>, separator: Fragment): Fragment {
        return Fragment(dev.typr.foundations.Fragment.join(fragments.map { it.underlying }, separator.underlying))
    }

    fun interpolate(initial: String): Fragment.Builder {
        return Fragment.Builder(dev.typr.foundations.Fragment.interpolate(initial))
    }

    fun concat(vararg fragments: Fragment): Fragment {
        return Fragment(dev.typr.foundations.Fragment.concat(*fragments.map { it.underlying }.toTypedArray()))
    }
}

// ================================
// ResultSetParser - Result parsing
// ================================

/**
 * ResultSetParser factory methods.
 * These work with the underlying Java RowParser type.
 * For Kotlin RowParser wrappers, use instance methods like .first(), .all() instead.
 */
object Parsers {
    fun <Out> all(rowParser: dev.typr.foundations.RowParser<Out>): ResultSetParser<List<Out>> {
        val javaParser = dev.typr.foundations.ResultSetParser.All(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).toList() }
    }

    fun <Out> first(rowParser: dev.typr.foundations.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = dev.typr.foundations.ResultSetParser.First(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> maxOne(rowParser: dev.typr.foundations.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = dev.typr.foundations.ResultSetParser.MaxOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> exactlyOne(rowParser: dev.typr.foundations.RowParser<Out>): ResultSetParser<Out> {
        val javaParser = dev.typr.foundations.ResultSetParser.ExactlyOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }

    fun <Out> foreach(rowParser: dev.typr.foundations.RowParser<Out>, consumer: (Out) -> Unit): ResultSetParser<Unit> {
        val javaParser = dev.typr.foundations.ResultSetParser.Foreach(rowParser, consumer)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }
}
