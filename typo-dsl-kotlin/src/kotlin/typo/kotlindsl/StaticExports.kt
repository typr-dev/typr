package typo.kotlindsl

import typo.runtime.SqlFunction

/**
 * Kotlin-friendly exports of static members from Java classes.
 * Type aliases don't export static methods, so we need object wrappers.
 */

// ================================
// PgTypes - Main type registry
// ================================

/**
 * Access to all PostgreSQL type descriptors.
 * Delegates to typo.runtime.PgTypes.
 */
object Types {
    // Primitive types
    val bool: PgType<Boolean> get() = typo.runtime.PgTypes.bool
    val int2: PgType<Short> get() = typo.runtime.PgTypes.int2
    val int4: PgType<Int> get() = typo.runtime.PgTypes.int4
    val int8: PgType<Long> get() = typo.runtime.PgTypes.int8
    val float4: PgType<Float> get() = typo.runtime.PgTypes.float4
    val float8: PgType<Double> get() = typo.runtime.PgTypes.float8
    val numeric: PgType<java.math.BigDecimal> get() = typo.runtime.PgTypes.numeric
    val text: PgType<String> get() = typo.runtime.PgTypes.text
    val bytea: PgType<ByteArray> get() = typo.runtime.PgTypes.bytea
    val uuid: PgType<java.util.UUID> get() = typo.runtime.PgTypes.uuid

    // Date/time types (using java.time)
    val date: PgType<java.time.LocalDate> get() = typo.runtime.PgTypes.date
    val time: PgType<java.time.LocalTime> get() = typo.runtime.PgTypes.time
    val timestamp: PgType<java.time.LocalDateTime> get() = typo.runtime.PgTypes.timestamp
    val timestamptz: PgType<java.time.Instant> get() = typo.runtime.PgTypes.timestamptz
    val interval: PgType<org.postgresql.util.PGInterval> get() = typo.runtime.PgTypes.interval

    // JSON types
    val json: PgType<Json> get() = typo.runtime.PgTypes.json
    val jsonb: PgType<Jsonb> get() = typo.runtime.PgTypes.jsonb

    // Other types
    val xml: PgType<Xml> get() = typo.runtime.PgTypes.xml
    val money: PgType<Money> get() = typo.runtime.PgTypes.money
    val inet: PgType<Inet> get() = typo.runtime.PgTypes.inet
    val vector: PgType<Vector> get() = typo.runtime.PgTypes.vector
    val xid: PgType<Xid> get() = typo.runtime.PgTypes.xid

    // Reg* types
    val regclass: PgType<Regclass> get() = typo.runtime.PgTypes.regclass
    val regconfig: PgType<Regconfig> get() = typo.runtime.PgTypes.regconfig
    val regdictionary: PgType<Regdictionary> get() = typo.runtime.PgTypes.regdictionary
    val regnamespace: PgType<Regnamespace> get() = typo.runtime.PgTypes.regnamespace
    val regoper: PgType<Regoper> get() = typo.runtime.PgTypes.regoper
    val regoperator: PgType<Regoperator> get() = typo.runtime.PgTypes.regoperator
    val regproc: PgType<Regproc> get() = typo.runtime.PgTypes.regproc
    val regprocedure: PgType<Regprocedure> get() = typo.runtime.PgTypes.regprocedure
    val regrole: PgType<Regrole> get() = typo.runtime.PgTypes.regrole
    val regtype: PgType<Regtype> get() = typo.runtime.PgTypes.regtype

    // Factory methods
    fun <E : Enum<E>> ofEnum(name: String, fromString: (String) -> E): PgType<E> {
        return typo.runtime.PgTypes.ofEnum(name, fromString)
    }

    fun <T> ofPgObject(
        sqlType: String,
        constructor: SqlFunction<String, T>,
        extractor: (T) -> String,
        json: typo.runtime.PgJson<T>
    ): PgType<T> {
        return typo.runtime.PgTypes.ofPgObject(sqlType, constructor, extractor, json)
    }

    fun ofPgObject(sqlType: String): PgType<String> {
        return typo.runtime.PgTypes.ofPgObject(
            sqlType,
            SqlFunction { it },
            { it },
            typo.runtime.PgJson.text
        )
    }

    fun bpchar(precision: Int): PgType<String> {
        return typo.runtime.PgTypes.bpchar(precision)
    }

    fun record(typename: String): PgType<typo.data.Record> {
        return typo.runtime.PgTypes.record(typename)
    }
}

// ================================
// Fragment - SQL fragment construction
// ================================

/**
 * Fragment factory methods.
 */
object Fragments {
    val EMPTY: Fragment get() = Fragment(typo.runtime.Fragment.EMPTY)

    fun lit(value: String): Fragment {
        return Fragment(typo.runtime.Fragment.lit(value))
    }

    fun empty(): Fragment {
        return Fragment(typo.runtime.Fragment.empty())
    }

    fun quotedDouble(value: String): Fragment {
        return Fragment(typo.runtime.Fragment.quotedDouble(value))
    }

    fun quotedSingle(value: String): Fragment {
        return Fragment(typo.runtime.Fragment.quotedSingle(value))
    }

    fun <A> value(value: A, type: PgType<A>): Fragment {
        return Fragment(typo.runtime.Fragment.value(value, type))
    }

    fun and(vararg fragments: Fragment): Fragment {
        return Fragment(typo.runtime.Fragment.and(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun and(fragments: List<Fragment>): Fragment {
        return Fragment(typo.runtime.Fragment.and(fragments.map { it.underlying }))
    }

    fun or(vararg fragments: Fragment): Fragment {
        return Fragment(typo.runtime.Fragment.or(*fragments.map { it.underlying }.toTypedArray()))
    }

    fun or(fragments: List<Fragment>): Fragment {
        return Fragment(typo.runtime.Fragment.or(fragments.map { it.underlying }))
    }

    fun join(fragments: List<Fragment>, separator: Fragment): Fragment {
        return Fragment(typo.runtime.Fragment.join(fragments.map { it.underlying }, separator.underlying))
    }

    fun interpolate(initial: String): Fragment.Builder {
        return Fragment.Builder(typo.runtime.Fragment.interpolate(initial))
    }

    fun concat(vararg fragments: Fragment): Fragment {
        return Fragment(typo.runtime.Fragment.concat(*fragments.map { it.underlying }.toTypedArray()))
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
    fun <Out> all(rowParser: typo.runtime.RowParser<Out>): ResultSetParser<List<Out>> {
        val javaParser = typo.runtime.ResultSetParser.All(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).toList() }
    }

    fun <Out> first(rowParser: typo.runtime.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = typo.runtime.ResultSetParser.First(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> maxOne(rowParser: typo.runtime.RowParser<Out>): ResultSetParser<Out?> {
        val javaParser = typo.runtime.ResultSetParser.MaxOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs).orNull() }
    }

    fun <Out> exactlyOne(rowParser: typo.runtime.RowParser<Out>): ResultSetParser<Out> {
        val javaParser = typo.runtime.ResultSetParser.ExactlyOne(rowParser)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }

    fun <Out> foreach(rowParser: typo.runtime.RowParser<Out>, consumer: (Out) -> Unit): ResultSetParser<Unit> {
        val javaParser = typo.runtime.ResultSetParser.Foreach(rowParser, consumer)
        return ResultSetParser { rs -> javaParser.apply(rs) }
    }
}
