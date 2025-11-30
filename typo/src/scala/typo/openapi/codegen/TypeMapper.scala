package typo.openapi.codegen

import typo.jvm
import typo.openapi.{PrimitiveType, TypeInfo}

/** Maps OpenAPI TypeInfo to jvm.Type for code generation */
class TypeMapper(
    modelPkg: jvm.QIdent,
    typeOverrides: Map[String, jvm.Type.Qualified],
    lang: typo.Lang
) {

  /** Map a TypeInfo to a jvm.Type */
  def map(typeInfo: TypeInfo): jvm.Type = typeInfo match {
    case TypeInfo.Primitive(primitiveType) =>
      mapPrimitive(primitiveType)

    case TypeInfo.ListOf(itemType) =>
      lang.ListType.tpe.of(map(itemType))

    case TypeInfo.Optional(underlying) =>
      lang.Optional.tpe.of(map(underlying))

    case TypeInfo.MapOf(keyType, valueType) =>
      lang.MapOps.tpe.of(map(keyType), map(valueType))

    case TypeInfo.Ref(name) =>
      typeOverrides.getOrElse(name, jvm.Type.Qualified(modelPkg / jvm.Ident(name)))

    case TypeInfo.Any =>
      Types.JsonNode

    case TypeInfo.InlineEnum(_) =>
      // Inline enums should have been extracted during parsing - fallback to String
      Types.String
  }

  /** Map a primitive type to jvm.Type */
  def mapPrimitive(primitiveType: PrimitiveType): jvm.Type = primitiveType match {
    case PrimitiveType.String     => Types.String
    case PrimitiveType.Int32      => Types.Int
    case PrimitiveType.Int64      => Types.Long
    case PrimitiveType.Float      => Types.Float
    case PrimitiveType.Double     => Types.Double
    case PrimitiveType.Boolean    => Types.Boolean
    case PrimitiveType.Date       => Types.LocalDate
    case PrimitiveType.DateTime   => Types.OffsetDateTime
    case PrimitiveType.Time       => Types.LocalTime
    case PrimitiveType.UUID       => Types.UUID
    case PrimitiveType.URI        => Types.URI
    case PrimitiveType.Email      => Types.String // Email is just a string with format validation
    case PrimitiveType.Binary     => Types.ByteArray
    case PrimitiveType.Byte       => Types.String // Base64 encoded string
    case PrimitiveType.BigDecimal => Types.BigDecimal
  }
}

/** Common JVM types used in OpenAPI code generation - Java edition */
object Types {
  // These are Java types - for Scala use ScalaTypes
  val String = jvm.Type.Qualified("java.lang.String")
  val Int = jvm.Type.Qualified("java.lang.Integer")
  val Long = jvm.Type.Qualified("java.lang.Long")
  val Float = jvm.Type.Qualified("java.lang.Float")
  val Double = jvm.Type.Qualified("java.lang.Double")
  val Boolean = jvm.Type.Qualified("java.lang.Boolean")
  val LocalDate = jvm.Type.Qualified("java.time.LocalDate")
  val OffsetDateTime = jvm.Type.Qualified("java.time.OffsetDateTime")
  val LocalTime = jvm.Type.Qualified("java.time.LocalTime")
  val UUID = jvm.Type.Qualified("java.util.UUID")
  val URI = jvm.Type.Qualified("java.net.URI")
  val BigDecimal = jvm.Type.Qualified("java.math.BigDecimal")
  val ByteArray = jvm.Type.ArrayOf(jvm.Type.Qualified("java.lang.Byte"))
  val JsonNode = jvm.Type.Qualified("com.fasterxml.jackson.databind.JsonNode")
  val Void = jvm.Type.Qualified("java.lang.Void")

  // Jackson annotations
  object Jackson {
    val JsonProperty = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonProperty")
    val JsonValue = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonValue")
    val JsonCreator = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonCreator")
    val JsonTypeInfo = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonTypeInfo")
    val JsonSubTypes = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonSubTypes")
    val JsonDeserialize = jvm.Type.Qualified("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
    val JsonSerialize = jvm.Type.Qualified("com.fasterxml.jackson.databind.annotation.JsonSerialize")
  }

  // JSR-380 Bean Validation annotations (Jakarta Validation)
  object Validation {
    val NotNull = jvm.Type.Qualified("jakarta.validation.constraints.NotNull")
    val NotBlank = jvm.Type.Qualified("jakarta.validation.constraints.NotBlank")
    val NotEmpty = jvm.Type.Qualified("jakarta.validation.constraints.NotEmpty")
    val Size = jvm.Type.Qualified("jakarta.validation.constraints.Size")
    val Pattern = jvm.Type.Qualified("jakarta.validation.constraints.Pattern")
    val Min = jvm.Type.Qualified("jakarta.validation.constraints.Min")
    val Max = jvm.Type.Qualified("jakarta.validation.constraints.Max")
    val DecimalMin = jvm.Type.Qualified("jakarta.validation.constraints.DecimalMin")
    val DecimalMax = jvm.Type.Qualified("jakarta.validation.constraints.DecimalMax")
    val Email = jvm.Type.Qualified("jakarta.validation.constraints.Email")
    val Valid = jvm.Type.Qualified("jakarta.validation.Valid")
  }

