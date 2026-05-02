package typr.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import typr.openapi._

import scala.jdk.CollectionConverters._

/** Extracts model classes and sum types from OpenAPI schemas */
object ModelExtractor {

  case class ExtractedModels(
      models: List[ModelClass],
      sumTypes: List[SumType]
  )

  /** How a oneOf union can be discriminated */
  sealed trait DiscriminationStrategy
  object DiscriminationStrategy {

    /** Explicit discriminator property with mapping */
    case class PropertyBased(propertyName: String, mapping: Map[String, String]) extends DiscriminationStrategy

    /** Inferred from JSON types (string, array, object, boolean, number) */
    case class TypeBased(variantTypes: Map[String, InferredVariant]) extends DiscriminationStrategy
  }

  /** Information about an inferred oneOf variant */
  case class InferredVariant(
      jsonType: String,
      constValue: Option[String],
      schema: Schema[?]
  )

  /** Extract all model classes and sum types from the OpenAPI spec */
  def extract(openApi: OpenAPI): ExtractedModels = {
    val schemas = Option(openApi.getComponents)
      .flatMap(c => Option(c.getSchemas))
      .map(_.asScala.toMap)
      .getOrElse(Map.empty[String, Schema[?]])

    val models = List.newBuilder[ModelClass]
    val sumTypes = List.newBuilder[SumType]

    schemas.foreach { case (name, schema) =>
      val sanitizedName = TypeResolver.sanitizeClassName(name)
      val (extracted, maybeSumType) = extractSchema(sanitizedName, schema, schemas)
      models ++= extracted
      maybeSumType.foreach(sumTypes += _)
    }

    ExtractedModels(models.result(), sumTypes.result())
  }

  private def extractSchema(
      name: String,
      schema: Schema[?],
      allSchemas: Map[String, Schema[?]]
  ): (List[ModelClass], Option[SumType]) = {
    val description = Option(schema.getDescription)

    // Check for discriminator (sum type)
    val discriminator = Option(schema.getDiscriminator)
    val oneOf = Option(schema.getOneOf).map(_.asScala.toList).getOrElse(Nil)

    if (discriminator.isDefined && oneOf.nonEmpty) {
      // This is a sum type (discriminated union)
      val disc = discriminator.get
      // Validate that discriminator has mapping
      if (Option(disc.getMapping).forall(_.isEmpty)) {
        throw new IllegalArgumentException(
          s"Schema '$name' uses oneOf with discriminator but no mapping defined. " +
            "Discriminator mapping is required for type-safe code generation. " +
            "Add 'mapping' to the discriminator object."
        )
      }
      val sumType = extractSumType(
        name,
        description,
        schema,
        oneOf,
        DiscriminationStrategy.PropertyBased(
          disc.getPropertyName,
          Option(disc.getMapping).map(_.asScala.toMap).getOrElse(Map.empty)
        )
      )
      (Nil, Some(sumType))
    } else if (oneOf.nonEmpty) {
      // oneOf without discriminator - try to infer discrimination strategy
      tryInferDiscrimination(name, oneOf) match {
        case Some(strategy @ DiscriminationStrategy.TypeBased(variantTypes)) =>
          val sumType = extractSumType(name, description, schema, oneOf, strategy)
          // Generate ModelClass entries for inline variants
          val subtypeModels = generateInlineVariants(name, variantTypes, allSchemas)
          (subtypeModels, Some(sumType))
        case None =>
          throw new IllegalArgumentException(
            s"Schema '$name' uses oneOf without a discriminator and variants cannot be distinguished by type. " +
              "Add 'discriminator' with 'propertyName' and 'mapping' to the schema, " +
              "or ensure variants have different JSON types (string, array, object, boolean, number)."
          )
      }
    } else {
      // Regular schema
      (List(extractModelClass(name, schema, allSchemas)), None)
    }
  }

