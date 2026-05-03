package typr.bridge.model

sealed trait ResolvedFieldAction
object ResolvedFieldAction {
  case class Forward(
      domainField: String,
      sourceField: String,
      typePolicy: TypePolicy,
      isAutoMatched: Boolean
  ) extends ResolvedFieldAction

  case class Drop(
      domainField: String,
      reason: Option[String],
      isAutoDropped: Boolean
  ) extends ResolvedFieldAction

  case class Custom(
      domainField: String,
      kind: CustomKind
  ) extends ResolvedFieldAction

  case class Unannotated(
      sourceField: String,
      sourceType: String,
      sourceNullable: Boolean
  ) extends ResolvedFieldAction
}

case class ResolvedEntityFlow(
    entityName: String,
    sources: Map[String, ResolvedSourceFlow]
)

case class ResolvedSourceFlow(
    sourceKey: String,
    direction: FlowDirection,
    fields: List[ResolvedFieldAction]
)
