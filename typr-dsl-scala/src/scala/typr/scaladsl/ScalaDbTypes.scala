package typr.scaladsl

import typr.runtime.{DuckDbType, MariaType, OracleType, PgType, SqlServerType}

import scala.jdk.CollectionConverters.*

/** Scala-friendly DbType instances that use Scala types instead of Java boxed types.
  */
object ScalaDbTypes {
  object DuckDbTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: DuckDbType[Byte] = typr.runtime.DuckDbTypes.tinyint.bimap(b => b, b => b)
    val smallint: DuckDbType[Short] = typr.runtime.DuckDbTypes.smallint.bimap(s => s, s => s)
    val integer: DuckDbType[Int] = typr.runtime.DuckDbTypes.integer.bimap(i => i, i => i)
    val bigint: DuckDbType[Long] = typr.runtime.DuckDbTypes.bigint.bimap(l => l, l => l)
    val float_ : DuckDbType[Float] = typr.runtime.DuckDbTypes.float_.bimap(f => f, f => f)
    val double_ : DuckDbType[Double] = typr.runtime.DuckDbTypes.double_.bimap(d => d, d => d)
    val boolean_ : DuckDbType[Boolean] = typr.runtime.DuckDbTypes.boolean_.bimap(b => b, b => b)
    val bool: DuckDbType[Boolean] = typr.runtime.DuckDbTypes.bool.bimap(b => b, b => b)

