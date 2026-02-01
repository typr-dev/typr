package typr

sealed trait Scope
object Scope {
  case object Main extends Scope
  case object Test extends Scope
}
