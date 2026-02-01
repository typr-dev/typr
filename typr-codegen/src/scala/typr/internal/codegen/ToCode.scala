package typr
package internal
package codegen

@FunctionalInterface
trait ToCode[T] {
  def toCode(t: T): jvm.Code
}

object ToCode {
  def apply[T: ToCode]: ToCode[T] = implicitly

  implicit def tree[T <: jvm.Tree]: ToCode[T] = jvm.Code.Tree.apply

  implicit val str: ToCode[String] = jvm.Code.Str.apply
  implicit val int: ToCode[Int] = _.toString
  implicit val code: ToCode[jvm.Code] = identity
  implicit val dbColName: ToCode[db.ColName] = colName => jvm.StrLit(colName.value)

  implicit val tableName: ToCode[db.RelationName] = name => jvm.Code.Str(name.quotedValue)
}
