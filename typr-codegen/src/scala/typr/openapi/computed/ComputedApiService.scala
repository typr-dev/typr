package typr.openapi.computed

import typr.jvm
import typr.openapi.ApiInterface

/** Computed representation of an API service (tag group) for code generation.
  *
  * An API service groups related endpoints by tag and generates interfaces for server and client implementations.
  *
  * @param proto
  *   The original API interface definition
  * @param baseTpe
  *   The qualified JVM type for the base interface
  * @param serverTpe
  *   The qualified JVM type for the server implementation
  * @param clientTpe
  *   The qualified JVM type for the client implementation
  * @param endpoints
  *   Computed endpoints with JVM types
  * @param basePath
  *   Common base path extracted from all endpoint paths
  */
case class ComputedApiService(
    proto: ApiInterface,
    baseTpe: jvm.Type.Qualified,
    serverTpe: jvm.Type.Qualified,
    clientTpe: jvm.Type.Qualified,
    endpoints: List[ComputedEndpoint],
    basePath: Option[String]
) {

  /** Service name */
  def name: String = proto.name

  /** Documentation */
  def doc: Option[String] = proto.description
}
