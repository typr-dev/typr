package typr.bridge.api

import typr.bridge.*
import typr.bridge.model.*

trait BridgeApi {
  def check(
      domainTypes: Map[String, DomainTypeDefinition],
      sourceDeclarations: Map[String, Map[String, SourceDeclaration]],
      sourceEntities: Map[String, Map[String, SourceEntity]],
      nameAligner: NameAligner
  ): CheckReport

  def resolveFlows(
      domainType: DomainTypeDefinition,
      sourceDeclarations: Map[String, SourceDeclaration],
      sourceEntities: Map[String, SourceEntity],
      nameAligner: NameAligner
  ): ResolvedEntityFlow
}
