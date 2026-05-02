package typr.bridge.model

sealed trait TypePolicy
object TypePolicy {
  case object Exact extends TypePolicy
  case object AllowWidening extends TypePolicy
  case object AllowNarrowing extends TypePolicy
  case object AllowPrecisionLoss extends TypePolicy
  case object AllowTruncation extends TypePolicy
  case object AllowNullableToRequired extends TypePolicy

  def fromString(s: String): Option[TypePolicy] = s.toLowerCase match {
    case "exact"                      => Some(Exact)
    case "allow_widening"             => Some(AllowWidening)
    case "allow_narrowing"            => Some(AllowNarrowing)
    case "allow_precision_loss"       => Some(AllowPrecisionLoss)
    case "allow_truncation"           => Some(AllowTruncation)
    case "allow_nullable_to_required" => Some(AllowNullableToRequired)
    case _                            => None
  }

  def label(policy: TypePolicy): String = policy match {
    case Exact                   => "exact"
    case AllowWidening           => "allow_widening"
    case AllowNarrowing          => "allow_narrowing"
    case AllowPrecisionLoss      => "allow_precision_loss"
    case AllowTruncation         => "allow_truncation"
    case AllowNullableToRequired => "allow_nullable_to_required"
  }
}
