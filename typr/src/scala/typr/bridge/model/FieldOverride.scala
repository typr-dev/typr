package typr.bridge.model

sealed trait FieldOverride
object FieldOverride {
  case class Forward(
      sourceFieldName: Option[String],
      typePolicy: Option[TypePolicy],
      directionOverride: Option[FieldDirection]
  ) extends FieldOverride

  case class Drop(reason: Option[String]) extends FieldOverride

  case class Custom(kind: CustomKind) extends FieldOverride
}

sealed trait FieldDirection
object FieldDirection {
  case object OutOnly extends FieldDirection
  case object InOnly extends FieldDirection

  def fromString(s: String): Option[FieldDirection] = s.toLowerCase match {
    case "out-only" | "out_only" => Some(OutOnly)
    case "in-only" | "in_only"   => Some(InOnly)
    case _                       => None
  }
}

sealed trait CustomKind
object CustomKind {
  case class MergeFrom(sourceFields: List[String]) extends CustomKind
  case class SplitFrom(sourceField: String) extends CustomKind
  case class Enrichment(description: Option[String]) extends CustomKind
  case class ComputedFrom(domainFields: List[String]) extends CustomKind
}
