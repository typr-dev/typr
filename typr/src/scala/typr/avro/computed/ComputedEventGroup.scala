package typr.avro.computed

import typr.avro.{AvroEventGroup, AvroRecord}
import typr.{jvm, Naming}

/** Computed representation of an Avro event group for code generation.
  *
  * An event group is a collection of related events published to the same Kafka topic, represented as a sealed interface/trait with each event type as a subtype.
  *
  * @param proto
  *   The original event group definition
  * @param tpe
  *   The qualified JVM type for the sealed interface
  * @param memberTypes
  *   The qualified JVM types for each member record
  */
case class ComputedEventGroup(
    proto: AvroEventGroup,
    tpe: jvm.Type.Qualified,
    memberTypes: List[jvm.Type.Qualified]
) {

  /** Group name */
  def name: String = proto.name

  /** Group namespace */
  def namespace: Option[String] = proto.namespace

  /** Documentation */
  def doc: Option[String] = proto.doc

  /** Member records */
  def members: List[AvroRecord] = proto.members
}

object ComputedEventGroup {

  /** Create a computed event group from an Avro event group definition */
  def from(
      proto: AvroEventGroup,
      naming: Naming
  ): ComputedEventGroup = {
    val tpe = naming.avroEventGroupTypeName(proto.name, proto.namespace)

    val memberTypes = proto.members.map { record =>
      naming.avroRecordTypeName(record.name, record.namespace)
    }

    ComputedEventGroup(
      proto = proto,
      tpe = tpe,
      memberTypes = memberTypes
    )
  }
}
