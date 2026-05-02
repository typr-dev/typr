package typr.bridge

import typr.bridge.model.*

object ConfigToBridge {

  def convertAlignedSource(
      sourceName: String,
      entityPath: String,
      alignedSource: AlignedSource,
      role: SourceRole,
      direction: Option[String],
      typePolicy: Option[String],
      fieldOverrides: Option[Map[String, io.circe.Json]]
  ): SourceDeclaration = {
    val flowDirection = direction.flatMap(FlowDirection.fromString).getOrElse(FlowDirection.InOut)
    val defaultPolicy = typePolicy.flatMap(TypePolicy.fromString).getOrElse(TypePolicy.Exact)
    val overrides = fieldOverrides.map(parseFieldOverrides).getOrElse(Map.empty)

    SourceDeclaration(
      sourceName = sourceName,
      entityPath = entityPath,
      role = role,
      direction = flowDirection,
      mode = alignedSource.mode,
      mappings = alignedSource.mappings,
      exclude = alignedSource.exclude,
      includeExtra = alignedSource.includeExtra,
      readonly = alignedSource.readonly,
      defaultTypePolicy = defaultPolicy,
      fieldOverrides = overrides
    )
  }

  def convertPrimarySource(
      sourceName: String,
      entityPath: String,
      mode: CompatibilityMode,
      direction: Option[String],
      typePolicy: Option[String]
  ): SourceDeclaration = {
    val flowDirection = direction.flatMap(FlowDirection.fromString).getOrElse(FlowDirection.InOut)
    val defaultPolicy = typePolicy.flatMap(TypePolicy.fromString).getOrElse(TypePolicy.Exact)

    SourceDeclaration(
      sourceName = sourceName,
      entityPath = entityPath,
      role = SourceRole.Primary,
      direction = flowDirection,
      mode = mode,
      mappings = Map.empty,
      exclude = Set.empty,
      includeExtra = Nil,
      readonly = false,
      defaultTypePolicy = defaultPolicy,
      fieldOverrides = Map.empty
    )
  }

  def convertDomainTypeDefinition(
      domainType: DomainTypeDefinition,
      directionsBySource: Map[String, String],
      typePoliciesBySource: Map[String, String],
      fieldOverridesBySource: Map[String, Map[String, io.circe.Json]]
  ): Map[String, SourceDeclaration] = {
    val primaryDecls = domainType.primary.map { primary =>
      val dir = directionsBySource.get(primary.key)
      val policy = typePoliciesBySource.get(primary.key)
      primary.key -> convertPrimarySource(
        sourceName = primary.sourceName,
        entityPath = primary.entityPath,
        mode = CompatibilityMode.Exact,
        direction = dir,
        typePolicy = policy
      )
    }.toMap

    val alignedDecls = domainType.alignedSources.map { case (key, aligned) =>
      val dir = directionsBySource.get(key)
      val policy = typePoliciesBySource.get(key)
      val overrides = fieldOverridesBySource.get(key)
      key -> convertAlignedSource(
        sourceName = aligned.sourceName,
        entityPath = aligned.entityPath,
        alignedSource = aligned,
        role = SourceRole.Aligned,
        direction = dir,
        typePolicy = policy,
        fieldOverrides = overrides
      )
    }

    primaryDecls ++ alignedDecls
  }

  private def parseFieldOverrides(overrides: Map[String, io.circe.Json]): Map[String, FieldOverride] =
    overrides.flatMap { case (fieldName, json) =>
      parseFieldOverride(json).map(fieldName -> _)
    }

  private def parseFieldOverride(json: io.circe.Json): Option[FieldOverride] =
    json.asString match {
      case Some("forward") =>
        Some(FieldOverride.Forward(sourceFieldName = None, typePolicy = None, directionOverride = None))
      case Some("drop") =>
        Some(FieldOverride.Drop(reason = None))
      case _ =>
        json.asObject.map { obj =>
          val action = obj("action").flatMap(_.asString).getOrElse("forward")
          action match {
            case "drop" =>
              val reason = obj("reason").flatMap(_.asString)
              FieldOverride.Drop(reason = reason)

            case "custom" =>
              val mergeFrom = obj("merge_from").flatMap(_.asArray).map(_.toList.flatMap(_.asString))
              val splitFrom = obj("split_from").flatMap(_.asString)
              val computedFrom = obj("computed_from").flatMap(_.asArray).map(_.toList.flatMap(_.asString))
              val enrichment = obj("enrichment").flatMap(_.asString)

              val kind = mergeFrom
                .map(CustomKind.MergeFrom.apply)
                .orElse(splitFrom.map(CustomKind.SplitFrom.apply))
                .orElse(computedFrom.map(CustomKind.ComputedFrom.apply))
                .orElse(Some(CustomKind.Enrichment(enrichment)))
                .get

              FieldOverride.Custom(kind = kind)

            case _ =>
              val sourceField = obj("source_field").flatMap(_.asString)
              val typePolicy = obj("type_policy").flatMap(_.asString).flatMap(TypePolicy.fromString)
              val dirOverride = obj("direction").flatMap(_.asString).flatMap(FieldDirection.fromString)
              FieldOverride.Forward(
                sourceFieldName = sourceField,
                typePolicy = typePolicy,
                directionOverride = dirOverride
              )
          }
        }
    }
}
