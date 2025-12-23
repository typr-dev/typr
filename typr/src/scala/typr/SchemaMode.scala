package typr

sealed trait SchemaMode

object SchemaMode {
  case object MultiSchema extends SchemaMode
  case class SingleSchema(schema: String) extends SchemaMode
}
