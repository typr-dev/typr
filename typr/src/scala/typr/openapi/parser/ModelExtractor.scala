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

  /** Extract all model classes and sum types from the OpenAPI spec */
  def extract(openApi: OpenAPI): ExtractedModels = {
    val schemas = Option(openApi.getComponents)
      .flatMap(c => Option(c.getSchemas))
      .map(_.asScala.toMap)
      .getOrElse(Map.empty[String, Schema[_]])

    val models = List.newBuilder[ModelClass]
    val sumTypes = List.newBuilder[SumType]

    schemas.foreach { case (name, schema) =>
      val sanitizedName = TypeResolver.sanitizeClassName(name)
      extractSchema(sanitizedName, schema, schemas) match {
        case Left(model)   => models += model
        case Right(sumTyp) => sumTypes += sumTyp
      }
    }

    ExtractedModels(models.result(), sumTypes.result())
  }

  private def extractSchema(
      name: String,
      schema: Schema[_],
      allSchemas: Map[String, Schema[_]]
  ): Either[ModelClass, SumType] = {
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
      Right(extractSumType(name, description, schema, oneOf))
    } else if (oneOf.nonEmpty) {
      // oneOf without discriminator - this is not supported for type-safe code generation
      throw new IllegalArgumentException(
        s"Schema '$name' uses oneOf without a discriminator. " +
          "Discriminators are required for type-safe code generation. " +
          "Add 'discriminator' with 'propertyName' and 'mapping' to the schema."
      )
    } else {
      // Regular schema
      Left(extractModelClass(name, schema, allSchemas))
    }
  }

  private def extractModelClass(
      name: String,
      schema: Schema[_],
      allSchemas: Map[String, Schema[_]]
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
      allOf: List[Schema[_]],
      allSchemas: Map[String, Schema[_]]
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

  private def extractProperties(schema: Schema[_]): List[Property] = {
    val properties = Option(schema.getProperties).map(_.asScala.toMap).getOrElse(Map.empty[String, Schema[_]])
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
      schema: Schema[_],
      oneOf: List[Schema[_]]
  ): SumType = {
    val discriminator = schema.getDiscriminator
    val propertyName = discriminator.getPropertyName
    val mapping = Option(discriminator.getMapping)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty[String, String])

    // Invert mapping: type name -> discriminator value (use sanitized class names)
    val invertedMapping = mapping.map { case (value, ref) =>
      TypeResolver.extractRefClassName(ref) -> value
    }

    val subtypeNames = oneOf.map { subSchema =>
      if (subSchema.get$ref() != null) {
        TypeResolver.extractRefClassName(subSchema.get$ref())
      } else {
        // Inline schema - would need to generate a name
        s"${name}Subtype"
      }
    }

    SumType(
      name = name,
      description = description,
      discriminator = Discriminator(propertyName, invertedMapping),
      commonProperties = Nil, // Will be populated during analysis
      subtypeNames = subtypeNames,
      parentSumTypes = Nil
    )
  }

  private def inferEnumUnderlyingType(schema: Schema[_]): PrimitiveType = {
    val schemaType = Option(schema.getType).orElse(Option(schema.getTypes).flatMap(_.asScala.headOption))
    schemaType match {
      case Some("integer") => PrimitiveType.Int32
      case _               => PrimitiveType.String
    }
  }

  private def extractValidationConstraints(schema: Schema[_]): ValidationConstraints = {
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
