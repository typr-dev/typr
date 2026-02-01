package typr.bridge.api

import typr.bridge.*
import typr.bridge.model.*
import typr.bridge.validation.*

class BridgeApiImpl extends BridgeApi {

  override def check(
      domainTypes: Map[String, DomainTypeDefinition],
      sourceDeclarations: Map[String, Map[String, SourceDeclaration]],
      sourceEntities: Map[String, Map[String, SourceEntity]],
      nameAligner: NameAligner
  ): CheckReport = {
    val allFindings = List.newBuilder[CheckFinding]
    val summaries = List.newBuilder[EntitySummary]

    domainTypes.foreach { case (entityName, domainType) =>
      val entitySourceDecls = sourceDeclarations.getOrElse(entityName, Map.empty)
      val entitySourceEntities = flattenSourceEntities(entitySourceDecls, sourceEntities)

      val findings = FlowValidator.validateEntity(domainType, entitySourceDecls, entitySourceEntities, nameAligner)
      allFindings ++= findings

      val resolvedActions = entitySourceDecls.flatMap { case (sourceKey, sourceDecl) =>
        entitySourceEntities.get(sourceKey).map { se =>
          SmartDefaults.resolveFieldActions(domainType, sourceDecl, se, nameAligner)
        }
      }.flatten

      val forwardCount = resolvedActions.count(_.isInstanceOf[ResolvedFieldAction.Forward])
      val dropCount = resolvedActions.count(_.isInstanceOf[ResolvedFieldAction.Drop])
      val customCount = resolvedActions.count(_.isInstanceOf[ResolvedFieldAction.Custom])

      summaries += EntitySummary(
        name = entityName,
        fieldCount = domainType.fields.size,
        sourceCount = entitySourceDecls.size,
        forwardCount = forwardCount,
        dropCount = dropCount,
        customCount = customCount,
        errorCount = findings.count(_.severity == Severity.Error),
        warningCount = findings.count(_.severity == Severity.Warning)
      )
    }

    CheckReport(
      findings = allFindings.result(),
      entitySummaries = summaries.result(),
      timestamp = System.currentTimeMillis()
    )
  }

  override def resolveFlows(
      domainType: DomainTypeDefinition,
      sourceDeclarations: Map[String, SourceDeclaration],
      sourceEntities: Map[String, SourceEntity],
      nameAligner: NameAligner
  ): ResolvedEntityFlow = {
    val sources = sourceDeclarations.map { case (sourceKey, sourceDecl) =>
      val resolvedFields = sourceEntities.get(sourceKey) match {
        case Some(se) =>
          SmartDefaults.resolveFieldActions(domainType, sourceDecl, se, nameAligner)
        case None =>
          Nil
      }
      sourceKey -> ResolvedSourceFlow(
        sourceKey = sourceKey,
        direction = sourceDecl.direction,
        fields = resolvedFields
      )
    }

    ResolvedEntityFlow(
      entityName = domainType.name,
      sources = sources
    )
  }

  private def flattenSourceEntities(
      sourceDeclarations: Map[String, SourceDeclaration],
      allSourceEntities: Map[String, Map[String, SourceEntity]]
  ): Map[String, SourceEntity] = {
    sourceDeclarations.flatMap { case (sourceKey, sourceDecl) =>
      allSourceEntities
        .getOrElse(sourceDecl.sourceName, Map.empty)
        .get(sourceDecl.entityPath)
        .map(sourceKey -> _)
    }
  }
}
