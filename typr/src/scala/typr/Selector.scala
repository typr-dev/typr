package typr

@FunctionalInterface
trait Selector {
  def include(relation: db.RelationName): Boolean

  final infix def or(other: Selector): Selector = rel => include(rel) || other.include(rel)

  final infix def and(other: Selector): Selector = rel => include(rel) && other.include(rel)

  final def unary_! : Selector = rel => !include(rel)
}

object Selector {
  def apply(f: db.RelationName => Boolean): Selector = f.apply

  val Internal = Set("pg_catalog", "information_schema").map(Option.apply)
  val All: Selector = _ => true
  val None: Selector = _ => false
  val ExcludePostgresInternal: Selector = rel => !Internal(rel.schema)
  val OnlyPostgresInternal: Selector = rel => Internal(rel.schema)

  def fullRelationNames(names: String*): Selector = {
    val wanted = names.toSet
    rel => wanted(rel.value)
  }

  def relationNames(names: String*): Selector = {
    val wanted = names.toSet
    rel => wanted(rel.name)
  }

  def schemas(names: String*): Selector = {
    val wanted = names.toSet
    rel => rel.schema.exists(wanted.apply)
  }
}
