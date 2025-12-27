package dev.typr.foundations.kotlin

import dev.typr.foundations.Db2Type
import dev.typr.foundations.DuckDbType
import dev.typr.foundations.MariaType
import dev.typr.foundations.OracleType
import dev.typr.foundations.PgType
import dev.typr.foundations.SqlFunction
import dev.typr.foundations.SqlServerType

/**
 * Kotlin-friendly DbType instances that use Kotlin types instead of Java boxed types.
 */
object KotlinDbTypes {
    object PgTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val bool: PgType<Boolean> = dev.typr.foundations.PgTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
        val int2: PgType<Short> = dev.typr.foundations.PgTypes.int2.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: PgType<Short> = dev.typr.foundations.PgTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val int4: PgType<Int> = dev.typr.foundations.PgTypes.int4.bimap(
            SqlFunction { it },
            { it }
        )
        val int8: PgType<Long> = dev.typr.foundations.PgTypes.int8.bimap(
            SqlFunction { it },
            { it }
        )
        val float4: PgType<Float> = dev.typr.foundations.PgTypes.float4.bimap(
            SqlFunction { it },
            { it }
        )
        val float8: PgType<Double> = dev.typr.foundations.PgTypes.float8.bimap(
            SqlFunction { it },
            { it }
        )

        // Collections - convert Java collections to Kotlin collections
        val hstore: PgType<Map<String, String>> = dev.typr.foundations.PgTypes.hstore.bimap(
            SqlFunction { javaMap -> javaMap.toMap() },
            { kotlinMap -> kotlinMap.toMap(java.util.HashMap()) }
        )
    }

    object MariaTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val tinyint: MariaType<Byte> = dev.typr.foundations.MariaTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: MariaType<Short> = dev.typr.foundations.MariaTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val mediumint: MariaType<Int> = dev.typr.foundations.MariaTypes.mediumint.bimap(
            SqlFunction { it },
            { it }
        )
        val int_: MariaType<Int> = dev.typr.foundations.MariaTypes.int_.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: MariaType<Long> = dev.typr.foundations.MariaTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Unsigned integers
        val tinyintUnsigned: MariaType<Short> = dev.typr.foundations.MariaTypes.tinyintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val smallintUnsigned: MariaType<Int> = dev.typr.foundations.MariaTypes.smallintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val mediumintUnsigned: MariaType<Int> = dev.typr.foundations.MariaTypes.mediumintUnsigned.bimap(
            SqlFunction { it },
            { it }
        )
        val intUnsigned: MariaType<Long> = dev.typr.foundations.MariaTypes.intUnsigned.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val float_: MariaType<Float> = dev.typr.foundations.MariaTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
        val double_: MariaType<Double> = dev.typr.foundations.MariaTypes.double_.bimap(
            SqlFunction { it },
            { it }
        )

        // Decimal/Numeric
        val numeric: MariaType<java.math.BigDecimal> = dev.typr.foundations.MariaTypes.numeric

        // Boolean
        val bool: MariaType<Boolean> = dev.typr.foundations.MariaTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
        val bit1: MariaType<Boolean> = dev.typr.foundations.MariaTypes.bit1.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object DuckDbTypes {
        // Signed integers
        val tinyint: DuckDbType<Byte> = dev.typr.foundations.DuckDbTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: DuckDbType<Short> = dev.typr.foundations.DuckDbTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val integer: DuckDbType<Int> = dev.typr.foundations.DuckDbTypes.integer.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: DuckDbType<Long> = dev.typr.foundations.DuckDbTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Unsigned integers
        val utinyint: DuckDbType<Short> = dev.typr.foundations.DuckDbTypes.utinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val usmallint: DuckDbType<Int> = dev.typr.foundations.DuckDbTypes.usmallint.bimap(
            SqlFunction { it },
            { it }
        )
        val uinteger: DuckDbType<Long> = dev.typr.foundations.DuckDbTypes.uinteger.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val float_: DuckDbType<Float> = dev.typr.foundations.DuckDbTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
        val double_: DuckDbType<Double> = dev.typr.foundations.DuckDbTypes.double_.bimap(
            SqlFunction { it },
            { it }
        )

        // Boolean
        val boolean_: DuckDbType<Boolean> = dev.typr.foundations.DuckDbTypes.boolean_.bimap(
            SqlFunction { it },
            { it }
        )
        val bool: DuckDbType<Boolean> = dev.typr.foundations.DuckDbTypes.bool.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object OracleTypes {
        // Numeric types - NUMBER is Oracle's universal numeric type
        val number: OracleType<java.math.BigDecimal> = dev.typr.foundations.OracleTypes.number

        val numberInt: OracleType<Int> = dev.typr.foundations.OracleTypes.numberInt.bimap(
            SqlFunction { it },
            { it }
        )

        val numberLong: OracleType<Long> = dev.typr.foundations.OracleTypes.numberLong.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val binaryFloat: OracleType<Float> = dev.typr.foundations.OracleTypes.binaryFloat.bimap(
            SqlFunction { it },
            { it }
        )

        val binaryDouble: OracleType<Double> = dev.typr.foundations.OracleTypes.binaryDouble.bimap(
            SqlFunction { it },
            { it }
        )

        val float_: OracleType<Double> = dev.typr.foundations.OracleTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )
    }

    object SqlServerTypes {
        // Primitives - convert Java boxed types to Kotlin native types
        val tinyint: SqlServerType<Short> = dev.typr.foundations.SqlServerTypes.tinyint.bimap(
            SqlFunction { it },
            { it }
        )
        val smallint: SqlServerType<Short> = dev.typr.foundations.SqlServerTypes.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val int_: SqlServerType<Int> = dev.typr.foundations.SqlServerTypes.int_.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: SqlServerType<Long> = dev.typr.foundations.SqlServerTypes.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val real: SqlServerType<Float> = dev.typr.foundations.SqlServerTypes.real.bimap(
            SqlFunction { it },
            { it }
        )
        val float_: SqlServerType<Double> = dev.typr.foundations.SqlServerTypes.float_.bimap(
            SqlFunction { it },
            { it }
        )

        // Boolean
        val bit: SqlServerType<Boolean> = dev.typr.foundations.SqlServerTypes.bit.bimap(
            SqlFunction { it },
            { it }
        )

        // Decimal types - no conversion needed, already BigDecimal
        val decimal = dev.typr.foundations.SqlServerTypes.decimal
        val numeric = dev.typr.foundations.SqlServerTypes.numeric
        val money = dev.typr.foundations.SqlServerTypes.money
        val smallmoney = dev.typr.foundations.SqlServerTypes.smallmoney
    }

    object Db2Types {
        // Primitives - convert Java boxed types to Kotlin native types
        val smallint: Db2Type<Short> = dev.typr.foundations.Db2Types.smallint.bimap(
            SqlFunction { it },
            { it }
        )
        val integer: Db2Type<Int> = dev.typr.foundations.Db2Types.integer.bimap(
            SqlFunction { it },
            { it }
        )
        val bigint: Db2Type<Long> = dev.typr.foundations.Db2Types.bigint.bimap(
            SqlFunction { it },
            { it }
        )

        // Floating point
        val real: Db2Type<Float> = dev.typr.foundations.Db2Types.real.bimap(
            SqlFunction { it },
            { it }
        )
        val double_: Db2Type<Double> = dev.typr.foundations.Db2Types.double_.bimap(
            SqlFunction { it },
            { it }
        )

        // Boolean
        val boolean_: Db2Type<Boolean> = dev.typr.foundations.Db2Types.boolean_.bimap(
            SqlFunction { it },
            { it }
        )

        // Decimal types - no conversion needed, already BigDecimal
        val decimal = dev.typr.foundations.Db2Types.decimal
        val numeric = dev.typr.foundations.Db2Types.numeric
        val decfloat = dev.typr.foundations.Db2Types.decfloat
    }
}
