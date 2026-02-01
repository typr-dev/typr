package typr.bridge.validation

import typr.bridge.*
import typr.bridge.model.*

object SmartDefaults {

  def resolveFieldActions(
      domainType: DomainTypeDefinition,
      sourceDecl: SourceDeclaration,
      sourceEntity: SourceEntity,
      nameAligner: NameAligner
  ): List[ResolvedFieldAction] = {
    val sourceFieldMap = sourceEntity.fields.map(f => f.name -> f).toMap
    val sourceFieldNames = sourceFieldMap.keys.toList
    val usedSourceFields = scala.collection.mutable.Set[String]()

    val domainActions = domainType.fields.map { domainField =>
      sourceDecl.fieldOverrides.get(domainField.name) match {
        case Some(FieldOverride.Forward(sourceNameOpt, policyOpt, _)) =>
          val resolvedSourceName = sourceNameOpt
            .orElse(sourceDecl.mappings.get(domainField.name))
            .getOrElse(domainField.name)
          val autoAligned = nameAligner.align(resolvedSourceName, sourceFieldNames)
          val finalSourceName = sourceFieldMap.get(resolvedSourceName).map(_.name).orElse(autoAligned).getOrElse(resolvedSourceName)
          usedSourceFields += finalSourceName
          ResolvedFieldAction.Forward(
            domainField = domainField.name,
            sourceField = finalSourceName,
            typePolicy = policyOpt.getOrElse(sourceDecl.defaultTypePolicy),
            isAutoMatched = false
          )

        case Some(FieldOverride.Drop(reason)) =>
          ResolvedFieldAction.Drop(
            domainField = domainField.name,
            reason = reason,
            isAutoDropped = false
          )

        case Some(FieldOverride.Custom(kind)) =>
          ResolvedFieldAction.Custom(
            domainField = domainField.name,
            kind = kind
          )

        case None =>
          val mappedName = sourceDecl.mappings.get(domainField.name)
          val resolvedName = mappedName.getOrElse(domainField.name)
          val autoAligned = nameAligner.align(resolvedName, sourceFieldNames)

          val matchedSourceField = sourceFieldMap.get(resolvedName).orElse(autoAligned.flatMap(sourceFieldMap.get))

          matchedSourceField match {
            case Some(sf) =>
              usedSourceFields += sf.name
              val domainNorm = TypeNarrower.mapCanonicalToNormalized(domainField.typeName)
              val sourceNorm = TypeNarrower.normalizeDbType(sf.typeName)
              val compatible = domainNorm == sourceNorm || TypeNarrower.areTypesCompatible(domainNorm, sourceNorm)
              if (compatible) {
                ResolvedFieldAction.Forward(
                  domainField = domainField.name,
                  sourceField = sf.name,
                  typePolicy = sourceDecl.defaultTypePolicy,
                  isAutoMatched = true
                )
              } else {
                ResolvedFieldAction.Forward(
                  domainField = domainField.name,
                  sourceField = sf.name,
                  typePolicy = sourceDecl.defaultTypePolicy,
                  isAutoMatched = true
                )
              }

            case None =>
              ResolvedFieldAction.Drop(
                domainField = domainField.name,
                reason = None,
                isAutoDropped = true
              )
          }
      }
    }

    val unannotatedFields = sourceEntity.fields
      .filterNot(f => usedSourceFields.contains(f.name))
      .filterNot(f => sourceDecl.exclude.contains(f.name))
      .filterNot(f => sourceDecl.includeExtra.contains(f.name))
      .flatMap { sf =>
        sourceDecl.mode match {
          case CompatibilityMode.Exact =>
            Some(
              ResolvedFieldAction.Unannotated(
                sourceField = sf.name,
                sourceType = sf.typeName,
                sourceNullable = sf.nullable
              )
            )
          case CompatibilityMode.Superset =>
            None
          case CompatibilityMode.Subset =>
            None
        }
      }

    domainActions ++ unannotatedFields
  }
}
