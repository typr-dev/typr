package typr

sealed abstract class DslQualifiedNames(val dslPackage: String) {
  val Bijection: jvm.Type.Qualified
  val ConstAs = jvm.Type.Qualified(s"$dslPackage.SqlExpr.Const.As")
  val ConstAsAs = ConstAs / jvm.Ident("as")
  val ConstAsAsOpt = ConstAs / jvm.Ident("asOpt")
  val DeleteBuilder = jvm.Type.Qualified(s"$dslPackage.DeleteBuilder")
  val DeleteBuilderMock = jvm.Type.Qualified(s"$dslPackage.DeleteBuilderMock")
  val DeleteParams = jvm.Type.Qualified(s"$dslPackage.DeleteParams")
  val Dialect = jvm.Type.Qualified(s"$dslPackage.Dialect")
  val Field = jvm.Type.Qualified(s"$dslPackage.SqlExpr.Field")
  val FieldLikeNoHkt = jvm.Type.Qualified(s"$dslPackage.SqlExpr.FieldLike")
  val ForeignKey = jvm.Type.Qualified(s"$dslPackage.ForeignKey")
  val Fragment: jvm.Type.Qualified
  val IdField = jvm.Type.Qualified(s"$dslPackage.SqlExpr.IdField")
  val In = jvm.Type.Qualified(s"$dslPackage.SqlExpr.In")
  val OptField = jvm.Type.Qualified(s"$dslPackage.SqlExpr.OptField")
  val Path = jvm.Type.Qualified(s"$dslPackage.Path")
  val RowCodec: jvm.Type.Qualified
  val RowCodecs: jvm.Type.Qualified
  val Rows = jvm.Type.Qualified(s"$dslPackage.SqlExpr.Rows")
  val SelectBuilder = jvm.Type.Qualified(s"$dslPackage.SelectBuilder")
  val SelectBuilderMock = jvm.Type.Qualified(s"$dslPackage.SelectBuilderMock")
  val SelectParams = jvm.Type.Qualified(s"$dslPackage.SelectParams")
  val SqlExpr: jvm.Type.Qualified
  val StructureRelation = jvm.Type.Qualified(s"$dslPackage.RelationStructure")
  val TupleExpr: jvm.Type.Qualified
  val Tuples: jvm.Type.Qualified
  val UpdateBuilder = jvm.Type.Qualified(s"$dslPackage.UpdateBuilder")
  val UpdateBuilderMock = jvm.Type.Qualified(s"$dslPackage.UpdateBuilderMock")
  val UpdateParams = jvm.Type.Qualified(s"$dslPackage.UpdateParams")

  /** The name of the string interpolator used for SQL fragments in this DSL */
  val interpolatorName: String
}

sealed abstract class DslQualifiedNamesFoundations(dslPackage: String, val foundationsPackage: String) extends DslQualifiedNames(dslPackage) {
  def TupleExprN(n: Int): jvm.Type.Qualified
  def FieldsExpr(rowType: jvm.Type): jvm.Type
  val FieldsBase: jvm.Type.Qualified
  val FieldLikeJava: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.dsl.SqlExpr.FieldLike")
  val RowCodecJava: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.RowCodec")

  /** Foundations runtime classes — each language has its own wrapper package. */
  val Connection: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.Connection")
  val ConnectionRead: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.ConnectionRead")
  val Transactor: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.Transactor")
  val Operation: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.Operation")
  val OperationRead: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.OperationRead")
  val RowCodecNamed: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.RowCodecNamed")
  val StreamingInsert: jvm.Type.Qualified = jvm.Type.Qualified(s"$foundationsPackage.StreamingInsert")
}

object DslQualifiedNames {
  case object Scala extends DslQualifiedNamesFoundations("dev.typr.dslsc", "dev.typr.foundationssc") {
    override val Bijection: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.dslsc.Bijection")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.Fragment")
    override val RowCodec: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.RowCodec")
    override val RowCodecs: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.RowCodecs")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val TupleExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr")
    override val Tuples: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Tuples")
    override val interpolatorName: String = "sql"
    override def TupleExprN(n: Int): jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr$n")
    override def FieldsExpr(rowType: jvm.Type): jvm.Type = jvm.Type.Qualified(s"${Java.dslPackage}.FieldsExpr0").of(rowType)
    override val FieldsBase: jvm.Type.Qualified = jvm.Type.Qualified(s"${Java.dslPackage}.FieldsBase")
  }

  case object Java extends DslQualifiedNamesFoundations("dev.typr.dsl", "dev.typr.foundations") {
    override val Bijection: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Bijection")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Fragment")
    override val RowCodec: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.RowCodec")
    override val RowCodecs: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.RowCodecs")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val TupleExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr")
    override val Tuples: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Tuple")
    override val interpolatorName: String = "concat"
    override def TupleExprN(n: Int): jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr.TupleExpr$n")
    override def FieldsExpr(rowType: jvm.Type): jvm.Type = jvm.Type.Qualified(s"$dslPackage.FieldsExpr").of(rowType)
    override val FieldsBase: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.FieldsBase")
  }

  case object Kotlin extends DslQualifiedNamesFoundations("dev.typr.dslkt", "dev.typr.foundationskt") {
    override val Bijection: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.Bijection")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.Fragment")
    override val RowCodec: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.RowCodec")
    override val RowCodecs: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.RowCodecs")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val TupleExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr")
    override val Tuples: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Tuples")
    val SqlExprExtensions: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExprExtensions")
    override val interpolatorName: String = "concat"
    override def TupleExprN(n: Int): jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.TupleExpr$n")
    override def FieldsExpr(rowType: jvm.Type): jvm.Type = jvm.Type.Qualified(s"${Java.dslPackage}.FieldsExpr").of(rowType)
    override val FieldsBase: jvm.Type.Qualified = jvm.Type.Qualified(s"${Java.dslPackage}.FieldsBase")
  }

  /** Legacy DSL - preserves old typr.dsl package for Anorm/Doobie/ZioJdbc */
  case object Legacy extends DslQualifiedNames("typr.dsl") {
    override val Bijection: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Bijection")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Fragment")
    override val RowCodec: jvm.Type.Qualified = jvm.Type.Qualified("typr.anormruntime.RowParser")
    override val RowCodecs: jvm.Type.Qualified = jvm.Type.Qualified("typr.anormruntime.RowParser")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val TupleExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Tuples")
    override val Tuples: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.Tuples")
    override val interpolatorName: String = "frag"
  }
}
