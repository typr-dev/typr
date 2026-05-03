package typr
package internal

case class ComputedColumn(
    pointsTo: List[(Source.Relation, db.ColName)],
    name: jvm.Ident,
    dbCol: db.Col,
    typoType: TypoType
) {
  def dbName = dbCol.name
  def tpe: jvm.Type = typoType.jvmType
  def param: jvm.Param[jvm.Type] = jvm.Param(name, tpe)
}
