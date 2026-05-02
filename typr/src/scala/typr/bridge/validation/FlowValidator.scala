package typr.bridge.validation

import typr.bridge.*
import typr.bridge.model.*

object FlowValidator {

  def validateEntity(
      domainType: DomainTypeDefinition,
      sourceDeclarations: Map[String, SourceDeclaration],
      sourceEntities: Map[String, SourceEntity],
      nameAligner: NameAligner
  ): List[CheckFinding] = {
    val findings = List.newBuilder[CheckFinding]
    val entityName = domainType.name

    if (domainType.fields.isEmpty) {
      findings += CheckFinding(
        entityName = entityName,
        sourceKey = None,
        fieldName = None,
        severity = Severity.Error,
        code = CheckCode.NoFields,
        message = s"Domain type '$entityName' has no fields",
        suggestion = Some("Add at least one field to the domain type")
      )
    }

    val hasPrimary = sourceDeclarations.values.exists(_.role == SourceRole.Primary)
    if (!hasPrimary) {
      findings += CheckFinding(
        entityName = entityName,
        sourceKey = None,
        fieldName = None,
        severity = Severity.Error,
        code = CheckCode.NoPrimarySource,
        message = s"Domain type '$entityName' has no primary source declared",
        suggestion = Some("Declare a primary source for this domain type")
      )
    }

    sourceDeclarations.foreach { case (sourceKey, sourceDecl) =>
      sourceEntities.get(sourceKey) match {
        case None =>
          findings += CheckFinding(
            entityName = entityName,
            sourceKey = Some(sourceKey),
            fieldName = None,
            severity = Severity.Error,
            code = CheckCode.SourceEntityNotFound,
            message = s"Source entity '$sourceKey' not found",
            suggestion = Some("Check that the source name and entity path are correct")
          )

        case Some(sourceEntity) =>
          val resolved = SmartDefaults.resolveFieldActions(domainType, sourceDecl, sourceEntity, nameAligner)
          val sourceFieldMap = sourceEntity.fields.map(f => f.name -> f).toMap
          val domainFieldMap = domainType.fields.map(f => f.name -> f).toMap

          resolved.foreach {
            case ResolvedFieldAction.Forward(domainField, sourceField, typePolicy, _) =>
              sourceFieldMap.get(sourceField) match {
                case None =>
                  findings += CheckFinding(
                    entityName = entityName,
                    sourceKey = Some(sourceKey),
                    fieldName = Some(domainField),
                    severity = Severity.Error,
                    code = CheckCode.MissingRequiredField,
                    message = s"Forward field '$domainField' references source field '$sourceField' which does not exist",
                    suggestion = Some(s"Check field mapping or add '$sourceField' to the source entity")
                  )

                case Some(sf) =>
                  domainFieldMap.get(domainField).foreach { df =>
                    val result = TypePolicyValidator.validateWithCanonical(df.typeName, sf.typeName, typePolicy)
                    result match {
                      case Left(msg) =>
                        val code = if (typePolicy == TypePolicy.Exact) CheckCode.TypeIncompatible else CheckCode.TypePolicyViolation
                        findings += CheckFinding(
                          entityName = entityName,
                          sourceKey = Some(sourceKey),
                          fieldName = Some(domainField),
                          severity = Severity.Error,
                          code = code,
                          message = msg,
                          suggestion = Some(s"Adjust the type policy or update the field type")
                        )
                      case Right(()) =>
                    }

                    if (typePolicy != TypePolicy.AllowNullableToRequired) {
                      if (!df.nullable && sf.nullable) {
                        findings += CheckFinding(
                          entityName = entityName,
                          sourceKey = Some(sourceKey),
                          fieldName = Some(domainField),
                          severity = Severity.Error,
                          code = CheckCode.NullabilityMismatch,
                          message = s"Field '$domainField' is required in domain but nullable in source '$sourceKey'",
                          suggestion = Some("Mark the domain field as nullable or use AllowNullableToRequired policy")
                        )
                      }
                    }
                  }
              }

            case ResolvedFieldAction.Drop(domainField, _, _) =>
              sourceDecl.direction match {
                case FlowDirection.Out | FlowDirection.InOut =>
                  findings += CheckFinding(
                    entityName = entityName,
                    sourceKey = Some(sourceKey),
                    fieldName = Some(domainField),
                    severity = Severity.Warning,
                    code = CheckCode.MissingRequiredField,
                    message = s"Field '$domainField' is dropped from out-source '$sourceKey' - data will be lost",
                    suggestion = Some("Confirm this is intentional or add a forward mapping")
                  )
                case FlowDirection.In =>
              }

            case ResolvedFieldAction.Custom(domainField, kind) =>
              kind match {
                case CustomKind.MergeFrom(sourceFields) =>
                  sourceFields.foreach { sf =>
                    if (!sourceFieldMap.contains(sf)) {
                      findings += CheckFinding(
                        entityName = entityName,
                        sourceKey = Some(sourceKey),
                        fieldName = Some(domainField),
                        severity = Severity.Error,
                        code = CheckCode.InvalidMergeFromRef,
                        message = s"MergeFrom references source field '$sf' which does not exist in '$sourceKey'",
                        suggestion = Some(s"Check that '$sf' exists in the source entity")
                      )
                    }
                  }

                case CustomKind.SplitFrom(sourceField) =>
                  if (!sourceFieldMap.contains(sourceField)) {
                    findings += CheckFinding(
                      entityName = entityName,
                      sourceKey = Some(sourceKey),
                      fieldName = Some(domainField),
                      severity = Severity.Error,
                      code = CheckCode.InvalidSplitFromRef,
                      message = s"SplitFrom references source field '$sourceField' which does not exist in '$sourceKey'",
                      suggestion = Some(s"Check that '$sourceField' exists in the source entity")
                    )
                  }

                case CustomKind.ComputedFrom(domainFields) =>
                  domainFields.foreach { df =>
                    if (!domainFieldMap.contains(df)) {
                      findings += CheckFinding(
                        entityName = entityName,
                        sourceKey = Some(sourceKey),
                        fieldName = Some(domainField),
                        severity = Severity.Error,
                        code = CheckCode.InvalidComputedFromRef,
                        message = s"ComputedFrom references domain field '$df' which does not exist in '$entityName'",
                        suggestion = Some(s"Check that '$df' exists in the domain type")
                      )
                    }
                  }

                case CustomKind.Enrichment(_) =>
              }

            case ResolvedFieldAction.Unannotated(sourceField, sourceType, _) =>
              findings += CheckFinding(
                entityName = entityName,
                sourceKey = Some(sourceKey),
                fieldName = Some(sourceField),
                severity = Severity.Error,
                code = CheckCode.UnannotatedField,
                message = s"Source field '$sourceField' ($sourceType) in '$sourceKey' is not mapped to any domain field",
                suggestion = Some(s"Decide: forward to a domain field, add to exclude list, or drop")
              )
          }

          validateDirectionRequirements(entityName, sourceKey, sourceDecl, domainType, resolved, findings)
      }
    }

    findings.result()
  }

