package typr.bridge

/** ═══════════════════════════════════════════════════════════════════════════════ Domain Type Model
  *
  * A domain type (product type) represents an entity that exists across multiple sources - databases, APIs, event schemas. Unlike field types that match by pattern, domain types are grounded in a
  * primary source and can be aligned to other sources.
  *
  * Key concepts:
  *   - Primary Source: The source entity this domain type is based on (the anchor)
  *   - Fields: The fields selected for this domain type (with their types)
  *   - Aligned Sources: Other source entities that can be mapped to/from this domain type
  *   - Alignment: Field-level mapping between domain type and aligned source
  *   - Drift: When a source's structure differs from declared alignment ═══════════════════════════════════════════════════════════════════════════════
  */

/** A field in a domain type */
case class DomainField(
    name: String,
    typeName: String,
    nullable: Boolean,
    array: Boolean,
    description: Option[String]
) {
  def compactSyntax: String = {
    val nullSuffix = if (nullable) "?" else ""
    val arraySuffix = if (array) "[]" else ""
    s"$typeName$nullSuffix$arraySuffix"
  }

  def isScalarType: Boolean = DomainField.scalarTypes.contains(typeName)
  def isBridgeType: Boolean = !isScalarType
}

object DomainField {
  val scalarTypes: Set[String] = Set(
    "String",
    "Int",
    "Long",
    "Short",
    "Byte",
    "Float",
    "Double",
    "Boolean",
    "BigDecimal",
    "BigInteger",
    "Instant",
    "LocalDate",
    "LocalTime",
    "LocalDateTime",
    "OffsetDateTime",
    "ZonedDateTime",
    "UUID",
    "ByteArray",
    "Json"
  )

  /** Parse compact syntax: "TypeName", "TypeName?", "TypeName[]", "TypeName?[]" */
  def parseCompact(syntax: String): DomainField = {
    val trimmed = syntax.trim
    val isArray = trimmed.endsWith("[]")
    val withoutArray = if (isArray) trimmed.dropRight(2) else trimmed
    val isNullable = withoutArray.endsWith("?")
    val typeName = if (isNullable) withoutArray.dropRight(1) else withoutArray

    DomainField(
      name = "",
      typeName = typeName,
      nullable = isNullable,
      array = isArray,
      description = None
    )
  }
}

// Keep CompositeField as alias for backwards compatibility
type CompositeField = DomainField
val CompositeField: DomainField.type = DomainField

/** The primary source that this domain type is grounded in */
case class PrimarySource(
    sourceName: String,
    entityPath: String
) {
  def key: String = s"$sourceName:$entityPath"
}

object PrimarySource {
  def fromKey(key: String): Option[PrimarySource] = {
    key.split(":", 2) match {
      case Array(source, entity) => Some(PrimarySource(source, entity))
      case _                     => None
    }
  }
}

/** Compatibility mode for aligned sources */
sealed trait CompatibilityMode
object CompatibilityMode {
  case object Exact extends CompatibilityMode
  case object Superset extends CompatibilityMode
  case object Subset extends CompatibilityMode

  val all: List[CompatibilityMode] = List(Superset, Exact, Subset)

  def fromString(s: String): Option[CompatibilityMode] = s.toLowerCase match {
    case "exact"    => Some(Exact)
    case "superset" => Some(Superset)
    case "subset"   => Some(Subset)
    case _          => None
  }

  def label(mode: CompatibilityMode): String = mode match {
    case Exact    => "exact"
    case Superset => "superset"
    case Subset   => "subset"
  }

  def description(mode: CompatibilityMode): String = mode match {
    case Exact    => "Source must match exactly (events, contracts)"
    case Superset => "Source can have extra fields (DB audit columns, API computed)"
    case Subset   => "Source can have fewer fields (partial views)"
  }
}

/** An aligned source defines how a domain type maps to another source entity */
case class AlignedSource(
    sourceName: String,
    entityPath: String,
    mode: CompatibilityMode,
    mappings: Map[String, String],
    exclude: Set[String],
    includeExtra: List[String],
    readonly: Boolean
) {
  def key: String = s"$sourceName:$entityPath"

  def resolveSourceField(domainField: String): String =
    mappings.getOrElse(domainField, domainField)
}

