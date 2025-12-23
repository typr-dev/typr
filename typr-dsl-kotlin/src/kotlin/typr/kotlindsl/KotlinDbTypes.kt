package typr.kotlindsl

import typr.runtime.DuckDbType
import typr.runtime.MariaType
import typr.runtime.OracleType
import typr.runtime.PgType
import typr.runtime.SqlFunction
import typr.runtime.SqlServerType

/**
 * Kotlin-friendly DbType instances that use Kotlin types instead of Java boxed types.
 */
object KotlinDbTypes {
    object PgTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val bool: PgType<Boolean> = typr.runtime.PgTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
        val int2: PgType<Short> = typr.runtime.PgTypes.int2.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: PgType<Short> = typr.runtime.PgTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val int4: PgType<Int> = typr.runtime.PgTypes.int4.bimap(
            SqlFunction { it },
            { it }
        )
        val int8: PgType<Long> = typr.runtime.PgTypes.int8.bimap(
            SqlFunction { it },
            { it }
        )
        val float4: PgType<Float> = typr.runtime.PgTypes.float4.bimap(
            SqlFunction { it },
            { it }
        )
        val float8: PgType<Double> = typr.runtime.PgTypes.float8.bimap(
            SqlFunction { it },
            { it }
        )

        // Collections - convert Java collections to Kotlin collections
        val hstore: PgType<Map<String, String>> = typr.runtime.PgTypes.hstore.bimap(
            SqlFunction { javaMap -> javaMap.toMap() },
            { kotlinMap -> kotlinMap.toMap(java.util.HashMap()) }
        )
    }

    object MariaTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val tinyint: MariaType<Byte> = typr.runtime.MariaTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: MariaType<Short> = typr.runtime.MariaTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val mediumint: MariaType<Int> = typr.runtime.MariaTypes.mediumint.bimap(
            SqlFunction { it },
            { it }
        )
        val int_: MariaType<Int> = typr.runtime.MariaTypes.int_.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: MariaType<Long> = typr.runtime.MariaTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Unsigned integers
        val tinyintUnsigned: MariaType<Short> = typr.runtime.MariaTypes.tinyintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val smallintUnsigned: MariaType<Int> = typr.runtime.MariaTypes.smallintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val mediumintUnsigned: MariaType<Int> = typr.runtime.MariaTypes.mediumintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val intUnsigned: MariaType<Long> = typr.runtime.MariaTypes.intUnsigned.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val float_: MariaType<Float> = typr.runtime.MariaTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
        val double_: MariaType<Double> = typr.runtime.MariaTypes.double_.bimap(
            SqlFunction { it },
            { it }
        )

        // Decimal/Numeric
        val numeric: MariaType<java.math.BigDecimal> = typr.runtime.MariaTypes.numeric

        // Boolean
        val bool: MariaType<Boolean> = typr.runtime.MariaTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
        val bit1: MariaType<Boolean> = typr.runtime.MariaTypes.bit1.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object DuckDbTypes {
        // Signed integers
        val tinyint: DuckDbType<Byte> = typr.runtime.DuckDbTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: DuckDbType<Short> = typr.runtime.DuckDbTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val integer: DuckDbType<Int> = typr.runtime.DuckDbTypes.integer.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: DuckDbType<Long> = typr.runtime.DuckDbTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Unsigned integers
        val utinyint: DuckDbType<Short> = typr.runtime.DuckDbTypes.utinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val usmallint: DuckDbType<Int> = typr.runtime.DuckDbTypes.usmallint.bimap(
            SqlFunction { it },
            { it }
        )
        val uinteger: DuckDbType<Long> = typr.runtime.DuckDbTypes.uinteger.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val float_: DuckDbType<Float> = typr.runtime.DuckDbTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
        val double_: DuckDbType<Double> = typr.runtime.DuckDbTypes.double_.bimap(
            SqlFunction { it },
            { it }
        )

        // Boolean
        val boolean_: DuckDbType<Boolean> = typr.runtime.DuckDbTypes.boolean_.bimap(
            SqlFunction { it },
            { it }
        )
        val bool: DuckDbType<Boolean> = typr.runtime.DuckDbTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object OracleTypes {
        // Numeric types - NUMBER is Oracle's universal numeric type
        val number: OracleType<java.math.BigDecimal> = typr.runtime.OracleTypes.number

        val numberInt: OracleType<Int> = typr.runtime.OracleTypes.numberInt.bimap(
            SqlFunction { it },
            { it }
        )

        val numberLong: OracleType<Long> = typr.runtime.OracleTypes.numberLong.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val binaryFloat: OracleType<Float> = typr.runtime.OracleTypes.binaryFloat.bimap(
            SqlFunction { it },
            { it }
        )

        val binaryDouble: OracleType<Double> = typr.runtime.OracleTypes.binaryDouble.bimap(
            SqlFunction { it },
            { it }
        )

        val float_: OracleType<Double> = typr.runtime.OracleTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object SqlServerTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val tinyint: SqlServerType<Short> = typr.runtime.SqlServerTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: SqlServerType<Short> = typr.runtime.SqlServerTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val int_: SqlServerType<Int> = typr.runtime.SqlServerTypes.int_.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: SqlServerType<Long> = typr.runtime.SqlServerTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val real: SqlServerType<Float> = typr.runtime.SqlServerTypes.real.bimap(
            SqlFunction { it },
            { it }
        )
        val float_: SqlServerType<Double> = typr.runtime.SqlServerTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )

        // Boolean
        val bit: SqlServerType<Boolean> = typr.runtime.SqlServerTypes.bit.bimap(
            SqlFunction { it },
            { it }
        )

        // Decimal types - no conversion needed, already BigDecimal
        val decimal = typr.runtime.SqlServerTypes.decimal
        val numeric = typr.runtime.SqlServerTypes.numeric
        val money = typr.runtime.SqlServerTypes.money
        val smallmoney = typr.runtime.SqlServerTypes.smallmoney
    }
}
