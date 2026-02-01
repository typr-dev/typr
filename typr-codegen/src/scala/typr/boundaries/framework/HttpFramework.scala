package typr.boundaries.framework

import typr.jvm

/** HTTP protocol framework extension for OpenAPI code generation.
  *
  * Provides HTTP-specific code generation patterns on top of the base Framework trait for REST APIs generated from OpenAPI specifications.
  */
trait HttpFramework extends Framework {

  /** Annotation for HTTP method mapping (e.g., @GetMapping, @GET) */
  def methodAnnotation(method: String, path: String): jvm.Annotation

  /** Annotation for path parameters (e.g., @PathVariable, @PathParam) */
  def pathParamAnnotation(name: String): jvm.Annotation

  /** Annotation for query parameters (e.g., @RequestParam, @QueryParam) */
  def queryParamAnnotation(name: String, required: Boolean): jvm.Annotation

  /** Annotation for header parameters (e.g., @RequestHeader, @HeaderParam) */
  def headerParamAnnotation(name: String): jvm.Annotation

  /** Annotation for request body (e.g., @RequestBody, no annotation for JAX-RS) */
  def requestBodyAnnotation: List[jvm.Annotation]

  /** The response wrapper type (e.g., ResponseEntity, Response) */
  def responseType: jvm.Type.Qualified

  /** Build a success response (HTTP 200) */
  def buildOkResponse(value: jvm.Code): jvm.Code

  /** Build a response with a specific status code */
  def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code
}
