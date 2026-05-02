package typr

/** Here you can override the inferred nullability of a column.
  *
  * Postgres have multiple inferring nullability for both views and prepared statements (which are used for sql scripts)
  */
trait NullabilityOverride {

  /** @param from
    *   inside the ADT you can find the name of the relation of the location of the sql script
    * @param colName
    *   name of column
    * @return
    */
  def apply(from: db.RelationName, colName: db.ColName): Option[Nullability]

  final def orElse(other: NullabilityOverride): NullabilityOverride =
    (from, colName) => apply(from, colName).orElse(other(from, colName))
}

object NullabilityOverride {
  val Empty: NullabilityOverride = (_, _) => None

  def of(pf: PartialFunction[(db.RelationName, db.ColName), Nullability]): NullabilityOverride =
    (from, colName) => pf.lift((from, colName))

  def relation(pf: PartialFunction[( /* schemaname.relationname */ String, /* column name*/ String), Nullability]): NullabilityOverride =
    (from, colName) => pf.lift((from.value, colName.value))
}
