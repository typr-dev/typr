package dev.typr.foundations.scala

import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

object StaticExports {

  // Type aliases for runtime types
  type Either[L, R] = dev.typr.foundations.Either[L, R]
  type And[T1, T2] = dev.typr.foundations.And[T1, T2]

  // PostgreSQL data wrapper types
  type Json = dev.typr.foundations.data.Json
  type Jsonb = dev.typr.foundations.data.Jsonb
  type Money = dev.typr.foundations.data.Money
  type Xml = dev.typr.foundations.data.Xml
  type Vector = dev.typr.foundations.data.Vector
  type Record = dev.typr.foundations.data.Record
  type Unknown = dev.typr.foundations.data.Unknown
  type Xid = dev.typr.foundations.data.Xid
  type Inet = dev.typr.foundations.data.Inet
  type AclItem = dev.typr.foundations.data.AclItem
  type AnyArray = dev.typr.foundations.data.AnyArray
  type Int2Vector = dev.typr.foundations.data.Int2Vector
  type OidVector = dev.typr.foundations.data.OidVector
  type PgNodeTree = dev.typr.foundations.data.PgNodeTree

  // Regclass types
  type Regclass = dev.typr.foundations.data.Regclass
  type Regconfig = dev.typr.foundations.data.Regconfig
  type Regdictionary = dev.typr.foundations.data.Regdictionary
  type Regnamespace = dev.typr.foundations.data.Regnamespace
  type Regoper = dev.typr.foundations.data.Regoper
  type Regoperator = dev.typr.foundations.data.Regoperator
  type Regproc = dev.typr.foundations.data.Regproc
  type Regprocedure = dev.typr.foundations.data.Regprocedure
  type Regrole = dev.typr.foundations.data.Regrole
  type Regtype = dev.typr.foundations.data.Regtype

  // Range types
  type Range[T <: Comparable[T]] = dev.typr.foundations.data.Range[T]
  type RangeBound[T <: Comparable[T]] = dev.typr.foundations.data.RangeBound[T]
  type RangeFinite[T <: Comparable[T]] = dev.typr.foundations.data.RangeFinite[T]

  // Array type
  type Arr[A] = dev.typr.foundations.data.Arr[A]

  // Core type system
  type PgType[A] = dev.typr.foundations.PgType[A]
  type PgTypename[A] = dev.typr.foundations.PgTypename[A]
  type PgRead[A] = dev.typr.foundations.PgRead[A]
  type PgWrite[A] = dev.typr.foundations.PgWrite[A]
  type PgText[A] = dev.typr.foundations.PgText[A]

  // Database access types
  type Transactor = dev.typr.foundations.Transactor

  // Functional interfaces
  type SqlFunction[T, R] = dev.typr.foundations.SqlFunction[T, R]
  type SqlConsumer[T] = dev.typr.foundations.SqlConsumer[T]
  type SqlBiConsumer[T1, T2] = dev.typr.foundations.SqlBiConsumer[T1, T2]

  // Utility
  type ByteArrays = dev.typr.foundations.internal.ByteArrays
  type ArrParser = dev.typr.foundations.ArrParser

  object Types {
    // Primitive types
    val bool: PgType[java.lang.Boolean] = dev.typr.foundations.PgTypes.bool
    val int2: PgType[java.lang.Short] = dev.typr.foundations.PgTypes.int2
    val int4: PgType[java.lang.Integer] = dev.typr.foundations.PgTypes.int4
    val int8: PgType[java.lang.Long] = dev.typr.foundations.PgTypes.int8
    val float4: PgType[java.lang.Float] = dev.typr.foundations.PgTypes.float4
    val float8: PgType[java.lang.Double] = dev.typr.foundations.PgTypes.float8
    val numeric: PgType[java.math.BigDecimal] = dev.typr.foundations.PgTypes.numeric
    val text: PgType[String] = dev.typr.foundations.PgTypes.text
    val bytea: PgType[Array[Byte]] = dev.typr.foundations.PgTypes.bytea
    val uuid: PgType[java.util.UUID] = dev.typr.foundations.PgTypes.uuid

    // Date/time types
    val date: PgType[java.time.LocalDate] = dev.typr.foundations.PgTypes.date
    val time: PgType[java.time.LocalTime] = dev.typr.foundations.PgTypes.time
    val timestamp: PgType[java.time.LocalDateTime] = dev.typr.foundations.PgTypes.timestamp
    val timestamptz: PgType[java.time.Instant] = dev.typr.foundations.PgTypes.timestamptz
    val interval: PgType[org.postgresql.util.PGInterval] = dev.typr.foundations.PgTypes.interval

    // JSON types
    val json: PgType[Json] = dev.typr.foundations.PgTypes.json
    val jsonb: PgType[Jsonb] = dev.typr.foundations.PgTypes.jsonb

