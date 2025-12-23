package typr

sealed abstract class DslQualifiedNames(val dslPackage: String) {
  val Bijection = jvm.Type.Qualified(s"$dslPackage.Bijection")
  val CompositeIn = jvm.Type.Qualified(s"$dslPackage.SqlExpr.CompositeIn")
  val CompositeInPart = jvm.Type.Qualified(s"$dslPackage.SqlExpr.CompositeIn.Part") // Java
  val CompositeTuplePart = jvm.Type.Qualified(s"$dslPackage.SqlExpr.CompositeIn.TuplePart") // Scala
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
  val OptField = jvm.Type.Qualified(s"$dslPackage.SqlExpr.OptField")
  val Path = jvm.Type.Qualified(s"$dslPackage.Path")
  val RowParser: jvm.Type.Qualified
  val RowParsers: jvm.Type.Qualified
  val SelectBuilder = jvm.Type.Qualified(s"$dslPackage.SelectBuilder")
  val SelectBuilderMock = jvm.Type.Qualified(s"$dslPackage.SelectBuilderMock")
  val SelectParams = jvm.Type.Qualified(s"$dslPackage.SelectParams")
  val SqlExpr: jvm.Type.Qualified
  val StructureRelation = jvm.Type.Qualified(s"$dslPackage.RelationStructure")
  val UpdateBuilder = jvm.Type.Qualified(s"$dslPackage.UpdateBuilder")
  val UpdateBuilderMock = jvm.Type.Qualified(s"$dslPackage.UpdateBuilderMock")
  val UpdateParams = jvm.Type.Qualified(s"$dslPackage.UpdateParams")

  /** The name of the string interpolator used for SQL fragments in this DSL */
  val interpolatorName: String
}

object DslQualifiedNames {
  case object Scala extends DslQualifiedNames("typr.scaladsl") {
    override val RowParser: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.RowParser")
    override val RowParsers: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.RowParsers")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("typr.scaladsl.Fragment")
    override val interpolatorName: String = "sql"
  }

  case object Java extends DslQualifiedNames("typr.dsl") {
    override val RowParser: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.RowParser")
    override val RowParsers: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.RowParsers")
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified(s"$dslPackage.SqlExpr")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.Fragment")
    override val interpolatorName: String = "interpolate"
  }

  case object Kotlin extends DslQualifiedNames("typr.kotlindsl") {
    override val SqlExpr: jvm.Type.Qualified = jvm.Type.Qualified("typr.dsl.SqlExpr")
    override val RowParser: jvm.Type.Qualified = jvm.Type.Qualified("typr.kotlindsl.RowParser")
    override val RowParsers: jvm.Type.Qualified = jvm.Type.Qualified("typr.kotlindsl.RowParsers")
    override val Fragment: jvm.Type.Qualified = jvm.Type.Qualified("typr.kotlindsl.Fragment")
    override val interpolatorName: String = "interpolate"
  }
}