  private def validateDirectionRequirements(
      entityName: String,
      sourceKey: String,
      sourceDecl: SourceDeclaration,
      domainType: DomainTypeDefinition,
      resolved: List[ResolvedFieldAction],
      findings: scala.collection.mutable.Builder[CheckFinding, List[CheckFinding]]
  ): Unit = {
    val forwardedDomainFields = resolved.collect { case ResolvedFieldAction.Forward(df, _, _, _) => df }.toSet
    val customDomainFields = resolved.collect { case ResolvedFieldAction.Custom(df, _) => df }.toSet
    val coveredFields = forwardedDomainFields ++ customDomainFields

    domainType.fields.foreach { df =>
      if (!coveredFields.contains(df.name)) {
        sourceDecl.direction match {
          case FlowDirection.Out =>
            findings += CheckFinding(
              entityName = entityName,
              sourceKey = Some(sourceKey),
              fieldName = Some(df.name),
              severity = Severity.Error,
              code = CheckCode.MissingRequiredField,
              message = s"Out-source '$sourceKey' must produce field '${df.name}' but no mapping exists",
              suggestion = Some("Add a forward mapping or custom action for this field")
            )
          case FlowDirection.InOut =>
            findings += CheckFinding(
              entityName = entityName,
              sourceKey = Some(sourceKey),
              fieldName = Some(df.name),
              severity = Severity.Error,
              code = CheckCode.MissingRequiredField,
              message = s"InOut-source '$sourceKey' must produce field '${df.name}' but no mapping exists",
              suggestion = Some("Add a forward mapping or custom action for this field")
            )
          case FlowDirection.In =>
            findings += CheckFinding(
              entityName = entityName,
              sourceKey = Some(sourceKey),
              fieldName = Some(df.name),
              severity = Severity.Warning,
              code = CheckCode.MissingRequiredField,
              message = s"In-source '$sourceKey' does not provide field '${df.name}'",
              suggestion = None
            )
        }
      }
    }
  }
}