  /** Generate ModelClass entries for inline oneOf variants */
  private def generateInlineVariants(
      parentName: String,
      variantTypes: Map[String, InferredVariant],
      allSchemas: Map[String, Schema[?]]
  ): List[ModelClass] = {
    variantTypes.toList.map { case (subtypeName, variant) =>
      variant.jsonType match {
        case "string" =>
          variant.constValue match {
            case Some(constVal) =>
              // Const string like "all" -> enum with single value
              ModelClass.EnumType(
                name = subtypeName,
                description = Some(s"Constant value: $constVal"),
                values = List(constVal),
                underlyingType = PrimitiveType.String,
                sumTypeParents = List(parentName)
              )
            case None =>
              // Regular string -> wrapper type
              ModelClass.WrapperType(
                name = subtypeName,
                description = None,
                underlying = TypeInfo.Primitive(PrimitiveType.String),
                sumTypeParents = List(parentName)
              )
          }

        case "array" =>
          // Array type -> wrapper around list
          val itemType = Option(variant.schema.getItems)
            .map(items => TypeResolver.resolve(items, required = true))
            .getOrElse(TypeInfo.Any)
          ModelClass.WrapperType(
            name = subtypeName,
            description = None,
            underlying = TypeInfo.ListOf(itemType),
            sumTypeParents = List(parentName)
          )

        case "object" =>
          // Object type -> regular class with properties
          val properties = extractProperties(variant.schema)
          ModelClass.ObjectType(
            name = subtypeName,
            description = None,
            properties = properties,
            sumTypeParents = List(parentName),
            discriminatorValues = Map.empty
          )

        case "boolean" =>
          ModelClass.WrapperType(
            name = subtypeName,
            description = None,
            underlying = TypeInfo.Primitive(PrimitiveType.Boolean),
            sumTypeParents = List(parentName)
          )

        case "integer" =>
          ModelClass.WrapperType(
            name = subtypeName,
            description = None,
            underlying = TypeInfo.Primitive(PrimitiveType.Int64),
            sumTypeParents = List(parentName)
          )

        case "number" =>
          ModelClass.WrapperType(
            name = subtypeName,
            description = None,
            underlying = TypeInfo.Primitive(PrimitiveType.Double),
            sumTypeParents = List(parentName)
          )

        case _ =>
          // Fallback to empty object
          ModelClass.ObjectType(
            name = subtypeName,
            description = None,
            properties = Nil,
            sumTypeParents = List(parentName),
            discriminatorValues = Map.empty
          )
      }
    }
  }

  private def extractModelClass(
      name: String,
      schema: Schema[?],
      allSchemas: Map[String, Schema[?]]
  ): ModelClass = {
    val description = Option(schema.getDescription)

    // Check for enum
    val enumValues = Option(schema.getEnum).map(_.asScala.toList.map(_.toString))
    if (enumValues.exists(_.nonEmpty)) {
      return ModelClass.EnumType(
        name = name,
        description = description,
        values = enumValues.get,
        underlyingType = inferEnumUnderlyingType(schema)
      )
    }

    // Check for allOf (composition/flattening)
    val allOf = Option(schema.getAllOf).map(_.asScala.toList).getOrElse(Nil)
    if (allOf.nonEmpty) {
      return extractAllOfModel(name, description, allOf, allSchemas)
    }

    // Check for wrapper type (single property or extending a primitive)
    val schemaType = Option(schema.getType).orElse(Option(schema.getTypes).flatMap(_.asScala.headOption))
    schemaType match {
      case Some("string") | Some("integer") | Some("number") | Some("boolean") =>
        // This is a primitive wrapper
        return ModelClass.WrapperType(
          name = name,
          description = description,
          underlying = TypeResolver.resolveBase(schema)
        )
      case _ => // Continue to object handling
    }

    // Regular object type
    val properties = extractProperties(schema)
    if (properties.isEmpty && schemaType.isEmpty) {
      // Could be an alias to another type
      val ref = schema.get$ref()
      if (ref != null && ref.nonEmpty) {
        return ModelClass.AliasType(
          name = name,
          description = description,
          underlying = TypeInfo.Ref(TypeResolver.extractRefClassName(ref))
        )
      }
    }

    ModelClass.ObjectType(
      name = name,
      description = description,
      properties = properties,
      sumTypeParents = Nil, // Will be populated during linking phase
      discriminatorValues = Map.empty
    )
  }

