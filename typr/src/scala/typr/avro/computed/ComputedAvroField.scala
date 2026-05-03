package typr.avro.computed

import typr.avro.{AvroField, AvroType}
import typr.jvm

/** Computed representation of an Avro field for code generation.
  *
  * @param proto
  *   The original Avro field definition
  * @param name
  *   The computed JVM field name
  * @param jvmType
  *   The mapped JVM type
  * @param isNullable
  *   Whether the field is nullable (union with null)
  * @param hasDefault
  *   Whether the field has a default value
  */
case class ComputedAvroField(
    proto: AvroField,
    name: jvm.Ident,
    jvmType: jvm.Type,
    isNullable: Boolean,
    hasDefault: Boolean
) {

  /** The original field name from the Avro schema */
  def protoName: String = proto.name

  /** Documentation for the field */
  def doc: Option[String] = proto.doc

  /** The original Avro type */
  def avroType: AvroType = proto.fieldType

  /** Default value JSON if present */
  def defaultValue: Option[String] = proto.defaultValue
}