object AlignedSource {
  def empty(sourceName: String, entityPath: String): AlignedSource = AlignedSource(
    sourceName = sourceName,
    entityPath = entityPath,
    mode = CompatibilityMode.Superset,
    mappings = Map.empty,
    exclude = Set.empty,
    includeExtra = Nil,
    readonly = false
  )
}

// Keep Projection as alias for backwards compatibility
type Projection = AlignedSource
val Projection: AlignedSource.type = AlignedSource

/** Result of aligning a field between domain type and source */
sealed trait FieldAlignmentResult
object FieldAlignmentResult {
  case class Aligned(domainField: String, sourceField: String, typesMatch: Boolean) extends FieldAlignmentResult
  case class Missing(domainField: String) extends FieldAlignmentResult
  case class Extra(sourceField: String, sourceType: String) extends FieldAlignmentResult
  case class TypeMismatch(domainField: String, sourceField: String, expectedType: String, actualType: String) extends FieldAlignmentResult
  case class NullabilityMismatch(domainField: String, sourceField: String, expectedNullable: Boolean, actualNullable: Boolean) extends FieldAlignmentResult

  // Backwards compatibility aliases
  def Aligned(canonicalField: String, sourceField: String, typesMatch: Boolean): Aligned =
    new Aligned(canonicalField, sourceField, typesMatch)
}

/** Overall alignment status for an aligned source */
sealed trait AlignmentStatus
object AlignmentStatus {
  case object Compatible extends AlignmentStatus
  case class Warning(issues: List[String]) extends AlignmentStatus
  case class Incompatible(errors: List[String]) extends AlignmentStatus
  case object NotValidated extends AlignmentStatus

  def fromResults(results: List[FieldAlignmentResult], mode: CompatibilityMode): AlignmentStatus = {
    val errors = List.newBuilder[String]
    val warnings = List.newBuilder[String]

    results.foreach {
      case FieldAlignmentResult.Aligned(_, _, true) =>
      case FieldAlignmentResult.Aligned(c, s, false) =>
        warnings += s"Field '$c' aligned to '$s' but types may differ"

      case FieldAlignmentResult.Missing(f) =>
        mode match {
          case CompatibilityMode.Exact | CompatibilityMode.Superset =>
            errors += s"Required field '$f' not found in source"
          case CompatibilityMode.Subset =>
            warnings += s"Field '$f' missing in source (allowed by subset mode)"
        }

      case FieldAlignmentResult.Extra(f, _) =>
        mode match {
          case CompatibilityMode.Exact =>
            errors += s"Unexpected field '$f' in source (exact mode)"
          case _ =>
        }

      case FieldAlignmentResult.TypeMismatch(c, s, exp, act) =>
        errors += s"Type mismatch: '$c' expects $exp but '$s' is $act"

      case FieldAlignmentResult.NullabilityMismatch(c, _, exp, act) =>
        if (exp && !act) {
          warnings += s"Field '$c' is nullable in domain type but required in source"
        } else if (!exp && act) {
          errors += s"Field '$c' is required in domain type but nullable in source"
        }
    }

    val errorList = errors.result()
    val warningList = warnings.result()

    if (errorList.nonEmpty) Incompatible(errorList)
    else if (warningList.nonEmpty) Warning(warningList)
    else Compatible
  }
}

/** Complete definition of a domain type */
case class DomainTypeDefinition(
    name: String,
    primary: Option[PrimarySource],
    fields: List[DomainField],
    alignedSources: Map[String, AlignedSource],
    description: Option[String],
    generateDomainType: Boolean,
    generateMappers: Boolean,
    generateInterface: Boolean,
    generateBuilder: Boolean,
    generateCopy: Boolean
) {
  def fieldByName(name: String): Option[DomainField] = fields.find(_.name == name)
  def alignedSourceByKey(key: String): Option[AlignedSource] = alignedSources.get(key)

  // Backwards compatibility
  def projections: Map[String, AlignedSource] = alignedSources
  def projectionByKey(key: String): Option[AlignedSource] = alignedSourceByKey(key)
  def generateCanonical: Boolean = generateDomainType
}

object DomainTypeDefinition {
  def empty(name: String): DomainTypeDefinition = DomainTypeDefinition(
    name = name,
    primary = None,
    fields = Nil,
    alignedSources = Map.empty,
    description = None,
    generateDomainType = true,
    generateMappers = true,
    generateInterface = false,
    generateBuilder = false,
    generateCopy = true
  )
}

