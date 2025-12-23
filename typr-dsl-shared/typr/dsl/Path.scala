package typr.dsl

sealed trait Path

object Path {
  implicit val ordering: Ordering[Path] = {
    case (LeftInJoin, RightInJoin) => -1
    case (RightInJoin, LeftInJoin) => 1
    case _                         => 0
  }
  case object LeftInJoin extends Path
  case object RightInJoin extends Path
}
