package typr

/** Here you can override the type of a column. You specify either a fully qualified type name or something which is in scope, either by default in scala or is exposed in a super package of the
  * generated code.
  *
  * Note that you cannot override `Array` or `Option`.
  */
trait TypeOverride {

  /** @param relation
    *   name of schema and relation
    * @param colName
    *   name of column
    * @return
    */
  def apply(relation: db.RelationName, colName: db.ColName): Option[String]

  final def orElse(other: TypeOverride): TypeOverride =
    (from, colName) => apply(from, colName).orElse(other(from, colName))
}

object TypeOverride {
  val Empty: TypeOverride = (_, _) => None

  def of(pf: PartialFunction[(db.RelationName, db.ColName), String]): TypeOverride =
    (from, colName) => pf.lift((from, colName))

  def relation(pf: PartialFunction[( /* schemaname.relationname */ String, /* column name*/ String), String]): TypeOverride =
    (rel, colName) => pf.lift((rel.value, colName.value))

}
