package typr

sealed trait Source
object Source {
  sealed trait Relation extends Source {
    def name: db.RelationName
  }
  case class Table(name: db.RelationName) extends Relation
  case class View(name: db.RelationName, isMaterialized: Boolean) extends Relation
  case class SqlFile(relPath: RelPath) extends Source
}
