package typr.openapi.computed

import typr.jvm
import typr.openapi.Property

/** Computed representation of a model property for code generation.
  *
  * @param proto
  *   The original property definition
  * @param name
  *   The computed JVM field name
  * @param jvmType
  *   The mapped JVM type
  */
case class ComputedProperty(
    proto: Property,
    name: jvm.Ident,
    jvmType: jvm.Type
) {

  /** Original property name from the OpenAPI schema */
  def protoName: String = proto.originalName

  /** Documentation for the property */
  def doc: Option[String] = proto.description

  /** Whether the property is required */
  def required: Boolean = proto.required

  /** Whether the property is nullable */
  def nullable: Boolean = proto.nullable

  /** Whether the property is deprecated */
  def deprecated: Boolean = proto.deprecated
}
