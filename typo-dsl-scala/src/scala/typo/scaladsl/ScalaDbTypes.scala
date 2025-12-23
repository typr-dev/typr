package typo.scaladsl

import typo.runtime.{DuckDbType, MariaType, OracleType, PgType, SqlServerType}

import scala.jdk.CollectionConverters.*

/** Scala-friendly DbType instances that use Scala types instead of Java boxed types.
  */
object ScalaDbTypes {
  object DuckDbTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: DuckDbType[Byte] = typo.runtime.DuckDbTypes.tinyint.bimap(b => b, b => b)
    val smallint: DuckDbType[Short] = typo.runtime.DuckDbTypes.smallint.bimap(s => s, s => s)
    val integer: DuckDbType[Int] = typo.runtime.DuckDbTypes.integer.bimap(i => i, i => i)
    val bigint: DuckDbType[Long] = typo.runtime.DuckDbTypes.bigint.bimap(l => l, l => l)
    val float_ : DuckDbType[Float] = typo.runtime.DuckDbTypes.float_.bimap(f => f, f => f)
    val double_ : DuckDbType[Double] = typo.runtime.DuckDbTypes.double_.bimap(d => d, d => d)
    val boolean_ : DuckDbType[Boolean] = typo.runtime.DuckDbTypes.boolean_.bimap(b => b, b => b)
    val bool: DuckDbType[Boolean] = typo.runtime.DuckDbTypes.bool.bimap(b => b, b => b)

