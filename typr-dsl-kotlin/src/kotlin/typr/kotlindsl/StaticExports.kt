package typr.kotlindsl

import typr.runtime.SqlFunction

/**
 * Kotlin-friendly exports of static members from Java classes.
 * Type aliases don't export static methods, so we need object wrappers.
 */

// ================================
// PgTypes - Main type registry
// ================================

/**
 * Access to all PostgreSQL type descriptors.
 * Delegates to typr.runtime.PgTypes.
 */
object Types {
    // Primitive types
    val bool: PgType<Boolean> get() = typr.runtime.PgTypes.bool
    val int2: PgType<Short> get() = typr.runtime.PgTypes.int2
    val int4: PgType<Int> get() = typr.runtime.PgTypes.int4
    val int8: PgType<Long> get() = typr.runtime.PgTypes.int8
    val float4: PgType<Float> get() = typr.runtime.PgTypes.float4
    val float8: PgType<Double> get() = typr.runtime.PgTypes.float8
    val numeric: PgType<java.math.BigDecimal> get() = typr.runtime.PgTypes.numeric
    val text: PgType<String> get() = typr.runtime.PgTypes.text
    val bytea: PgType<ByteArray> get() = typr.runtime.PgTypes.bytea
    val uuid: PgType<java.util.UUID> get() = typr.runtime.PgTypes.uuid

    // Date/time types (using java.time)
    val date: PgType<java.time.LocalDate> get() = typr.runtime.PgTypes.date
    val time: PgType<java.time.LocalTime> get() = typr.runtime.PgTypes.time
    val timestamp: PgType<java.time.LocalDateTime> get() = typr.runtime.PgTypes.timestamp
    val timestamptz: PgType<java.time.Instant> get() = typr.runtime.PgTypes.timestamptz
    val interval: PgType<org.postgresql.util.PGInterval> get() = typr.runtime.PgTypes.interval

    // JSON types
    val json: PgType<Json> get() = typr.runtime.PgTypes.json
    val jsonb: PgType<Jsonb> get() = typr.runtime.PgTypes.jsonb

    // Other types
    val xml: PgType<Xml> get() = typr.runtime.PgTypes.xml
    val money: PgType<Money> get() = typr.runtime.PgTypes.money
    val inet: PgType<Inet> get() = typr.runtime.PgTypes.inet
    val vector: PgType<Vector> get() = typr.runtime.PgTypes.vector
    val xid: PgType<Xid> get() = typr.runtime.PgTypes.xid

    // Reg* types
    val regclass: PgType<Regclass> get() = typr.runtime.PgTypes.regclass
    val regconfig: PgType<Regconfig> get() = typr.runtime.PgTypes.regconfig
    val regdictionary: PgType<Regdictionary> get() = typr.runtime.PgTypes.regdictionary
    val regnamespace: PgType<Regnamespace> get() = typr.runtime.PgTypes.regnamespace
    val regoper: PgType<Regoper> get() = typr.runtime.PgTypes.regoper
    val regoperator: PgType<Regoperator> get() = typr.runtime.PgTypes.regoperator
    val regproc: PgType<Regproc> get() = typr.runtime.PgTypes.regproc
    val regprocedure: PgType<Regprocedure> get() = typr.runtime.PgTypes.regprocedure
    val regrole: PgType<Regrole> get() = typr.runtime.PgTypes.regrole
    val regtype: PgType<Regtype> get() = typr.runtime.PgTypes.regtype

    // Factory methods
    fun <E : Enum<E>> ofEnum(name: String, fromString: (String) -> E): PgType<E> {
        return typr.runtime.PgTypes.ofEnum(name, fromString)
    }

    fun <T> ofPgObject(
        sqlType: String,
        constructor: SqlFunction<String, T>,
        extractor: (T) -> String,
        json: typr.runtime.PgJson<T>
    ): PgType<T> {
        return typr.runtime.PgTypes.ofPgObject(sqlType, constructor, extractor, json)
    }

    fun ofPgObject(sqlType: String): PgType<String> {
        return typr.runtime.PgTypes.ofPgObject(
            sqlType,
            SqlFunction { it },
            { it },
            typr.runtime.PgJson.text
        )
    }

    fun bpchar(precision: Int): PgType<String> {
        return typr.runtime.PgTypes.bpchar(precision)
    }

    fun record(typename: String): PgType<typr.data.Record> {
        return typr.runtime.PgTypes.record(typename)
    }
}

// ================================
// Fragment - SQL fragment construction
// ================================

/**
 * Fragment factory methods.
 */
object Fragments {
    val EMPTY: Fragment get() = Fragment(typr.runtime.Fragment.EMPTY)

    fun lit(value: String): Fragment {
        return Fragment(typr.runtime.Fragment.lit(value))
    }

    fun empty(): Fragment {
        return Fragment(typr.runtime.Fragment.empty())
    }

    fun quotedDouble(value: String): Fragment {
        return Fragment(typr.runtime.Fragment.quotedDouble(value))
    }

    fun quotedSingle(value: String): Fragment {
        return Fragment(typr.runtime.Fragment.quotedSingle(value))
    }

    fun <A> value(value: A, type: PgType<A>): Fragment {
        return Fragment(typr.runtime.Fragment.value(value, type))
    }

    fun and(vararg fragments: Fragment): Fragment {
        return Fragment(typr.runtime.Fragment.and(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun and(fragments: List<Fragment>): Fragment {
        return Fragment(typr.runtime.Fragment.and(fragments.map { it.underlying }))
    }

    fun or(vararg fragments: Fragment): Fragment {
        return Fragment(typr.runtime.Fragment.or(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun or(fragments: List<Fragment>): Fragment {
        return Fragment(typr.runtime.Fragment.or(fragments.map { it.underlying }))
    }

    fun join(fragments: List<Fragment>, separator: Fragment): Fragment {
        return Fragment(typr.runtime.Fragment.join(fragments.map { it.underlying }, separator.underlying))
    }

    fun interpolate(initial: String): Fragment.Builder {
        return Fragment.Builder(typr.runtime.Fragment.interpolate(initial))
    }

    fun concat(vararg fragments: Fragment): Fragment {
        return Fragment(typr.runtime.Fragment.concat(*fragments.map { it.underlying }.toTypedArray()))
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
    fun <Out> all(rowParser: typr.runtime.RowParser<Out>): ResultSetParser<List<Out>> {
        val javaParser = typr.runtime.ResultSetParser.All(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).toList() }
    }

    fun <Out> first(rowParser: typr.runtime.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = typr.runtime.ResultSetParser.First(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> maxOne(rowParser: typr.runtime.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = typr.runtime.ResultSetParser.MaxOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> exactlyOne(rowParser: typr.runtime.RowParser<Out>): ResultSetParser<Out> {
        val javaParser = typr.runtime.ResultSetParser.ExactlyOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }

    fun <Out> foreach(rowParser: typr.runtime.RowParser<Out>, consumer: (Out) -> Unit): ResultSetParser<Unit> {
        val javaParser = typr.runtime.ResultSetParser.Foreach(rowParser, consumer)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }
}
