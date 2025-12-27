package dev.typr.foundations.scala

import dev.typr.foundations.{Db2Type, DuckDbType, MariaType, OracleType, PgType, SqlServerType}

import _root_.scala.jdk.CollectionConverters.*

/** Scala-friendly DbType instances that use Scala types instead of Java boxed types.
  */
object ScalaDbTypes {
  object DuckDbTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: DuckDbType[Byte] = dev.typr.foundations.DuckDbTypes.tinyint.bimap(b => b, b => b)
    val smallint: DuckDbType[Short] = dev.typr.foundations.DuckDbTypes.smallint.bimap(s => s, s => s)
    val integer: DuckDbType[Int] = dev.typr.foundations.DuckDbTypes.integer.bimap(i => i, i => i)
    val bigint: DuckDbType[Long] = dev.typr.foundations.DuckDbTypes.bigint.bimap(l => l, l => l)
    val float_ : DuckDbType[Float] = dev.typr.foundations.DuckDbTypes.float_.bimap(f => f, f => f)
    val double_ : DuckDbType[Double] = dev.typr.foundations.DuckDbTypes.double_.bimap(d => d, d => d)
    val boolean_ : DuckDbType[Boolean] = dev.typr.foundations.DuckDbTypes.boolean_.bimap(b => b, b => b)
    val bool: DuckDbType[Boolean] = dev.typr.foundations.DuckDbTypes.bool.bimap(b => b, b => b)

