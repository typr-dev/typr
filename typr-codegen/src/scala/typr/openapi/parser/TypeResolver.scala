package typr.openapi.parser

import io.swagger.v3.oas.models.media.Schema
import typr.openapi.{PrimitiveType, TypeInfo}

import scala.jdk.CollectionConverters._

/** Resolves OpenAPI schemas to TypeInfo intermediate representation */
object TypeResolver {

  /** Convert an OpenAPI Schema to TypeInfo */
  def resolve(schema: Schema[?], required: Boolean): TypeInfo = {
    val baseType = resolveBase(schema)
    // OpenAPI 3.0 uses nullable: true, OpenAPI 3.1 uses type: ["string", "null"]
    val nullable = Option(schema.getNullable).contains(java.lang.Boolean.TRUE) ||
      hasNullType(schema)

    if (!required || nullable) TypeInfo.Optional(baseType)
    else baseType
  }

  /** Check if schema has null in its type array (OpenAPI 3.1 style) */
  private def hasNullType(schema: Schema[?]): Boolean = {
    Option(schema.getTypes).exists(_.asScala.contains("null"))
  }

  /** Convert an OpenAPI Schema to TypeInfo without wrapping in Optional */
  def resolveBase(schema: Schema[?]): TypeInfo = {
    if (schema == null) return TypeInfo.Any

    // Handle $ref first
    val ref = schema.get$ref()
    if (ref != null && ref.nonEmpty) {
      return TypeInfo.Ref(extractRefClassName(ref))
    }

    // Handle enum
    val enumValues = Option(schema.getEnum).map(_.asScala.toList.map(_.toString))
    if (enumValues.exists(_.nonEmpty)) {
      return TypeInfo.InlineEnum(enumValues.get)
    }

    // OpenAPI 3.0 uses getType(), OpenAPI 3.1 uses getTypes() array
    // For type arrays like ["string", "null"], filter out "null" to get the actual type
    val schemaType = Option(schema.getType).orElse(
      Option(schema.getTypes).flatMap(_.asScala.filterNot(_ == "null").headOption)
    )

    schemaType match {
      case Some("string")  => resolveStringType(schema)
      case Some("integer") => resolveIntegerType(schema)
      case Some("number")  => resolveNumberType(schema)
      case Some("boolean") => TypeInfo.Primitive(PrimitiveType.Boolean)
      case Some("array")   => resolveArrayType(schema)
      case Some("object")  => resolveObjectType(schema)
      case Some("null")    => TypeInfo.Any
      case None            =>
        // No type specified - could be a composed schema (allOf, oneOf, anyOf)
        if (schema.getAllOf != null && !schema.getAllOf.isEmpty) {
          // For allOf, we'd typically need to merge schemas - for now return Any
          TypeInfo.Any
        } else if (schema.getOneOf != null && !schema.getOneOf.isEmpty) {
          // For oneOf without discriminator, return Any
          TypeInfo.Any
        } else if (schema.getAnyOf != null && !schema.getAnyOf.isEmpty) {
          TypeInfo.Any
        } else {
          TypeInfo.Any
        }
      case Some(_) =>
        // Unknown type
        TypeInfo.Any
    }
  }

  private def resolveStringType(schema: Schema[?]): TypeInfo = {
    val format = Option(schema.getFormat)
    TypeInfo.Primitive(format match {
      case Some("date")      => PrimitiveType.Date
      case Some("date-time") => PrimitiveType.DateTime
      case Some("time")      => PrimitiveType.Time
      case Some("uuid")      => PrimitiveType.UUID
      case Some("uri")       => PrimitiveType.URI
      case Some("email")     => PrimitiveType.Email
      case Some("binary")    => PrimitiveType.Binary
      case Some("byte")      => PrimitiveType.Byte
      case _                 => PrimitiveType.String
    })
  }

  private def resolveIntegerType(schema: Schema[?]): TypeInfo = {
    val format = Option(schema.getFormat)
    TypeInfo.Primitive(format match {
      case Some("int32") => PrimitiveType.Int32
      case Some("int64") => PrimitiveType.Int64
      case _             => PrimitiveType.Int64 // Default to Long for safety
    })
  }

  private def resolveNumberType(schema: Schema[?]): TypeInfo = {
    val format = Option(schema.getFormat)
    TypeInfo.Primitive(format match {
      case Some("float")   => PrimitiveType.Float
      case Some("double")  => PrimitiveType.Double
      case Some("decimal") => PrimitiveType.BigDecimal
      case _               => PrimitiveType.Double // Default to Double
    })
  }

  private def resolveArrayType(schema: Schema[?]): TypeInfo = {
    val itemSchema = schema.getItems
    val itemType = if (itemSchema != null) resolveBase(itemSchema) else TypeInfo.Any
    TypeInfo.ListOf(itemType)
  }

  private def resolveObjectType(schema: Schema[?]): TypeInfo = {
    // Check for additionalProperties (map type)
    val additionalProps = schema.getAdditionalProperties
    additionalProps match {
      case ap: Schema[_] =>
        val valueType = resolveBase(ap)
        TypeInfo.MapOf(TypeInfo.Primitive(PrimitiveType.String), valueType)
      case b: java.lang.Boolean if b =>
        // additionalProperties: true means any value
        TypeInfo.MapOf(TypeInfo.Primitive(PrimitiveType.String), TypeInfo.Any)
      case _ =>
        // Regular object with properties - this should be handled as a named type
        // If we get here, it's an inline object which we treat as Any for now
        TypeInfo.Any
    }
  }

  /** Extract the raw type name from a $ref path like "#/components/schemas/TypeName". Returns the unsanitized name for schema lookups.
    */
  def extractRefName(ref: String): String = {
    if (ref.startsWith("#/components/schemas/")) {
      ref.substring("#/components/schemas/".length)
    } else if (ref.contains("/")) {
      ref.substring(ref.lastIndexOf('/') + 1)
    } else {
      ref
    }
  }

  /** Extract the sanitized class name from a $ref path. Use this when creating TypeInfo.Ref for code generation.
    */
  def extractRefClassName(ref: String): String = {
    sanitizeClassName(extractRefName(ref))
  }

  /** Convert a raw OpenAPI schema name to a valid JVM class name. Handles names like "tax.transaction_line_item" -> "TaxTransactionLineItem"
    */
  def sanitizeClassName(name: String): String = {
    name
      .split("[^a-zA-Z0-9]")
      .filter(_.nonEmpty)
      .map(_.capitalize)
      .mkString
  }
}
