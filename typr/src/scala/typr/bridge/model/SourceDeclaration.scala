package typr.bridge.model

sealed trait FlowDirection
object FlowDirection {
  case object In extends FlowDirection
  case object Out extends FlowDirection
  case object InOut extends FlowDirection

  def fromString(s: String): Option[FlowDirection] = s.toLowerCase match {
    case "in"                          => Some(In)
    case "out"                         => Some(Out)
    case "in-out" | "in_out" | "inout" => Some(InOut)
    case _                             => None
  }

  def label(direction: FlowDirection): String = direction match {
    case In    => "in"
    case Out   => "out"
    case InOut => "in-out"
  }
}

sealed trait SourceRole
object SourceRole {
  case object Primary extends SourceRole
  case object Aligned extends SourceRole
}

case class SourceDeclaration(
    sourceName: String,
    entityPath: String,
    role: SourceRole,
    direction: FlowDirection,
    mode: typr.bridge.CompatibilityMode,
    mappings: Map[String, String],
    exclude: Set[String],
    includeExtra: List[String],
    readonly: Boolean,
    defaultTypePolicy: TypePolicy,
    fieldOverrides: Map[String, FieldOverride]
) {
  def key: String = s"$sourceName:$entityPath"
}
