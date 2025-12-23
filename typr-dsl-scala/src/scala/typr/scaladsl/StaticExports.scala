package typr.scaladsl

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object StaticExports {

  // Type aliases for runtime types
  type Either[L, R] = typr.runtime.Either[L, R]
  type And[T1, T2] = typr.runtime.And[T1, T2]

  // PostgreSQL data wrapper types
  type Json = typr.data.Json
  type Jsonb = typr.data.Jsonb
  type Money = typr.data.Money
  type Xml = typr.data.Xml
  type Vector = typr.data.Vector
  type Record = typr.data.Record
  type Unknown = typr.data.Unknown
  type Xid = typr.data.Xid
  type Inet = typr.data.Inet
  type AclItem = typr.data.AclItem
  type AnyArray = typr.data.AnyArray
  type Int2Vector = typr.data.Int2Vector
  type OidVector = typr.data.OidVector
  type PgNodeTree = typr.data.PgNodeTree

  // Regclass types
  type Regclass = typr.data.Regclass
  type Regconfig = typr.data.Regconfig
  type Regdictionary = typr.data.Regdictionary
  type Regnamespace = typr.data.Regnamespace
  type Regoper = typr.data.Regoper
  type Regoperator = typr.data.Regoperator
  type Regproc = typr.data.Regproc
  type Regprocedure = typr.data.Regprocedure
  type Regrole = typr.data.Regrole
  type Regtype = typr.data.Regtype

  // Range types
  type Range[T <: Comparable[T]] = typr.data.Range[T]
  type RangeBound[T <: Comparable[T]] = typr.data.RangeBound[T]
  type RangeFinite[T <: Comparable[T]] = typr.data.RangeFinite[T]

  // Array type
  type Arr[A] = typr.data.Arr[A]

  // Core type system
  type PgType[A] = typr.runtime.PgType[A]
  type PgTypename[A] = typr.runtime.PgTypename[A]
  type PgRead[A] = typr.runtime.PgRead[A]
  type PgWrite[A] = typr.runtime.PgWrite[A]
  type PgText[A] = typr.runtime.PgText[A]

  // Database access types
  type Transactor = typr.runtime.Transactor

  // Functional interfaces
  type SqlFunction[T, R] = typr.runtime.SqlFunction[T, R]
  type SqlConsumer[T] = typr.runtime.SqlConsumer[T]
  type SqlBiConsumer[T1, T2] = typr.runtime.SqlBiConsumer[T1, T2]

  // Utility
  type ByteArrays = typr.runtime.internal.ByteArrays
  type ArrParser = typr.runtime.ArrParser

  object Types {
    // Primitive types
    val bool: PgType[java.lang.Boolean] = typr.runtime.PgTypes.bool
    val int2: PgType[java.lang.Short] = typr.runtime.PgTypes.int2
    val int4: PgType[java.lang.Integer] = typr.runtime.PgTypes.int4
    val int8: PgType[java.lang.Long] = typr.runtime.PgTypes.int8
    val float4: PgType[java.lang.Float] = typr.runtime.PgTypes.float4
    val float8: PgType[java.lang.Double] = typr.runtime.PgTypes.float8
    val numeric: PgType[java.math.BigDecimal] = typr.runtime.PgTypes.numeric
    val text: PgType[String] = typr.runtime.PgTypes.text
    val bytea: PgType[Array[Byte]] = typr.runtime.PgTypes.bytea
    val uuid: PgType[java.util.UUID] = typr.runtime.PgTypes.uuid

    // Date/time types
    val date: PgType[java.time.LocalDate] = typr.runtime.PgTypes.date
    val time: PgType[java.time.LocalTime] = typr.runtime.PgTypes.time
    val timestamp: PgType[java.time.LocalDateTime] = typr.runtime.PgTypes.timestamp
    val timestamptz: PgType[java.time.Instant] = typr.runtime.PgTypes.timestamptz
    val interval: PgType[org.postgresql.util.PGInterval] = typr.runtime.PgTypes.interval

    // JSON types
    val json: PgType[Json] = typr.runtime.PgTypes.json
    val jsonb: PgType[Jsonb] = typr.runtime.PgTypes.jsonb

    // Other types
    val xml: PgType[Xml] = typr.runtime.PgTypes.xml
    val money: PgType[Money] = typr.runtime.PgTypes.money
    val inet: PgType[Inet] = typr.runtime.PgTypes.inet
    val vector: PgType[Vector] = typr.runtime.PgTypes.vector
    val xid: PgType[Xid] = typr.runtime.PgTypes.xid

    // Reg* types
    val regclass: PgType[Regclass] = typr.runtime.PgTypes.regclass
    val regconfig: PgType[Regconfig] = typr.runtime.PgTypes.regconfig
    val regdictionary: PgType[Regdictionary] = typr.runtime.PgTypes.regdictionary
    val regnamespace: PgType[Regnamespace] = typr.runtime.PgTypes.regnamespace
    val regoper: PgType[Regoper] = typr.runtime.PgTypes.regoper
    val regoperator: PgType[Regoperator] = typr.runtime.PgTypes.regoperator
    val regproc: PgType[Regproc] = typr.runtime.PgTypes.regproc
    val regprocedure: PgType[Regprocedure] = typr.runtime.PgTypes.regprocedure
    val regrole: PgType[Regrole] = typr.runtime.PgTypes.regrole
    val regtype: PgType[Regtype] = typr.runtime.PgTypes.regtype

    // Factory methods
    def ofEnum[E <: Enum[E]](name: String, fromString: String => E): PgType[E] = {
      typr.runtime.PgTypes.ofEnum(name, s => fromString(s))
    }

    def ofPgObject[T](
        sqlType: String,
        constructor: String => T,
        extractor: T => String,
        json: typr.runtime.PgJson[T]
    ): PgType[T] = {
      typr.runtime.PgTypes.ofPgObject(sqlType, s => constructor(s), t => extractor(t), json)
    }

    def bpchar(precision: Int): PgType[String] = {
      typr.runtime.PgTypes.bpchar(precision)
    }

    def record(typename: String): PgType[Record] = {
      typr.runtime.PgTypes.record(typename)
    }
  }

  // Export Fragment and Operation wrappers
  export typr.scaladsl.Fragment
  export typr.scaladsl.Fragment.{sql as _, *}
  export typr.scaladsl.Operation

  object Parsers {
    def all[Out](rowParser: typr.runtime.RowParser[Out]): typr.scaladsl.ResultSetParser[List[Out]] = {
      val javaParser = new typr.runtime.ResultSetParser.All(rowParser)
      new typr.scaladsl.ResultSetParser(new typr.runtime.ResultSetParser[List[Out]] {
        override def apply(rs: java.sql.ResultSet): List[Out] = javaParser.apply(rs).asScala.toList
      })
    }

    def first[Out](rowParser: typr.runtime.RowParser[Out]): typr.scaladsl.ResultSetParser[Option[Out]] = {
      val javaParser = new typr.runtime.ResultSetParser.First(rowParser)
      new typr.scaladsl.ResultSetParser(new typr.runtime.ResultSetParser[Option[Out]] {
        override def apply(rs: java.sql.ResultSet): Option[Out] = javaParser.apply(rs).toScala
      })
    }

    def maxOne[Out](rowParser: typr.runtime.RowParser[Out]): typr.scaladsl.ResultSetParser[Option[Out]] = {
      val javaParser = new typr.runtime.ResultSetParser.MaxOne(rowParser)
      new typr.scaladsl.ResultSetParser(new typr.runtime.ResultSetParser[Option[Out]] {
        override def apply(rs: java.sql.ResultSet): Option[Out] = javaParser.apply(rs).toScala
      })
    }

    def exactlyOne[Out](rowParser: typr.runtime.RowParser[Out]): typr.scaladsl.ResultSetParser[Out] = {
      val javaParser = new typr.runtime.ResultSetParser.ExactlyOne(rowParser)
      new typr.scaladsl.ResultSetParser(javaParser)
    }

    def foreach[Out](rowParser: typr.runtime.RowParser[Out], consumer: Out => Unit): typr.scaladsl.ResultSetParser[Unit] = {
      val foreachParser = new typr.runtime.ResultSetParser.Foreach(rowParser, c => consumer(c))
      // Wrap Void-returning parser to return Unit
      val unitParser = new typr.runtime.ResultSetParser[Unit] {
        override def apply(rs: java.sql.ResultSet): Unit = {
          foreachParser.apply(rs)
          ()
        }
      }
      new typr.scaladsl.ResultSetParser(unitParser)
    }
  }
}
