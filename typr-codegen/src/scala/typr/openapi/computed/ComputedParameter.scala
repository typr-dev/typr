package typr.openapi.computed

import typr.jvm
import typr.openapi.{ApiParameter, ParameterIn}

/** Computed representation of an API parameter for code generation.
  *
  * @param proto
  *   The original parameter definition
  * @param name
  *   The computed JVM parameter name
  * @param jvmType
  *   The mapped JVM type
  */
case class ComputedParameter(
    proto: ApiParameter,
    name: jvm.Ident,
    jvmType: jvm.Type
) {

  /** Original parameter name from the OpenAPI spec */
  def protoName: String = proto.originalName

  /** Documentation for the parameter */
  def doc: Option[String] = proto.description

  /** Parameter location (path, query, header, cookie) */
  def in: ParameterIn = proto.in

  /** Whether the parameter is required */
  def required: Boolean = proto.required

  /** Whether the parameter is deprecated */
  def deprecated: Boolean = proto.deprecated

  /** Default value if any */
  def defaultValue: Option[String] = proto.defaultValue
}
