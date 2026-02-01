package typr.internal

object ArrayName {
  def unapply(udtName: String): Option[String] = {
    udtName.split('.') match {
      case Array(schema, name) if name.startsWith("_") => Some(s"$schema.${name.drop(1)}")
      case Array(name) if name.startsWith("_")         => Some(name.drop(1))
      case _                                           => None
    }
  }
}