    // Unsigned integer types
    val utinyint: DuckDbType[Short] = typr.runtime.DuckDbTypes.utinyint.bimap(s => s, s => s)
    val usmallint: DuckDbType[Int] = typr.runtime.DuckDbTypes.usmallint.bimap(i => i, i => i)
    val uinteger: DuckDbType[Long] = typr.runtime.DuckDbTypes.uinteger.bimap(l => l, l => l)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: DuckDbType[BigDecimal] = typr.runtime.DuckDbTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: DuckDbType[BigDecimal] = typr.runtime.DuckDbTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Array types - convert Java boxed arrays to Scala primitive arrays
    val booleanArrayUnboxed: DuckDbType[Array[Boolean]] = typr.runtime.DuckDbTypes.booleanArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val tinyintArrayUnboxed: DuckDbType[Array[Byte]] = typr.runtime.DuckDbTypes.tinyintArray.bimap(
      arr => arr.map(_.byteValue()),
      arr => arr.map(java.lang.Byte.valueOf)
    )
    val smallintArrayUnboxed: DuckDbType[Array[Short]] = typr.runtime.DuckDbTypes.smallintArray.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val integerArrayUnboxed: DuckDbType[Array[Int]] = typr.runtime.DuckDbTypes.integerArray.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val bigintArrayUnboxed: DuckDbType[Array[Long]] = typr.runtime.DuckDbTypes.bigintArray.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val floatArrayUnboxed: DuckDbType[Array[Float]] = typr.runtime.DuckDbTypes.floatArray.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val doubleArrayUnboxed: DuckDbType[Array[Double]] = typr.runtime.DuckDbTypes.doubleArray.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
  }

  object PgTypes {
    // Primitives - convert Java boxed types to Scala native types
    val bool: PgType[Boolean] = typr.runtime.PgTypes.bool.bimap(b => b, b => b)
    val int2: PgType[Short] = typr.runtime.PgTypes.int2.bimap(s => s, s => s)
    val smallint: PgType[Short] = typr.runtime.PgTypes.smallint.bimap(s => s, s => s)
    val int4: PgType[Int] = typr.runtime.PgTypes.int4.bimap(i => i, i => i)
    val int8: PgType[Long] = typr.runtime.PgTypes.int8.bimap(l => l, l => l)
    val float4: PgType[Float] = typr.runtime.PgTypes.float4.bimap(f => f, f => f)
    val float8: PgType[Double] = typr.runtime.PgTypes.float8.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: PgType[BigDecimal] = typr.runtime.PgTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Collections - convert Java collections to Scala collections
    val hstore: PgType[Map[String, String]] = typr.runtime.PgTypes.hstore.bimap(javaMap => javaMap.asScala.toMap, scalaMap => scalaMap.asJava)

    // Array types - convert Java boxed arrays to Scala native arrays
    val boolArray: PgType[Array[Boolean]] = typr.runtime.PgTypes.boolArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val int2Array: PgType[Array[Short]] = typr.runtime.PgTypes.int2Array.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val smallintArray: PgType[Array[Short]] = int2Array
    val int4Array: PgType[Array[Int]] = typr.runtime.PgTypes.int4Array.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val int8Array: PgType[Array[Long]] = typr.runtime.PgTypes.int8Array.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val float4Array: PgType[Array[Float]] = typr.runtime.PgTypes.float4Array.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val float8Array: PgType[Array[Double]] = typr.runtime.PgTypes.float8Array.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
    val numericArray: PgType[Array[BigDecimal]] = typr.runtime.PgTypes.numericArray.bimap(
      arr => arr.map(BigDecimal(_)),
      arr => arr.map(_.bigDecimal)
    )
  }

  object MariaTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: MariaType[Byte] = typr.runtime.MariaTypes.tinyint.bimap(b => b, b => b)
    val smallint: MariaType[Short] = typr.runtime.MariaTypes.smallint.bimap(s => s, s => s)
    val mediumint: MariaType[Int] = typr.runtime.MariaTypes.mediumint.bimap(i => i, i => i)
    val int_ : MariaType[Int] = typr.runtime.MariaTypes.int_.bimap(i => i, i => i)
    val bigint: MariaType[Long] = typr.runtime.MariaTypes.bigint.bimap(l => l, l => l)

    // Unsigned integers
    val tinyintUnsigned: MariaType[Short] = typr.runtime.MariaTypes.tinyintUnsigned.bimap(s => s, s => s)
    val smallintUnsigned: MariaType[Int] = typr.runtime.MariaTypes.smallintUnsigned.bimap(i => i, i => i)
    val mediumintUnsigned: MariaType[Int] = typr.runtime.MariaTypes.mediumintUnsigned.bimap(i => i, i => i)
    val intUnsigned: MariaType[Long] = typr.runtime.MariaTypes.intUnsigned.bimap(l => l, l => l)

    // Floating point
    val float_ : MariaType[Float] = typr.runtime.MariaTypes.float_.bimap(f => f, f => f)
    val double_ : MariaType[Double] = typr.runtime.MariaTypes.double_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: MariaType[BigDecimal] = typr.runtime.MariaTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bool: MariaType[Boolean] = typr.runtime.MariaTypes.bool.bimap(b => b, b => b)
    val bit1: MariaType[Boolean] = typr.runtime.MariaTypes.bit1.bimap(b => b, b => b)
  }

  object OracleTypes {
    // BigDecimal - convert Java BigDecimal to Scala BigDecimal (Oracle NUMBER type)
    val number: OracleType[BigDecimal] = typr.runtime.OracleTypes.number.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Floating point primitives - no conversion needed
    val binaryFloat: OracleType[Float] = typr.runtime.OracleTypes.binaryFloat.bimap(f => f, f => f)
    val binaryDouble: OracleType[Double] = typr.runtime.OracleTypes.binaryDouble.bimap(d => d, d => d)
  }

  object SqlServerTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: SqlServerType[Short] = typr.runtime.SqlServerTypes.tinyint.bimap(s => s, s => s) // Unsigned!
    val smallint: SqlServerType[Short] = typr.runtime.SqlServerTypes.smallint.bimap(s => s, s => s)
    val int_ : SqlServerType[Int] = typr.runtime.SqlServerTypes.int_.bimap(i => i, i => i)
    val bigint: SqlServerType[Long] = typr.runtime.SqlServerTypes.bigint.bimap(l => l, l => l)

    // Floating point
    val real: SqlServerType[Float] = typr.runtime.SqlServerTypes.real.bimap(f => f, f => f)
    val float_ : SqlServerType[Double] = typr.runtime.SqlServerTypes.float_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: SqlServerType[BigDecimal] = typr.runtime.SqlServerTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: SqlServerType[BigDecimal] = typr.runtime.SqlServerTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val money: SqlServerType[BigDecimal] = typr.runtime.SqlServerTypes.money.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val smallmoney: SqlServerType[BigDecimal] = typr.runtime.SqlServerTypes.smallmoney.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bit: SqlServerType[Boolean] = typr.runtime.SqlServerTypes.bit.bimap(b => b, b => b)
  }
}
