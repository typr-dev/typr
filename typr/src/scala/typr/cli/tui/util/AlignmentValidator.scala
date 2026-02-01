package typr.cli.tui.util

import typr.cli.tui.SourceName
import typr.cli.tui.TuiState
import typr.cli.tui.FieldAlignmentState
import typr.cli.tui.FieldEditState
import typr.cli.tui.AlignedSourceEditState
import typr.cli.tui.CompositeWizardState
import typr.cli.tui.AlignmentDisplayRow
import typr.cli.tui.AlignmentCellStatus
import typr.bridge.AlignmentStatus
import typr.bridge.AlignmentComputer
import typr.bridge.CompositeField
import typr.bridge.CompositeTypeDefinition
import typr.bridge.DefaultNameAligner
import typr.bridge.FieldAlignmentResult
import typr.bridge.AlignedSource
import typr.bridge.SourceEntity
import typr.bridge.SourceEntityType
import typr.bridge.SourceField

object AlignmentValidator {

  case class ValidationResult(
      projectionKey: String,
      status: AlignmentStatus,
      rows: List[AlignmentDisplayRow]
  )

  def validateAlignedSource(
      state: TuiState,
      wizardState: CompositeWizardState,
      alignedSourceState: AlignedSourceEditState
  ): ValidationResult = {
    val fields = wizardState.fields
    val alignedSource = alignedSourceState.toAlignedSource
    val sourceName = SourceName(alignedSourceState.sourceName)

    ProjectionFieldExtractor.extractFieldsFromSource(state, sourceName, alignedSourceState.entityPath) match {
      case Right(extractedEntity) =>
        validateWithEntity(fields, alignedSource, extractedEntity)

      case Left(error) =>
        ValidationResult(
          projectionKey = alignedSource.key,
          status = AlignmentStatus.Incompatible(List(error)),
          rows = fields.map { f =>
            AlignmentDisplayRow(
              canonicalField = f.name,
              canonicalType = f.typeName,
              sourceField = None,
              sourceType = None,
              status = AlignmentCellStatus.Missing,
              autoMapped = false
            )
          }
        )
    }
  }

  private def validateWithEntity(
      fields: List[FieldEditState],
      alignedSource: AlignedSource,
      extractedEntity: ExtractedEntity
  ): ValidationResult = {
    val domainType = toDomainTypeDefinition(fields, alignedSource)
    val sourceEntity = toSourceEntity(extractedEntity)
    val nameAligner = DefaultNameAligner.default

    val results = AlignmentComputer.computeAlignment(domainType, alignedSource, sourceEntity, nameAligner)
    val status = AlignmentStatus.fromResults(results, alignedSource.mode)

    val sourceFieldMap = sourceEntity.fields.map(f => f.name -> f).toMap

    val rows = fields.map { field =>
      val alignmentResult = results.collectFirst {
        case r @ FieldAlignmentResult.Aligned(c, _, _) if c == field.name                => r
        case r @ FieldAlignmentResult.Missing(c) if c == field.name                      => r
        case r @ FieldAlignmentResult.TypeMismatch(c, _, _, _) if c == field.name        => r
        case r @ FieldAlignmentResult.NullabilityMismatch(c, _, _, _) if c == field.name => r
      }

      alignmentResult match {
        case Some(FieldAlignmentResult.Aligned(_, sourceField, _)) =>
          val sf = sourceFieldMap.get(sourceField)
          AlignmentDisplayRow(
            canonicalField = field.name,
            canonicalType = field.typeName,
            sourceField = Some(sourceField),
            sourceType = sf.map(_.typeName),
            status = AlignmentCellStatus.Aligned,
            autoMapped = !alignedSource.mappings.contains(field.name)
          )

        case Some(FieldAlignmentResult.Missing(_)) =>
          AlignmentDisplayRow(
            canonicalField = field.name,
            canonicalType = field.typeName,
            sourceField = None,
            sourceType = None,
            status = AlignmentCellStatus.Missing,
            autoMapped = false
          )

        case Some(FieldAlignmentResult.TypeMismatch(_, sourceField, _, actualType)) =>
          AlignmentDisplayRow(
            canonicalField = field.name,
            canonicalType = field.typeName,
            sourceField = Some(sourceField),
            sourceType = Some(actualType),
            status = AlignmentCellStatus.TypeMismatch,
            autoMapped = false
          )

        case Some(FieldAlignmentResult.NullabilityMismatch(_, sourceField, _, _)) =>
          val sf = sourceFieldMap.get(sourceField)
          AlignmentDisplayRow(
            canonicalField = field.name,
            canonicalType = field.typeName,
            sourceField = Some(sourceField),
            sourceType = sf.map(_.typeName),
            status = AlignmentCellStatus.NullabilityMismatch,
            autoMapped = false
          )

        case _ =>
          AlignmentDisplayRow(
            canonicalField = field.name,
            canonicalType = field.typeName,
            sourceField = None,
            sourceType = None,
            status = AlignmentCellStatus.Missing,
            autoMapped = false
          )
      }
    }

    val extraRows = results.collect { case FieldAlignmentResult.Extra(sourceField, sourceType) =>
      AlignmentDisplayRow(
        canonicalField = s"[extra] $sourceField",
        canonicalType = "",
        sourceField = Some(sourceField),
        sourceType = Some(sourceType),
        status = AlignmentCellStatus.Extra,
        autoMapped = false
      )
    }

    ValidationResult(
      projectionKey = alignedSource.key,
      status = status,
      rows = rows ++ extraRows
    )
  }

  private def toDomainTypeDefinition(fields: List[FieldEditState], alignedSource: AlignedSource): CompositeTypeDefinition = {
    CompositeTypeDefinition(
      name = "wizard",
      primary = None,
      fields = fields.map { f =>
        CompositeField(
          name = f.name,
          typeName = f.typeName,
          nullable = f.nullable,
          array = f.array,
          description = if (f.description.isEmpty) None else Some(f.description)
        )
      },
      alignedSources = Map(alignedSource.key -> alignedSource),
      description = None,
      generateDomainType = true,
      generateMappers = true,
      generateInterface = false,
      generateBuilder = false,
      generateCopy = true
    )
  }

  private def toSourceEntity(extracted: ExtractedEntity): SourceEntity = {
    SourceEntity(
      sourceName = extracted.sourceName,
      entityPath = extracted.entityPath,
      sourceType = SourceEntityType.Table,
      fields = extracted.fields.map { f =>
        SourceField(
          name = f.name,
          typeName = f.typeName,
          nullable = f.nullable,
          isPrimaryKey = f.isPrimaryKey,
          comment = f.comment
        )
      }
    )
  }

  def validateAllAlignedSources(
      state: TuiState,
      wizardState: CompositeWizardState
  ): Map[String, ValidationResult] = {
    wizardState.alignedSources.map { alignedSource =>
      alignedSource.key -> validateAlignedSource(state, wizardState, alignedSource)
    }.toMap
  }

  def toFieldAlignmentState(result: ValidationResult): FieldAlignmentState = {
    FieldAlignmentState(
      projectionKey = result.projectionKey,
      rows = result.rows,
      selectedRowIndex = 0,
      editingMapping = None,
      validationResult = result.status
    )
  }
}
