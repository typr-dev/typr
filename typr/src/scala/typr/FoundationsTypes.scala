package typr

import typr.jvm.Type.Qualified

object FoundationsTypes {
  private val dataPackage = "dev.typr.foundations.data"
  private val internalPackage = "dev.typr.foundations.internal"

  object unsigned {
    val Uint1: Qualified = Qualified(s"$dataPackage.Uint1")
    val Uint2: Qualified = Qualified(s"$dataPackage.Uint2")
    val Uint4: Qualified = Qualified(s"$dataPackage.Uint4")
    val Uint8: Qualified = Qualified(s"$dataPackage.Uint8")
  }

  object runtime {
    val AclItem: Qualified = Qualified(s"$dataPackage.AclItem")
    val AnyArray: Qualified = Qualified(s"$dataPackage.AnyArray")
    val Arr: Qualified = Qualified(s"$dataPackage.Arr")
    val Cidr: Qualified = Qualified(s"$dataPackage.Cidr")
    val Inet: Qualified = Qualified(s"$dataPackage.Inet")
    val MacAddr: Qualified = Qualified(s"$dataPackage.MacAddr")
    val MacAddr8: Qualified = Qualified(s"$dataPackage.MacAddr8")
    val Int2Vector: Qualified = Qualified(s"$dataPackage.Int2Vector")
    val Json: Qualified = Qualified(s"$dataPackage.Json")
    val Jsonb: Qualified = Qualified(s"$dataPackage.Jsonb")
    val Money: Qualified = Qualified(s"$dataPackage.Money")
    val OidVector: Qualified = Qualified(s"$dataPackage.OidVector")
    val OracleIntervalDS: Qualified = Qualified(s"$dataPackage.OracleIntervalDS")
    val OracleIntervalYM: Qualified = Qualified(s"$dataPackage.OracleIntervalYM")
    val PgNodeTree: Qualified = Qualified(s"$dataPackage.PgNodeTree")
    val Record: Qualified = Qualified(s"$dataPackage.Record")
    val Regclass: Qualified = Qualified(s"$dataPackage.Regclass")
    val Regconfig: Qualified = Qualified(s"$dataPackage.Regconfig")
    val Regdictionary: Qualified = Qualified(s"$dataPackage.Regdictionary")
    val Regnamespace: Qualified = Qualified(s"$dataPackage.Regnamespace")
    val Regoper: Qualified = Qualified(s"$dataPackage.Regoper")
    val Regoperator: Qualified = Qualified(s"$dataPackage.Regoperator")
    val Regproc: Qualified = Qualified(s"$dataPackage.Regproc")
    val Regprocedure: Qualified = Qualified(s"$dataPackage.Regprocedure")
    val Regrole: Qualified = Qualified(s"$dataPackage.Regrole")
    val Regtype: Qualified = Qualified(s"$dataPackage.Regtype")
    val HierarchyId: Qualified = Qualified(s"$dataPackage.HierarchyId")
    val Vector: Qualified = Qualified(s"$dataPackage.Vector")
    val Unknown: Qualified = Qualified(s"$dataPackage.Unknown")
    val Xid: Qualified = Qualified(s"$dataPackage.Xid")
    val Xml: Qualified = Qualified(s"$dataPackage.Xml")
  }

  object maria {
    val Inet4: Qualified = Qualified(s"$dataPackage.maria.Inet4")
    val Inet6: Qualified = Qualified(s"$dataPackage.maria.Inet6")
    val MariaSet: Qualified = Qualified(s"$dataPackage.maria.MariaSet")
  }

  object internal {
    val ByteArrays: Qualified = Qualified(s"$internalPackage.ByteArrays")
    val RandomHelper: Qualified = Qualified(s"$internalPackage.RandomHelper")
    val TypoPGObjectHelper: Qualified = Qualified(s"$internalPackage.TypoPGObjectHelper")
    val arrayMap: Qualified = Qualified(s"$internalPackage.arrayMap")
  }

  object scala {
    private val scalaPackage = "dev.typr.foundations.scala"
    val ScalaIteratorOps: Qualified = Qualified(s"$scalaPackage.ScalaIteratorOps")
    val ScalaDbTypeOps: Qualified = Qualified(s"$scalaPackage.DbTypeOps")
    val OperationListOps: Qualified = Qualified(s"$scalaPackage.OperationListOps")
    val OperationOptionalToOptionOps: Qualified = Qualified(s"$scalaPackage.OperationOptionalToOptionOps")
    val ScalaDbTypes: Qualified = Qualified(s"$scalaPackage.ScalaDbTypes")
  }

  object kotlin {
    private val kotlinPackage = "dev.typr.foundations.kotlin"
    val KotlinDbTypes: Qualified = Qualified(s"$kotlinPackage.KotlinDbTypes")
    val KotlinNullableExtension: Qualified = Qualified(s"$kotlinPackage.nullable")
    val KotlinQueryExtension: Qualified = Qualified(s"$kotlinPackage.query")
  }

  val streamingInsert: Qualified = Qualified("dev.typr.foundations.streamingInsert")
  val Inserter: Qualified = Qualified("dev.typr.foundations.Inserter")
  val DbText: Qualified = Qualified("dev.typr.foundations.DbText")

  def RowParsersFunctionN(n: Int): Qualified = Qualified(s"dev.typr.foundations.RowParsers.Function$n")

  val TupleN: Int => Qualified = n => Qualified(s"dev.typr.foundations.Tuple.Tuple$n")

  object oracle {
    val OracleType: Qualified = Qualified("dev.typr.foundations.OracleType")
    val OracleObject: Qualified = Qualified("dev.typr.foundations.OracleObject")
    val OracleVArray: Qualified = Qualified("dev.typr.foundations.OracleVArray")
    val OracleNestedTable: Qualified = Qualified("dev.typr.foundations.OracleNestedTable")
  }

  object duckdb {
    val DuckDbStruct: Qualified = Qualified("dev.typr.foundations.DuckDbStruct")
    val DuckDbType: Qualified = Qualified("dev.typr.foundations.DuckDbType")
    val DuckDbTypes: Qualified = Qualified("dev.typr.foundations.DuckDbTypes")
  }

  object precise {
    private val precisePackage = s"$dataPackage.precise"
    val StringN: Qualified = Qualified(s"$precisePackage.StringN")
    val PaddedStringN: Qualified = Qualified(s"$precisePackage.PaddedStringN")
    val NonEmptyStringN: Qualified = Qualified(s"$precisePackage.NonEmptyStringN")
    val NonEmptyPaddedStringN: Qualified = Qualified(s"$precisePackage.NonEmptyPaddedStringN")
    val BinaryN: Qualified = Qualified(s"$precisePackage.BinaryN")
    val DecimalN: Qualified = Qualified(s"$precisePackage.DecimalN")
    val LocalDateTimeN: Qualified = Qualified(s"$precisePackage.LocalDateTimeN")
    val LocalTimeN: Qualified = Qualified(s"$precisePackage.LocalTimeN")
    val OffsetDateTimeN: Qualified = Qualified(s"$precisePackage.OffsetDateTimeN")
    val InstantN: Qualified = Qualified(s"$precisePackage.InstantN")
  }
}
