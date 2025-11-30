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
    responseVariants: Option[List[ResponseVariant]]
)

/** Response variant for multi-status response sum types */
case class ResponseVariant(
    statusCode: String,
    typeInfo: TypeInfo,
    description: Option[String]
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