  private def extractAllOfModel(
      name: String,
      description: Option[String],
      allOf: List[Schema[?]],
      allSchemas: Map[String, Schema[?]]
  ): ModelClass = {
    // Flatten properties from all schemas in allOf
    val allProperties = allOf.flatMap { schema =>
      if (schema.get$ref() != null) {
        // Reference to another schema - get its properties
        val refName = TypeResolver.extractRefName(schema.get$ref())
        allSchemas.get(refName).toList.flatMap(extractProperties)
      } else {
        extractProperties(schema)
      }
    }

    // Deduplicate by property name
    val uniqueProperties = allProperties.groupBy(_.name).map { case (_, props) => props.head }.toList

    ModelClass.ObjectType(
      name = name,
      description = description,
      properties = uniqueProperties,
      sumTypeParents = Nil,
      discriminatorValues = Map.empty
    )
  }

  private def extractProperties(schema: Schema[?]): List[Property] = {
    val properties = Option(schema.getProperties).map(_.asScala.toMap).getOrElse(Map.empty[String, Schema[?]])
    val requiredSet = Option(schema.getRequired).map(_.asScala.toSet).getOrElse(Set.empty[String])

    properties
      .map { case (propName, propSchema) =>
        val isRequired = requiredSet.contains(propName)
        val isNullable = Option(propSchema.getNullable).contains(java.lang.Boolean.TRUE)
        val isDeprecated = Option(propSchema.getDeprecated).contains(java.lang.Boolean.TRUE)

        Property(
          name = sanitizePropertyName(propName),
          originalName = propName,
          description = Option(propSchema.getDescription),
          typeInfo = TypeResolver.resolve(propSchema, isRequired),
          required = isRequired,
          nullable = isNullable,
          deprecated = isDeprecated,
          validation = extractValidationConstraints(propSchema)
        )
      }
      .toList
      .sortBy(_.name)
  }

  private def extractSumType(
      name: String,
      description: Option[String],
      schema: Schema[?],
      oneOf: List[Schema[?]],
      strategy: DiscriminationStrategy
  ): SumType = {
    strategy match {
      case DiscriminationStrategy.PropertyBased(propertyName, mapping) =>
        // Invert mapping: type name -> discriminator value (use sanitized class names)
        val invertedMapping = mapping.map { case (value, ref) =>
          TypeResolver.extractRefClassName(ref) -> value
        }

        val subtypeNames = oneOf.map { subSchema =>
          if (subSchema.get$ref() != null) {
            TypeResolver.extractRefClassName(subSchema.get$ref())
          } else {
            s"${name}Subtype"
          }
        }

        SumType(
          name = name,
          description = description,
          discriminator = Discriminator(propertyName, invertedMapping),
          commonProperties = Nil,
          subtypeNames = subtypeNames,
          parentSumTypes = Nil,
          usesTypeInference = false
        )

      case DiscriminationStrategy.TypeBased(variantTypes) =>
        // Generate subtype names based on JSON type
        val subtypeNames = variantTypes.keys.toList

        // Use synthetic @type property for discrimination (Jackson DEDUCTION handles this)
        val syntheticDiscriminator = Discriminator(
          propertyName = "@type",
          mapping = variantTypes.map { case (subtypeName, variant) =>
            subtypeName -> variant.jsonType
          }
        )

        SumType(
          name = name,
          description = description,
          discriminator = syntheticDiscriminator,
          commonProperties = Nil,
          subtypeNames = subtypeNames,
          parentSumTypes = Nil,
          usesTypeInference = true
        )
    }
  }

