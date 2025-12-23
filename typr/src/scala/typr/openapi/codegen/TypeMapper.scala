package typr.openapi.codegen

import typr.jvm
import typr.openapi.{PrimitiveType, TypeInfo}

/** Maps OpenAPI TypeInfo to jvm.Type for code generation */
class TypeMapper(
    modelPkg: jvm.QIdent,
    typeOverrides: Map[String, jvm.Type.Qualified],
    lang: typr.Lang
) {

  /** Map a TypeInfo to a jvm.Type */
  def map(typeInfo: TypeInfo): jvm.Type = typeInfo match {
    case TypeInfo.Primitive(primitiveType) =>
      mapPrimitive(primitiveType)

    case TypeInfo.ListOf(itemType) =>
      lang.ListType.tpe.of(map(itemType))

    case TypeInfo.Optional(underlying) =>
      lang.Optional.tpe(map(underlying))

    case TypeInfo.MapOf(keyType, valueType) =>
      lang.MapOps.tpe.of(map(keyType), map(valueType))

    case TypeInfo.Ref(name) =>
      typeOverrides.getOrElse(name, jvm.Type.Qualified(modelPkg / jvm.Ident(name)))

    case TypeInfo.Any =>
      Types.JsonNode

    case TypeInfo.InlineEnum(_) =>
      // Inline enums should have been extracted during parsing - fallback to String
      lang.String
  }

  /** Map a primitive type to jvm.Type */
  def mapPrimitive(primitiveType: PrimitiveType): jvm.Type = primitiveType match {
    case PrimitiveType.String     => lang.String
    case PrimitiveType.Int32      => lang.Int
    case PrimitiveType.Int64      => lang.Long
    case PrimitiveType.Float      => lang.Float
    case PrimitiveType.Double     => lang.Double
    case PrimitiveType.Boolean    => lang.Boolean
    case PrimitiveType.Date       => Types.LocalDate
    case PrimitiveType.DateTime   => Types.OffsetDateTime
    case PrimitiveType.Time       => Types.LocalTime
    case PrimitiveType.UUID       => Types.UUID
    case PrimitiveType.URI        => Types.URI
    case PrimitiveType.Email      => lang.String // Email is just a string with format validation
    case PrimitiveType.Binary     => Types.ByteArray
    case PrimitiveType.Byte       => lang.String // Base64 encoded string
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
  val Throwable = jvm.Type.Qualified("java.lang.Throwable")
  val RuntimeException = jvm.Type.Qualified("java.lang.RuntimeException")
  val IllegalStateException = jvm.Type.Qualified("java.lang.IllegalStateException")

  // Jackson annotations
  object Jackson {
    val JsonProperty = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonProperty")
    val JsonValue = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonValue")
    val JsonCreator = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonCreator")
    val JsonTypeInfo = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonTypeInfo")
    val JsonSubTypes = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonSubTypes")
    val JsonSubTypesType = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonSubTypes.Type")
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

  // Java standard library types
  object Java {
    val Optional = jvm.Type.Qualified("java.util.Optional")
    val Integer = jvm.Type.Qualified("java.lang.Integer")
    val Long = jvm.Type.Qualified("java.lang.Long")
    def Function(inputType: jvm.Type, outputType: jvm.Type): jvm.Type =
      jvm.Type.TApply(jvm.Type.Qualified("java.util.function.Function"), List(inputType, outputType))
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
    val Response = jvm.Type.Qualified("jakarta.ws.rs.core.Response")
    val GenericType = jvm.Type.Qualified("jakarta.ws.rs.core.GenericType")
    val WebApplicationException = jvm.Type.Qualified("jakarta.ws.rs.WebApplicationException")
  }

  // Spring Boot / Spring MVC annotations
  object Spring {
    val RestController = jvm.Type.Qualified("org.springframework.web.bind.annotation.RestController")
    val RequestMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.RequestMapping")
    val GetMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.GetMapping")
    val PostMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.PostMapping")
    val PutMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.PutMapping")
    val DeleteMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.DeleteMapping")
    val PatchMapping = jvm.Type.Qualified("org.springframework.web.bind.annotation.PatchMapping")
    val PathVariable = jvm.Type.Qualified("org.springframework.web.bind.annotation.PathVariable")
    val RequestParam = jvm.Type.Qualified("org.springframework.web.bind.annotation.RequestParam")
    val RequestHeader = jvm.Type.Qualified("org.springframework.web.bind.annotation.RequestHeader")
    val CookieValue = jvm.Type.Qualified("org.springframework.web.bind.annotation.CookieValue")
    val RequestBody = jvm.Type.Qualified("org.springframework.web.bind.annotation.RequestBody")
    val RequestPart = jvm.Type.Qualified("org.springframework.web.bind.annotation.RequestPart")
    val ResponseBody = jvm.Type.Qualified("org.springframework.web.bind.annotation.ResponseBody")
    val ResponseStatus = jvm.Type.Qualified("org.springframework.web.bind.annotation.ResponseStatus")
    val HttpStatus = jvm.Type.Qualified("org.springframework.http.HttpStatus")
    val MediaType = jvm.Type.Qualified("org.springframework.http.MediaType")
    val MultipartFile = jvm.Type.Qualified("org.springframework.web.multipart.MultipartFile")
    val ResponseEntity = jvm.Type.Qualified("org.springframework.http.ResponseEntity")
    val WebClientResponseException = jvm.Type.Qualified("org.springframework.web.reactive.function.client.WebClientResponseException")
    val HttpStatusCodeException = jvm.Type.Qualified("org.springframework.web.client.HttpStatusCodeException")
  }

  // Swagger/OpenAPI annotations for security documentation
  object OpenApiAnnotations {
    val SecurityRequirement = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.SecurityRequirement")
    val SecurityRequirements = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.SecurityRequirements")
    val SecurityScheme = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.SecurityScheme")
    val SecuritySchemes = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.SecuritySchemes")
    val OAuthFlow = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.OAuthFlow")
    val OAuthFlows = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.OAuthFlows")
    val OAuthScope = jvm.Type.Qualified("io.swagger.v3.oas.annotations.security.OAuthScope")
    val SecuritySchemeType = jvm.Type.Qualified("io.swagger.v3.oas.annotations.enums.SecuritySchemeType")
    val SecuritySchemeIn = jvm.Type.Qualified("io.swagger.v3.oas.annotations.enums.SecuritySchemeIn")
  }

  // JAX-RS Multipart types
  object JaxRsMultipart {
    val FormDataParam = jvm.Type.Qualified("org.glassfish.jersey.media.multipart.FormDataParam")
    val FormDataContentDisposition = jvm.Type.Qualified("org.glassfish.jersey.media.multipart.FormDataContentDisposition")
  }

  // File/IO types
  val InputStream = jvm.Type.Qualified("java.io.InputStream")
  val IOException = jvm.Type.Qualified("java.io.IOException")
  val InterruptedException = jvm.Type.Qualified("java.lang.InterruptedException")

  // Exception type that covers both IOException and InterruptedException
  val Exception = jvm.Type.Qualified("java.lang.Exception")

  /** Error type - typically in the model package. Used for default/error response types. */
  def Error(apiPkg: jvm.QIdent): jvm.Type.Qualified = {
    // Error is typically in sibling model package, go up to parent and then to model
    val parentPkg = apiPkg.idents.init // Remove "api" segment
    val modelPkg = jvm.QIdent(parentPkg :+ jvm.Ident("model"))
    jvm.Type.Qualified(modelPkg / jvm.Ident("Error"))
  }

  // JDK HTTP Client types (java.net.http)
  object JdkHttp {
    val HttpClient = jvm.Type.Qualified("java.net.http.HttpClient")
    val HttpRequest = jvm.Type.Qualified("java.net.http.HttpRequest")
    val HttpResponse = jvm.Type.Qualified("java.net.http.HttpResponse")
    val BodyPublishers = jvm.Type.Qualified("java.net.http.HttpRequest.BodyPublishers")
    val BodyHandlers = jvm.Type.Qualified("java.net.http.HttpResponse.BodyHandlers")
  }

  // Jackson ObjectMapper
  object JacksonMapper {
    val ObjectMapper = jvm.Type.Qualified("com.fasterxml.jackson.databind.ObjectMapper")
    val TypeReference = jvm.Type.Qualified("com.fasterxml.jackson.core.type.TypeReference")
  }

  // HTTP4s types
  object Http4s {
    val ResponseCtor = jvm.Type.Qualified("org.http4s.Response")
    val Response: jvm.Type = jvm.Type.TApply(ResponseCtor, List(Cats.IO)) // Response[IO]
    val Request = jvm.Type.Qualified("org.http4s.Request")
    val Status = jvm.Type.Qualified("org.http4s.Status")
    val Uri = jvm.Type.Qualified("org.http4s.Uri")
    val Method = jvm.Type.Qualified("org.http4s.Method")
    val HttpRoutes = jvm.Type.Qualified("org.http4s.HttpRoutes")
    val Client = jvm.Type.Qualified("org.http4s.client.Client")
    val EntityDecoder = jvm.Type.Qualified("org.http4s.EntityDecoder")
    val EntityEncoder = jvm.Type.Qualified("org.http4s.EntityEncoder")
    val UnexpectedStatus = jvm.Type.Qualified("org.http4s.client.UnexpectedStatus")
    val CIString = jvm.Type.Qualified("org.typelevel.ci.CIString")
    val SegmentEncoder = jvm.Type.Qualified("org.http4s.Uri.Path.SegmentEncoder")
  }

  // Cats Effect types
  object Cats {
    val IO = jvm.Type.Qualified("cats.effect.IO")
    val Async = jvm.Type.Qualified("cats.effect.Async")
    val Concurrent = jvm.Type.Qualified("cats.effect.Concurrent")
  }

  // Circe types for JSON
  object Circe {
    val Json = jvm.Type.Qualified("io.circe.Json")
    val Encoder = jvm.Type.Qualified("io.circe.Encoder")
    val Decoder = jvm.Type.Qualified("io.circe.Decoder")
    val DecodingFailure = jvm.Type.Qualified("io.circe.DecodingFailure")
    val deriveEncoder = jvm.Type.Qualified("io.circe.generic.semiauto.deriveEncoder")
    val deriveDecoder = jvm.Type.Qualified("io.circe.generic.semiauto.deriveDecoder")
  }
}

object OpenApiTypesScala {

  /** Annotation for suppressing variance checking */
  val UncheckedVariance = jvm.Type.Qualified("scala.annotation.unchecked.uncheckedVariance")
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
  val Some = jvm.Type.Qualified("scala.Some")
  val List = jvm.Type.Qualified("scala.List")
  val Map = jvm.Type.Qualified("scala.collection.immutable.Map")
  val JsonNode = jvm.Type.Qualified("io.circe.Json") // For Scala, use Circe's Json type
  val Void = jvm.Type.Qualified("scala.Unit")
}

/** Scala-specific TypeMapper */
class ScalaTypeMapper(
    modelPkg: jvm.QIdent,
    typeOverrides: Map[String, jvm.Type.Qualified],
    lang: typr.Lang
) extends TypeMapper(modelPkg, typeOverrides, lang) {

  /** Map a TypeInfo to a jvm.Type - delegates to parent for Optional/List/Map to respect TypeSupport */
  override def map(typeInfo: TypeInfo): jvm.Type = typeInfo match {
    case TypeInfo.Primitive(primitiveType) =>
      mapPrimitive(primitiveType)

    case TypeInfo.ListOf(itemType) =>
      lang.ListType.tpe.of(map(itemType))

    case TypeInfo.Optional(underlying) =>
      lang.Optional.tpe(map(underlying))

    case TypeInfo.MapOf(keyType, valueType) =>
      lang.MapOps.tpe.of(map(keyType), map(valueType))

    case TypeInfo.Ref(name) =>
      typeOverrides.getOrElse(name, jvm.Type.Qualified(modelPkg / jvm.Ident(name)))

    case TypeInfo.Any =>
      ScalaTypes.JsonNode

    case TypeInfo.InlineEnum(_) =>
      // Inline enums should have been extracted during parsing - fallback to String
      ScalaTypes.String
  }

  /** Map a primitive type to jvm.Type - uses lang for primitives to respect TypeSupport */
  override def mapPrimitive(primitiveType: PrimitiveType): jvm.Type = primitiveType match {
    case PrimitiveType.String     => lang.String
    case PrimitiveType.Int32      => lang.Int
    case PrimitiveType.Int64      => lang.Long
    case PrimitiveType.Float      => lang.Float
    case PrimitiveType.Double     => lang.Double
    case PrimitiveType.Boolean    => lang.Boolean
    case PrimitiveType.Date       => ScalaTypes.LocalDate
    case PrimitiveType.DateTime   => ScalaTypes.OffsetDateTime
    case PrimitiveType.Time       => ScalaTypes.LocalTime
    case PrimitiveType.UUID       => ScalaTypes.UUID
    case PrimitiveType.URI        => ScalaTypes.URI
    case PrimitiveType.Email      => lang.String
    case PrimitiveType.Binary     => ScalaTypes.ByteArray
    case PrimitiveType.Byte       => ScalaTypes.String
    case PrimitiveType.BigDecimal => ScalaTypes.BigDecimal
  }
}
