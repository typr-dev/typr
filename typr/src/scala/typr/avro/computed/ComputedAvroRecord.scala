package typr.avro.computed

import typr.avro.{AvroRecord, AvroType}
import typr.avro.codegen.AvroTypeMapper
import typr.{jvm, Lang, Naming}

/** Computed representation of an Avro record for code generation.
  *
  * Separates computation (type mapping, naming) from file generation.
  *
  * @param proto
  *   The original Avro record definition
  * @param tpe
  *   The qualified JVM type for this record
  * @param fields
  *   Computed fields with JVM types
  * @param parentEventGroupType
  *   If this record belongs to an event group, the sealed interface type
  */
case class ComputedAvroRecord(
    proto: AvroRecord,
    tpe: jvm.Type.Qualified,
    fields: List[ComputedAvroField],
    parentEventGroupType: Option[jvm.Type.Qualified]
) {

  /** Record name */
  def name: String = proto.name

  /** Full name including namespace */
  def fullName: String = proto.fullName

  /** Record namespace */
  def namespace: Option[String] = proto.namespace

  /** Documentation */
  def doc: Option[String] = proto.doc

  /** Whether this record is part of an event group */
  def isEventGroupMember: Boolean = parentEventGroupType.isDefined
}

object ComputedAvroRecord {

  /** Create a computed record from an Avro record definition */
  def from(
      proto: AvroRecord,
      naming: Naming,
      lang: Lang,
      typeMapper: AvroTypeMapper,
      parentEventGroupType: Option[jvm.Type.Qualified]
  ): ComputedAvroRecord = {
    val tpe = naming.avroRecordTypeName(proto.name, proto.namespace)

    val fields = proto.fields.map { field =>
      val jvmType = typeMapper.mapType(field.fieldType)
      val isNullable = field.fieldType match {
        case AvroType.Union(members) => members.contains(AvroType.Null)
        case _                       => false
      }

      ComputedAvroField(
        proto = field,
        name = naming.avroFieldName(field.name),
        jvmType = jvmType,
        isNullable = isNullable,
        hasDefault = field.defaultValue.isDefined
      )
    }

    ComputedAvroRecord(
      proto = proto,
      tpe = tpe,
      fields = fields,
      parentEventGroupType = parentEventGroupType
    )
  }
}