// Keep CompositeTypeDefinition as alias for backwards compatibility
type CompositeTypeDefinition = DomainTypeDefinition
val CompositeTypeDefinition: DomainTypeDefinition.type = DomainTypeDefinition

/** Source field information extracted from MetaDb or ParsedSpec */
case class SourceField(
    name: String,
    typeName: String,
    nullable: Boolean,
    isPrimaryKey: Boolean,
    comment: Option[String]
)

/** Entity information from a source */
case class SourceEntity(
    sourceName: String,
    entityPath: String,
    sourceType: SourceEntityType,
    fields: List[SourceField]
)

sealed trait SourceEntityType
object SourceEntityType {
  case object Table extends SourceEntityType
  case object View extends SourceEntityType
  case object Schema extends SourceEntityType
  case object Record extends SourceEntityType
}

/** Alignment computation between domain type and source entity */
object AlignmentComputer {

  def computeAlignment(
      domainType: DomainTypeDefinition,
      alignedSource: AlignedSource,
      sourceEntity: SourceEntity,
      nameAligner: NameAligner
  ): List[FieldAlignmentResult] = {
    val sourceFieldMap = sourceEntity.fields.map(f => f.name -> f).toMap
    val usedSourceFields = scala.collection.mutable.Set[String]()

    val domainResults = domainType.fields.map { domainField =>
      val sourceFieldName = alignedSource.resolveSourceField(domainField.name)
      val autoAlignedName = nameAligner.align(domainField.name, sourceFieldMap.keys.toList)

      val resolvedSourceField = sourceFieldMap
        .get(sourceFieldName)
        .orElse(autoAlignedName.flatMap(sourceFieldMap.get))

      resolvedSourceField match {
        case Some(sf) =>
          usedSourceFields += sf.name
          val typesMatch = typeCompatible(domainField, sf)
          if (domainField.nullable != sf.nullable) {
            FieldAlignmentResult.NullabilityMismatch(
              domainField.name,
              sf.name,
              domainField.nullable,
              sf.nullable
            )
          } else if (!typesMatch) {
            FieldAlignmentResult.TypeMismatch(
              domainField.name,
              sf.name,
              domainField.typeName,
              sf.typeName
            )
          } else {
            FieldAlignmentResult.Aligned(domainField.name, sf.name, typesMatch)
          }

        case None =>
          FieldAlignmentResult.Missing(domainField.name)
      }
    }

    val extraFields = sourceEntity.fields
      .filterNot(f => usedSourceFields.contains(f.name))
      .filterNot(f => alignedSource.exclude.contains(f.name))
      .map(f => FieldAlignmentResult.Extra(f.name, f.typeName))

    domainResults ++ extraFields
  }

  private def typeCompatible(domainField: DomainField, sourceField: SourceField): Boolean = {
    val domainNorm = TypeNarrower.mapCanonicalToNormalized(domainField.typeName)
    val sourceNorm = TypeNarrower.normalizeDbType(sourceField.typeName)
    domainNorm == sourceNorm || TypeNarrower.areTypesCompatible(domainNorm, sourceNorm)
  }
}

/** Name alignment utilities */
trait NameAligner {
  def align(domainName: String, sourceNames: List[String]): Option[String]
}

/** Default name aligner using tokenization and stemming */
class DefaultNameAligner(
    customAbbreviations: Map[String, String],
    useStemming: Boolean
) extends NameAligner {

  override def align(domainName: String, sourceNames: List[String]): Option[String] = {
    val domainNorm = normalize(domainName)

    sourceNames.find { sourceName =>
      val sourceNorm = normalize(sourceName)
      domainNorm == sourceNorm
    }
  }

  private def normalize(name: String): String = {
    val tokens = ColumnTokenizer.tokenize(name).bestInterpretation.tokens
    val expanded = tokens.map(t => customAbbreviations.getOrElse(t.toLowerCase, t.toLowerCase))
    val stemmed = if (useStemming) expanded.map(ColumnStemmer.stem) else expanded
    stemmed.mkString("")
  }
}

object DefaultNameAligner {
  def default: DefaultNameAligner = new DefaultNameAligner(
    customAbbreviations = Map.empty,
    useStemming = true
  )
}