    // Unsigned integer types
    val utinyint: DuckDbType[Short] = dev.typr.foundations.DuckDbTypes.utinyint.bimap(s => s, s => s)
    val usmallint: DuckDbType[Int] = dev.typr.foundations.DuckDbTypes.usmallint.bimap(i => i, i => i)
    val uinteger: DuckDbType[Long] = dev.typr.foundations.DuckDbTypes.uinteger.bimap(l => l, l => l)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: DuckDbType[BigDecimal] = dev.typr.foundations.DuckDbTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: DuckDbType[BigDecimal] = dev.typr.foundations.DuckDbTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Array types - convert Java boxed arrays to Scala primitive arrays
    val booleanArrayUnboxed: DuckDbType[Array[Boolean]] = dev.typr.foundations.DuckDbTypes.booleanArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val tinyintArrayUnboxed: DuckDbType[Array[Byte]] = dev.typr.foundations.DuckDbTypes.tinyintArray.bimap(
      arr => arr.map(_.byteValue()),
      arr => arr.map(java.lang.Byte.valueOf)
    )
    val smallintArrayUnboxed: DuckDbType[Array[Short]] = dev.typr.foundations.DuckDbTypes.smallintArray.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val integerArrayUnboxed: DuckDbType[Array[Int]] = dev.typr.foundations.DuckDbTypes.integerArray.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val bigintArrayUnboxed: DuckDbType[Array[Long]] = dev.typr.foundations.DuckDbTypes.bigintArray.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val floatArrayUnboxed: DuckDbType[Array[Float]] = dev.typr.foundations.DuckDbTypes.floatArray.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val doubleArrayUnboxed: DuckDbType[Array[Double]] = dev.typr.foundations.DuckDbTypes.doubleArray.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
  }

  object PgTypes {
    // Primitives - convert Java boxed types to Scala native types
    val bool: PgType[Boolean] = dev.typr.foundations.PgTypes.bool.bimap(b => b, b => b)
    val int2: PgType[Short] = dev.typr.foundations.PgTypes.int2.bimap(s => s, s => s)
    val smallint: PgType[Short] = dev.typr.foundations.PgTypes.smallint.bimap(s => s, s => s)
    val int4: PgType[Int] = dev.typr.foundations.PgTypes.int4.bimap(i => i, i => i)
    val int8: PgType[Long] = dev.typr.foundations.PgTypes.int8.bimap(l => l, l => l)
    val float4: PgType[Float] = dev.typr.foundations.PgTypes.float4.bimap(f => f, f => f)
    val float8: PgType[Double] = dev.typr.foundations.PgTypes.float8.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: PgType[BigDecimal] = dev.typr.foundations.PgTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Collections - convert Java collections to Scala collections
    val hstore: PgType[Map[String, String]] = dev.typr.foundations.PgTypes.hstore.bimap(javaMap => javaMap.asScala.toMap, scalaMap => scalaMap.asJava)

    // Array types - convert Java boxed arrays to Scala native arrays
    val boolArray: PgType[Array[Boolean]] = dev.typr.foundations.PgTypes.boolArray.bimap(
      arr => arr.map(_.booleanValue()),
      arr => arr.map(java.lang.Boolean.valueOf)
    )
    val int2Array: PgType[Array[Short]] = dev.typr.foundations.PgTypes.int2Array.bimap(
      arr => arr.map(_.shortValue()),
      arr => arr.map(java.lang.Short.valueOf)
    )
    val smallintArray: PgType[Array[Short]] = int2Array
    val int4Array: PgType[Array[Int]] = dev.typr.foundations.PgTypes.int4Array.bimap(
      arr => arr.map(_.intValue()),
      arr => arr.map(java.lang.Integer.valueOf)
    )
    val int8Array: PgType[Array[Long]] = dev.typr.foundations.PgTypes.int8Array.bimap(
      arr => arr.map(_.longValue()),
      arr => arr.map(java.lang.Long.valueOf)
    )
    val float4Array: PgType[Array[Float]] = dev.typr.foundations.PgTypes.float4Array.bimap(
      arr => arr.map(_.floatValue()),
      arr => arr.map(java.lang.Float.valueOf)
    )
    val float8Array: PgType[Array[Double]] = dev.typr.foundations.PgTypes.float8Array.bimap(
      arr => arr.map(_.doubleValue()),
      arr => arr.map(java.lang.Double.valueOf)
    )
    val numericArray: PgType[Array[BigDecimal]] = dev.typr.foundations.PgTypes.numericArray.bimap(
      arr => arr.map(BigDecimal(_)),
      arr => arr.map(_.bigDecimal)
    )
  }

  object MariaTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: MariaType[Byte] = dev.typr.foundations.MariaTypes.tinyint.bimap(b => b, b => b)
    val smallint: MariaType[Short] = dev.typr.foundations.MariaTypes.smallint.bimap(s => s, s => s)
    val mediumint: MariaType[Int] = dev.typr.foundations.MariaTypes.mediumint.bimap(i => i, i => i)
    val int_ : MariaType[Int] = dev.typr.foundations.MariaTypes.int_.bimap(i => i, i => i)
    val bigint: MariaType[Long] = dev.typr.foundations.MariaTypes.bigint.bimap(l => l, l => l)

    // Unsigned integers
    val tinyintUnsigned: MariaType[Short] = dev.typr.foundations.MariaTypes.tinyintUnsigned.bimap(s => s, s => s)
    val smallintUnsigned: MariaType[Int] = dev.typr.foundations.MariaTypes.smallintUnsigned.bimap(i => i, i => i)
    val mediumintUnsigned: MariaType[Int] = dev.typr.foundations.MariaTypes.mediumintUnsigned.bimap(i => i, i => i)
    val intUnsigned: MariaType[Long] = dev.typr.foundations.MariaTypes.intUnsigned.bimap(l => l, l => l)

    // Floating point
    val float_ : MariaType[Float] = dev.typr.foundations.MariaTypes.float_.bimap(f => f, f => f)
    val double_ : MariaType[Double] = dev.typr.foundations.MariaTypes.double_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val numeric: MariaType[BigDecimal] = dev.typr.foundations.MariaTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bool: MariaType[Boolean] = dev.typr.foundations.MariaTypes.bool.bimap(b => b, b => b)
    val bit1: MariaType[Boolean] = dev.typr.foundations.MariaTypes.bit1.bimap(b => b, b => b)
  }

  object OracleTypes {
    // BigDecimal - convert Java BigDecimal to Scala BigDecimal (Oracle NUMBER type)
    val number: OracleType[BigDecimal] = dev.typr.foundations.OracleTypes.number.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Floating point primitives - no conversion needed
    val binaryFloat: OracleType[Float] = dev.typr.foundations.OracleTypes.binaryFloat.bimap(f => f, f => f)
    val binaryDouble: OracleType[Double] = dev.typr.foundations.OracleTypes.binaryDouble.bimap(d => d, d => d)
  }

  object SqlServerTypes {
    // Primitives - convert Java boxed types to Scala native types
    val tinyint: SqlServerType[Short] = dev.typr.foundations.SqlServerTypes.tinyint.bimap(s => s, s => s) // Unsigned!
    val smallint: SqlServerType[Short] = dev.typr.foundations.SqlServerTypes.smallint.bimap(s => s, s => s)
    val int_ : SqlServerType[Int] = dev.typr.foundations.SqlServerTypes.int_.bimap(i => i, i => i)
    val bigint: SqlServerType[Long] = dev.typr.foundations.SqlServerTypes.bigint.bimap(l => l, l => l)

    // Floating point
    val real: SqlServerType[Float] = dev.typr.foundations.SqlServerTypes.real.bimap(f => f, f => f)
    val float_ : SqlServerType[Double] = dev.typr.foundations.SqlServerTypes.float_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: SqlServerType[BigDecimal] = dev.typr.foundations.SqlServerTypes.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: SqlServerType[BigDecimal] = dev.typr.foundations.SqlServerTypes.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val money: SqlServerType[BigDecimal] = dev.typr.foundations.SqlServerTypes.money.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val smallmoney: SqlServerType[BigDecimal] = dev.typr.foundations.SqlServerTypes.smallmoney.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val bit: SqlServerType[Boolean] = dev.typr.foundations.SqlServerTypes.bit.bimap(b => b, b => b)
  }

  object Db2Types {
    // Primitives - convert Java boxed types to Scala native types
    val smallint: Db2Type[Short] = dev.typr.foundations.Db2Types.smallint.bimap(s => s, s => s)
    val integer: Db2Type[Int] = dev.typr.foundations.Db2Types.integer.bimap(i => i, i => i)
    val bigint: Db2Type[Long] = dev.typr.foundations.Db2Types.bigint.bimap(l => l, l => l)

    // Floating point
    val real: Db2Type[Float] = dev.typr.foundations.Db2Types.real.bimap(f => f, f => f)
    val double_ : Db2Type[Double] = dev.typr.foundations.Db2Types.double_.bimap(d => d, d => d)

    // BigDecimal - convert Java BigDecimal to Scala BigDecimal
    val decimal: Db2Type[BigDecimal] = dev.typr.foundations.Db2Types.decimal.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val numeric: Db2Type[BigDecimal] = dev.typr.foundations.Db2Types.numeric.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)
    val decfloat: Db2Type[BigDecimal] = dev.typr.foundations.Db2Types.decfloat.bimap(jbd => BigDecimal(jbd), sbd => sbd.bigDecimal)

    // Boolean
    val boolean_ : Db2Type[Boolean] = dev.typr.foundations.Db2Types.boolean_.bimap(b => b, b => b)
  }
}
