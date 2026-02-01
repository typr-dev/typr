package typr.openapi.computed

import typr.jvm
import typr.openapi.{ApiMethod, HttpMethod, RequestBody, ResponseVariant}

/** Computed representation of an API endpoint (operation) for code generation.
  *
  * @param proto
  *   The original API method definition
  * @param name
  *   The computed method name
  * @param parameters
  *   Computed parameters with JVM types
  * @param requestBodyType
  *   The JVM type for the request body if present
  * @param returnType
  *   The computed return type
  * @param responseSumType
  *   If multiple response variants exist, the sum type for responses
  */
case class ComputedEndpoint(
    proto: ApiMethod,
    name: jvm.Ident,
    parameters: List[ComputedParameter],
    requestBodyType: Option[jvm.Type],
    returnType: jvm.Type,
    responseSumType: Option[jvm.Type.Qualified]
) {

  /** Original operation name from the OpenAPI spec */
  def protoName: String = proto.name

  /** Documentation for the endpoint */
  def doc: Option[String] = proto.description

  /** HTTP method (GET, POST, etc.) */
  def httpMethod: HttpMethod = proto.httpMethod

  /** URL path */
  def path: String = proto.path

  /** Request body definition if present */
  def requestBody: Option[RequestBody] = proto.requestBody

  /** Whether this endpoint is deprecated */
  def deprecated: Boolean = proto.deprecated

  /** Response variants for multi-response endpoints */
  def responseVariants: Option[List[ResponseVariant]] = proto.responseVariants

  /** Tags for grouping */
  def tags: List[String] = proto.tags
}
