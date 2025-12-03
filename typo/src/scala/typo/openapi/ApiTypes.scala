package typo.openapi

/** Represents an API interface grouping related operations (by tag) */
case class ApiInterface(
    name: String,
    description: Option[String],
    methods: List[ApiMethod]
)

/** Represents a single API operation */
case class ApiMethod(
    name: String, // operationId or generated name
    description: Option[String],
    httpMethod: HttpMethod,
    path: String,
    parameters: List[ApiParameter],
    requestBody: Option[RequestBody],
    responses: List[ApiResponse],
    deprecated: Boolean,
    security: List[SecurityRequirement],
    tags: List[String],
    /** Non-empty when there are ≥2 response variants with different types */
    responseVariants: Option[List[ResponseVariant]],
    /** Callbacks defined for this operation */
    callbacks: List[Callback]
)

/** Response variant for multi-status response sum types */
case class ResponseVariant(
    statusCode: String,
    typeInfo: TypeInfo,
    description: Option[String],
    headers: List[ResponseHeader]
)

/** HTTP methods */
sealed trait HttpMethod
object HttpMethod {
  case object Get extends HttpMethod
  case object Post extends HttpMethod
  case object Put extends HttpMethod
  case object Delete extends HttpMethod
  case object Patch extends HttpMethod
  case object Head extends HttpMethod
  case object Options extends HttpMethod
}

/** Parameter location in the request */
sealed trait ParameterIn
object ParameterIn {
  case object Path extends ParameterIn
  case object Query extends ParameterIn
  case object Header extends ParameterIn
  case object Cookie extends ParameterIn
}

/** API parameter */
case class ApiParameter(
    name: String,
    originalName: String, // Original name from spec
    description: Option[String],
    in: ParameterIn,
    typeInfo: TypeInfo,
    required: Boolean,
    deprecated: Boolean,
    defaultValue: Option[String] // Formatted default value
)

/** Request body specification */
case class RequestBody(
    description: Option[String],
    typeInfo: TypeInfo,
    required: Boolean,
    contentType: String, // e.g., "application/json"
    /** Form fields for multipart/form-data requests */
    formFields: List[FormField]
) {
  def isMultipart: Boolean = contentType == "multipart/form-data"
}

/** Form field in multipart request */
case class FormField(
    name: String,
    description: Option[String],
    typeInfo: TypeInfo,
    required: Boolean,
    isBinary: Boolean // true for file uploads (type: string, format: binary)
)

/** API response specification */
case class ApiResponse(
    statusCode: ResponseStatus,
    description: Option[String],
    typeInfo: Option[TypeInfo], // None for empty responses
    contentType: Option[String],
    headers: List[ResponseHeader]
)

/** Response header specification */
case class ResponseHeader(
    name: String,
    description: Option[String],
    typeInfo: TypeInfo,
    required: Boolean
)

/** Response status codes and patterns */
sealed trait ResponseStatus
object ResponseStatus {
  case class Specific(code: Int) extends ResponseStatus
  case object Default extends ResponseStatus

  // Wildcard patterns like 2XX, 4XX, 5XX
  case object Success2XX extends ResponseStatus
  case object ClientError4XX extends ResponseStatus
  case object ServerError5XX extends ResponseStatus
}

/** Security requirement for an operation */
case class SecurityRequirement(
    schemeName: String,
    scopes: List[String]
)

/** Webhook definition (OpenAPI 3.1+) */
case class Webhook(
    name: String,
    description: Option[String],
    methods: List[ApiMethod]
)

/** Callback definition - an endpoint the API will call back to after an operation */
case class Callback(
    name: String,
    /** Runtime expression defining the callback URL (e.g., "{$request.body#/callbackUrl}") */
    expression: String,
    /** The operations that will be called on the callback URL */
    methods: List[ApiMethod]
)

/** Represents a unique response shape defined by status codes. Used for deduplicating response types that have the same status code pattern. For example, all methods returning 200+default can share a
  * generic Response200Default[T] type.
  */
case class ResponseShape(
    /** Status codes sorted numerically, "default" comes last. e.g., List("200", "default") */
    statusCodes: List[String],
    /** Whether any status uses a range (4xx, 5xx, default) that requires statusCode field */
    hasRangeStatuses: List[String]
) {

  /** Unique identifier for this shape, e.g., "200_Default" */
  def shapeId: String = statusCodes.map(ResponseShape.normalizeStatusCode).mkString("_")

  /** The generated type name for this shape, e.g., "Response200Default" */
  def typeName: String = "Response" + statusCodes.map(ResponseShape.normalizeStatusCode).mkString("")
}

object ResponseShape {

  /** Normalize a status code for use in type names */
  def normalizeStatusCode(statusCode: String): String = statusCode.toLowerCase match {
    case "default" => "Default"
    case "2xx"     => "2XX"
    case "4xx"     => "4XX"
    case "5xx"     => "5XX"
    case s         => s
  }

  /** Get human-readable HTTP status class name from status code */
  def httpStatusClassName(statusCode: String): String = statusCode.toLowerCase match {
    case "200"     => "Ok"
    case "201"     => "Created"
    case "202"     => "Accepted"
    case "204"     => "NoContent"
    case "301"     => "MovedPermanently"
    case "302"     => "Found"
    case "304"     => "NotModified"
    case "400"     => "BadRequest"
    case "401"     => "Unauthorized"
    case "403"     => "Forbidden"
    case "404"     => "NotFound"
    case "405"     => "MethodNotAllowed"
    case "409"     => "Conflict"
    case "410"     => "Gone"
    case "422"     => "UnprocessableEntity"
    case "429"     => "TooManyRequests"
    case "500"     => "InternalServerError"
    case "501"     => "NotImplemented"
    case "502"     => "BadGateway"
    case "503"     => "ServiceUnavailable"
    case "504"     => "GatewayTimeout"
    case "2xx"     => "Success2XX"
    case "4xx"     => "ClientError4XX"
    case "5xx"     => "ServerError5XX"
    case "default" => "Default"
    case s         => s"Status$s" // Fallback for unknown codes
  }

  /** Check if a status code is a range status (needs statusCode field) */
  def isRangeStatus(statusCode: String): Boolean = statusCode.toLowerCase match {
    case "4xx" | "5xx" | "default" | "2xx" => true
    case _                                 => false
  }

  /** Extract shape from response variants */
  def fromVariants(variants: List[ResponseVariant]): ResponseShape = {
    val statusCodes = variants.map(_.statusCode).sortWith(statusCodeOrder)
    val rangeStatuses = statusCodes.filter(isRangeStatus)
    ResponseShape(statusCodes, rangeStatuses)
  }

  /** Sort status codes: numeric first (ascending), then wildcards, then "default" last */
  private def statusCodeOrder(a: String, b: String): Boolean = {
    val aLower = a.toLowerCase
    val bLower = b.toLowerCase

    (aLower, bLower) match {
      case ("default", _) => false // default always last
      case (_, "default") => true
      case _              =>
        // Try to parse as numbers for sorting
        val aNum = scala.util.Try(a.toInt).toOption
        val bNum = scala.util.Try(b.toInt).toOption
        (aNum, bNum) match {
          case (Some(an), Some(bn)) => an < bn
          case (Some(_), None)      => true // numbers before wildcards
          case (None, Some(_))      => false
          case (None, None)         => a < b // alphabetical for wildcards
        }
    }
  }
}