    // Other types
    val xml: PgType[Xml] = dev.typr.foundations.PgTypes.xml
    val money: PgType[Money] = dev.typr.foundations.PgTypes.money
    val inet: PgType[Inet] = dev.typr.foundations.PgTypes.inet
    val vector: PgType[Vector] = dev.typr.foundations.PgTypes.vector
    val xid: PgType[Xid] = dev.typr.foundations.PgTypes.xid

    // Reg* types
    val regclass: PgType[Regclass] = dev.typr.foundations.PgTypes.regclass
    val regconfig: PgType[Regconfig] = dev.typr.foundations.PgTypes.regconfig
    val regdictionary: PgType[Regdictionary] = dev.typr.foundations.PgTypes.regdictionary
    val regnamespace: PgType[Regnamespace] = dev.typr.foundations.PgTypes.regnamespace
    val regoper: PgType[Regoper] = dev.typr.foundations.PgTypes.regoper
    val regoperator: PgType[Regoperator] = dev.typr.foundations.PgTypes.regoperator
    val regproc: PgType[Regproc] = dev.typr.foundations.PgTypes.regproc
    val regprocedure: PgType[Regprocedure] = dev.typr.foundations.PgTypes.regprocedure
    val regrole: PgType[Regrole] = dev.typr.foundations.PgTypes.regrole
    val regtype: PgType[Regtype] = dev.typr.foundations.PgTypes.regtype

    // Factory methods
    def ofEnum[E <: Enum[E]](name: String, fromString: String => E): PgType[E] = {
      dev.typr.foundations.PgTypes.ofEnum(name, s => fromString(s))
    }

    def ofPgObject[T](
        sqlType: String,
        constructor: String => T,
        extractor: T => String,
        json: dev.typr.foundations.PgJson[T]
    ): PgType[T] = {
      dev.typr.foundations.PgTypes.ofPgObject(sqlType, s => constructor(s), t => extractor(t), json)
    }

    def bpchar(precision: Int): PgType[String] = {
      dev.typr.foundations.PgTypes.bpchar(precision)
    }

    def record(typename: String): PgType[Record] = {
      dev.typr.foundations.PgTypes.record(typename)
    }
  }

  // Export Fragment and Operation wrappers
  export dev.typr.foundations.scala.Fragment
  export dev.typr.foundations.scala.Fragment.{sql as _, *}
  export dev.typr.foundations.scala.Operation

  object Parsers {
    def all[Out](rowParser: dev.typr.foundations.RowParser[Out]): dev.typr.foundations.scala.ResultSetParser[List[Out]] = {
      val javaParser = new dev.typr.foundations.ResultSetParser.All(rowParser)
      new dev.typr.foundations.scala.ResultSetParser(new dev.typr.foundations.ResultSetParser[List[Out]] {
        override def apply(rs: java.sql.ResultSet): List[Out] = javaParser.apply(rs).asScala.toList
      })
    }

    def first[Out](rowParser: dev.typr.foundations.RowParser[Out]): dev.typr.foundations.scala.ResultSetParser[Option[Out]] = {
      val javaParser = new dev.typr.foundations.ResultSetParser.First(rowParser)
      new dev.typr.foundations.scala.ResultSetParser(new dev.typr.foundations.ResultSetParser[Option[Out]] {
        override def apply(rs: java.sql.ResultSet): Option[Out] = javaParser.apply(rs).toScala
      })
    }

    def maxOne[Out](rowParser: dev.typr.foundations.RowParser[Out]): dev.typr.foundations.scala.ResultSetParser[Option[Out]] = {
      val javaParser = new dev.typr.foundations.ResultSetParser.MaxOne(rowParser)
      new dev.typr.foundations.scala.ResultSetParser(new dev.typr.foundations.ResultSetParser[Option[Out]] {
        override def apply(rs: java.sql.ResultSet): Option[Out] = javaParser.apply(rs).toScala
      })
    }

    def exactlyOne[Out](rowParser: dev.typr.foundations.RowParser[Out]): dev.typr.foundations.scala.ResultSetParser[Out] = {
      val javaParser = new dev.typr.foundations.ResultSetParser.ExactlyOne(rowParser)
      new dev.typr.foundations.scala.ResultSetParser(javaParser)
    }

    def foreach[Out](rowParser: dev.typr.foundations.RowParser[Out], consumer: Out => Unit): dev.typr.foundations.scala.ResultSetParser[Unit] = {
      val foreachParser = new dev.typr.foundations.ResultSetParser.Foreach(rowParser, c => consumer(c))
      // Wrap Void-returning parser to return Unit
      val unitParser = new dev.typr.foundations.ResultSetParser[Unit] {
        override def apply(rs: java.sql.ResultSet): Unit = {
          foreachParser.apply(rs)
          ()
        }
      }
      new dev.typr.foundations.scala.ResultSetParser(unitParser)
    }
  }
}