  /** Try to infer discrimination strategy from oneOf variants.
    *
    * Returns Some(TypeBased) if all variants have different JSON types, None otherwise.
    */
  private def tryInferDiscrimination(
      parentName: String,
      oneOf: List[Schema[?]]
  ): Option[DiscriminationStrategy.TypeBased] = {
    val variants = oneOf.zipWithIndex.map { case (schema, idx) =>
      val jsonType = inferJsonType(schema)
      val constValue = Option(schema.getConst).map(_.toString)
      val enumValues = Option(schema.getEnum).map(_.asScala.toList.map(_.toString))

      // Generate a subtype name based on type/const/enum
      val subtypeName = constValue match {
        case Some(c) =>
          // Const value like "all" -> "All"
          TypeResolver.sanitizeClassName(c.capitalize)
        case None =>
          enumValues match {
            case Some(values) if values.nonEmpty =>
              // Enum variant
              s"${parentName}Enum"
            case _ =>
              // Use JSON type as suffix
              jsonType match {
                case "string"  => s"${parentName}String"
                case "array"   => s"${parentName}Array"
                case "object"  => s"${parentName}Object"
                case "boolean" => s"${parentName}Boolean"
                case "number"  => s"${parentName}Number"
                case "integer" => s"${parentName}Integer"
                case _         => s"${parentName}Variant$idx"
              }
          }
      }

      (subtypeName, InferredVariant(jsonType, constValue, schema))
    }

    // Check if JSON types are unique (allows discrimination)
    val typeGroups = variants.groupBy(_._2.jsonType)
    val allUniqueTypes = typeGroups.forall(_._2.size == 1)

    if (allUniqueTypes) {
      Some(DiscriminationStrategy.TypeBased(variants.toMap))
    } else {
      // Check if we can differentiate within same-type groups by const values
      val canDifferentiate = typeGroups.forall { case (_, sameTypeVariants) =>
        if (sameTypeVariants.size == 1) true
        else {
          // Multiple variants with same type - check if all have unique const values
          val consts = sameTypeVariants.map(_._2.constValue)
          consts.forall(_.isDefined) && consts.distinct.size == consts.size
        }
      }

      if (canDifferentiate) {
        Some(DiscriminationStrategy.TypeBased(variants.toMap))
      } else {
        None
      }
    }
  }

  /** Infer the JSON type from a schema */
  private def inferJsonType(schema: Schema[?]): String = {
    // Check explicit type
    val explicitType = Option(schema.getType).orElse(
      Option(schema.getTypes).flatMap(_.asScala.filterNot(_ == "null").headOption)
    )

    explicitType match {
      case Some(t) => t
      case None    =>
        // Infer from structure
        if (schema.get$ref() != null) "object"
        else if (Option(schema.getProperties).exists(!_.isEmpty)) "object"
        else if (schema.getItems != null) "array"
        else if (Option(schema.getEnum).exists(!_.isEmpty)) "string"
        else if (schema.getConst != null) inferConstType(schema.getConst)
        else "object" // Default fallback
    }
  }

  /** Infer type from a const value */
  private def inferConstType(value: Any): String = value match {
    case _: String               => "string"
    case _: java.lang.Boolean    => "boolean"
    case _: java.lang.Integer    => "integer"
    case _: java.lang.Long       => "integer"
    case _: java.lang.Double     => "number"
    case _: java.lang.Float      => "number"
    case _: java.math.BigDecimal => "number"
    case _: java.util.List[?]    => "array"
    case _: java.util.Map[?, ?]  => "object"
    case _                       => "string"
  }

  private def inferEnumUnderlyingType(schema: Schema[?]): PrimitiveType = {
    val schemaType = Option(schema.getType).orElse(Option(schema.getTypes).flatMap(_.asScala.headOption))
    schemaType match {
      case Some("integer") => PrimitiveType.Int32
      case _               => PrimitiveType.String
    }
  }

  private def extractValidationConstraints(schema: Schema[?]): ValidationConstraints = {
    val format = Option(schema.getFormat)
    val isEmail = format.contains("email")

    // Helper to convert java.math.BigDecimal to scala.math.BigDecimal
    def toBigDecimal(bd: java.math.BigDecimal): Option[BigDecimal] =
      if (bd == null) None else Some(BigDecimal(bd))

    ValidationConstraints(
      minLength = Option(schema.getMinLength).map(_.intValue),
      maxLength = Option(schema.getMaxLength).map(_.intValue),
      pattern = Option(schema.getPattern),
      minimum = toBigDecimal(schema.getMinimum),
      maximum = toBigDecimal(schema.getMaximum),
      exclusiveMinimum = toBigDecimal(schema.getExclusiveMinimumValue),
      exclusiveMaximum = toBigDecimal(schema.getExclusiveMaximumValue),
      minItems = Option(schema.getMinItems).map(_.intValue),
      maxItems = Option(schema.getMaxItems).map(_.intValue),
      uniqueItems = Option(schema.getUniqueItems).contains(java.lang.Boolean.TRUE),
      email = isEmail
    )
  }

  private def sanitizePropertyName(name: String): String = {
    // Convert to valid identifier
    val sanitized = name.replace("-", "_").replace(".", "_").replace("$", "_")
    if (sanitized.headOption.exists(_.isDigit)) s"_$sanitized" else sanitized
  }
}