    // Unsigned integer types
    val utinyint: DuckDbType[Short] = typo.runtime.DuckDbTypes.utinyint.bimap(s => s, s => s)
    val usmallint: DuckDbType[Int] = typo.runtime.DuckDbTypes.usmallint.bimap(i => i, i => i)
    val uinteger: DuckDbType[Long] = typo.runtime.DuckDbTypes.uinteger.bimap(l => l, l => l)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: DuckDbType[BigDecimal] = typo.runtime.DuckDbTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: DuckDbType[BigDecimal] = typo.runtime.DuckDbTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Array types - convert Java boxed arrays to Scala primitive arrays
    val booleanArrayUnboxed: DuckDbType[Array[Boolean]] = typo.runtime.DuckDbTypes.booleanArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val tinyintArrayUnboxed: DuckDbType[Array[Byte]] = typo.runtime.DuckDbTypes.tinyintArray.bimap(
      arr => arr.map(_.byteValue()),
      arr => arr.map(java.lang.Byte.valueOf)
    )
    val smallintArrayUnboxed: DuckDbType[Array[Short]] = typo.runtime.DuckDbTypes.smallintArray.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val integerArrayUnboxed: DuckDbType[Array[Int]] = typo.runtime.DuckDbTypes.integerArray.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val bigintArrayUnboxed: DuckDbType[Array[Long]] = typo.runtime.DuckDbTypes.bigintArray.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val floatArrayUnboxed: DuckDbType[Array[Float]] = typo.runtime.DuckDbTypes.floatArray.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val doubleArrayUnboxed: DuckDbType[Array[Double]] = typo.runtime.DuckDbTypes.doubleArray.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
  }

  object PgTypes {
    // Primitives - convert Java boxed types to Scala native types
    val bool: PgType[Boolean] = typo.runtime.PgTypes.bool.bimap(b => b, b => b)
    val int2: PgType[Short] = typo.runtime.PgTypes.int2.bimap(s => s, s => s)
    val smallint: PgType[Short] = typo.runtime.PgTypes.smallint.bimap(s => s, s => s)
    val int4: PgType[Int] = typo.runtime.PgTypes.int4.bimap(i => i, i => i)
    val int8: PgType[Long] = typo.runtime.PgTypes.int8.bimap(l => l, l => l)
    val float4: PgType[Float] = typo.runtime.PgTypes.float4.bimap(f => f, f => f)
    val float8: PgType[Double] = typo.runtime.PgTypes.float8.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: PgType[BigDecimal] = typo.runtime.PgTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Collections - convert Java collections to Scala collections
    val hstore: PgType[Map[String, String]] = typo.runtime.PgTypes.hstore.bimap(javaMap => javaMap.asScala.toMap, scalaMap => scalaMap.asJava)

    // Array types - convert Java boxed arrays to Scala native arrays
    val boolArray: PgType[Array[Boolean]] = typo.runtime.PgTypes.boolArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val int2Array: PgType[Array[Short]] = typo.runtime.PgTypes.int2Array.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val smallintArray: PgType[Array[Short]] = int2Array
    val int4Array: PgType[Array[Int]] = typo.runtime.PgTypes.int4Array.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val int8Array: PgType[Array[Long]] = typo.runtime.PgTypes.int8Array.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val float4Array: PgType[Array[Float]] = typo.runtime.PgTypes.float4Array.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val float8Array: PgType[Array[Double]] = typo.runtime.PgTypes.float8Array.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
    val numericArray: PgType[Array[BigDecimal]] = typo.runtime.PgTypes.numericArray.bimap(
      arr => arr.map(BigDecimal(_)),
      arr => arr.map(_.bigDecimal)
    )
  }

  object MariaTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: MariaType[Byte] = typo.runtime.MariaTypes.tinyint.bimap(b => b, b => b)
    val smallint: MariaType[Short] = typo.runtime.MariaTypes.smallint.bimap(s => s, s => s)
    val mediumint: MariaType[Int] = typo.runtime.MariaTypes.mediumint.bimap(i => i, i => i)
    val int_ : MariaType[Int] = typo.runtime.MariaTypes.int_.bimap(i => i, i => i)
    val bigint: MariaType[Long] = typo.runtime.MariaTypes.bigint.bimap(l => l, l => l)

    // Unsigned integers
    val tinyintUnsigned: MariaType[Short] = typo.runtime.MariaTypes.tinyintUnsigned.bimap(s => s, s => s)
    val smallintUnsigned: MariaType[Int] = typo.runtime.MariaTypes.smallintUnsigned.bimap(i => i, i => i)
    val mediumintUnsigned: MariaType[Int] = typo.runtime.MariaTypes.mediumintUnsigned.bimap(i => i, i => i)
    val intUnsigned: MariaType[Long] = typo.runtime.MariaTypes.intUnsigned.bimap(l => l, l => l)

    // Floating point
    val float_ : MariaType[Float] = typo.runtime.MariaTypes.float_.bimap(f => f, f => f)
    val double_ : MariaType[Double] = typo.runtime.MariaTypes.double_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: MariaType[BigDecimal] = typo.runtime.MariaTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bool: MariaType[Boolean] = typo.runtime.MariaTypes.bool.bimap(b => b, b => b)
    val bit1: MariaType[Boolean] = typo.runtime.MariaTypes.bit1.bimap(b => b, b => b)
  }

  object OracleTypes {
    // BigDecimal - convert Java BigDecimal to Scala BigDecimal (Oracle NUMBER type)
    val number: OracleType[BigDecimal] = typo.runtime.OracleTypes.number.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Floating point primitives - no conversion needed
    val binaryFloat: OracleType[Float] = typo.runtime.OracleTypes.binaryFloat.bimap(f => f, f => f)
    val binaryDouble: OracleType[Double] = typo.runtime.OracleTypes.binaryDouble.bimap(d => d, d => d)
  }

  object SqlServerTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: SqlServerType[Short] = typo.runtime.SqlServerTypes.tinyint.bimap(s => s, s => s) // Unsigned!
    val smallint: SqlServerType[Short] = typo.runtime.SqlServerTypes.smallint.bimap(s => s, s => s)
    val int_ : SqlServerType[Int] = typo.runtime.SqlServerTypes.int_.bimap(i => i, i => i)
    val bigint: SqlServerType[Long] = typo.runtime.SqlServerTypes.bigint.bimap(l => l, l => l)

    // Floating point
    val real: SqlServerType[Float] = typo.runtime.SqlServerTypes.real.bimap(f => f, f => f)
    val float_ : SqlServerType[Double] = typo.runtime.SqlServerTypes.float_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: SqlServerType[BigDecimal] = typo.runtime.SqlServerTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: SqlServerType[BigDecimal] = typo.runtime.SqlServerTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val money: SqlServerType[BigDecimal] = typo.runtime.SqlServerTypes.money.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val smallmoney: SqlServerType[BigDecimal] = typo.runtime.SqlServerTypes.smallmoney.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bit: SqlServerType[Boolean] = typo.runtime.SqlServerTypes.bit.bimap(b => b, b => b)
  }
}