  // JAX-RS annotations (Jakarta EE / javax.ws.rs)
  object JaxRs {
    val Path = jvm.Type.Qualified("jakarta.ws.rs.Path")
    val GET = jvm.Type.Qualified("jakarta.ws.rs.GET")
    val POST = jvm.Type.Qualified("jakarta.ws.rs.POST")
    val PUT = jvm.Type.Qualified("jakarta.ws.rs.PUT")
    val DELETE = jvm.Type.Qualified("jakarta.ws.rs.DELETE")
    val PATCH = jvm.Type.Qualified("jakarta.ws.rs.PATCH")
    val HEAD = jvm.Type.Qualified("jakarta.ws.rs.HEAD")
    val OPTIONS = jvm.Type.Qualified("jakarta.ws.rs.OPTIONS")
    val PathParam = jvm.Type.Qualified("jakarta.ws.rs.PathParam")
    val QueryParam = jvm.Type.Qualified("jakarta.ws.rs.QueryParam")
    val HeaderParam = jvm.Type.Qualified("jakarta.ws.rs.HeaderParam")
    val CookieParam = jvm.Type.Qualified("jakarta.ws.rs.CookieParam")
    val DefaultValue = jvm.Type.Qualified("jakarta.ws.rs.DefaultValue")
    val Consumes = jvm.Type.Qualified("jakarta.ws.rs.Consumes")
    val Produces = jvm.Type.Qualified("jakarta.ws.rs.Produces")
    val MediaType = jvm.Type.Qualified("jakarta.ws.rs.core.MediaType")
  }
}

/** Scala types for OpenAPI code generation */
object ScalaTypes {
  val String = jvm.Type.Qualified("java.lang.String") // String is the same
  val Int = jvm.Type.Qualified("scala.Int")
  val Long = jvm.Type.Qualified("scala.Long")
  val Float = jvm.Type.Qualified("scala.Float")
  val Double = jvm.Type.Qualified("scala.Double")
  val Boolean = jvm.Type.Qualified("scala.Boolean")
  val LocalDate = jvm.Type.Qualified("java.time.LocalDate")
  val OffsetDateTime = jvm.Type.Qualified("java.time.OffsetDateTime")
  val LocalTime = jvm.Type.Qualified("java.time.LocalTime")
  val UUID = jvm.Type.Qualified("java.util.UUID")
  val URI = jvm.Type.Qualified("java.net.URI")
  val BigDecimal = jvm.Type.Qualified("scala.math.BigDecimal")
  val ByteArray = jvm.Type.ArrayOf(jvm.Type.Qualified("scala.Byte"))
  val Option = jvm.Type.Qualified("scala.Option")
  val List = jvm.Type.Qualified("scala.List")
  val Map = jvm.Type.Qualified("scala.collection.immutable.Map")
  val JsonNode = jvm.Type.Qualified("io.circe.Json") // For Scala, use Circe's Json type
  val Void = jvm.Type.Qualified("scala.Unit")
}

/** Scala-specific TypeMapper */
class ScalaTypeMapper(
    modelPkg: jvm.QIdent,
    typeOverrides: Map[String, jvm.Type.Qualified],
    lang: typo.Lang
) extends TypeMapper(modelPkg, typeOverrides, lang) {

  /** Map a TypeInfo to a jvm.Type */
  override def map(typeInfo: TypeInfo): jvm.Type = typeInfo match {
    case TypeInfo.Primitive(primitiveType) =>
      mapPrimitive(primitiveType)

    case TypeInfo.ListOf(itemType) =>
      ScalaTypes.List.of(map(itemType))

    case TypeInfo.Optional(underlying) =>
      ScalaTypes.Option.of(map(underlying))

    case TypeInfo.MapOf(keyType, valueType) =>
      ScalaTypes.Map.of(map(keyType), map(valueType))

    case TypeInfo.Ref(name) =>
      typeOverrides.getOrElse(name, jvm.Type.Qualified(modelPkg / jvm.Ident(name)))

    case TypeInfo.Any =>
      ScalaTypes.JsonNode

    case TypeInfo.InlineEnum(_) =>
      // Inline enums should have been extracted during parsing - fallback to String
      ScalaTypes.String
  }

  /** Map a primitive type to jvm.Type */
  override def mapPrimitive(primitiveType: PrimitiveType): jvm.Type = primitiveType match {
    case PrimitiveType.String     => ScalaTypes.String
    case PrimitiveType.Int32      => ScalaTypes.Int
    case PrimitiveType.Int64      => ScalaTypes.Long
    case PrimitiveType.Float      => ScalaTypes.Float
    case PrimitiveType.Double     => ScalaTypes.Double
    case PrimitiveType.Boolean    => ScalaTypes.Boolean
    case PrimitiveType.Date       => ScalaTypes.LocalDate
    case PrimitiveType.DateTime   => ScalaTypes.OffsetDateTime
    case PrimitiveType.Time       => ScalaTypes.LocalTime
    case PrimitiveType.UUID       => ScalaTypes.UUID
    case PrimitiveType.URI        => ScalaTypes.URI
    case PrimitiveType.Email      => ScalaTypes.String
    case PrimitiveType.Binary     => ScalaTypes.ByteArray
    case PrimitiveType.Byte       => ScalaTypes.String
    case PrimitiveType.BigDecimal => ScalaTypes.BigDecimal
  }
}
