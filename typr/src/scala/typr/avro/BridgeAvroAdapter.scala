package typr.avro

import typr.avro.computed.ComputedAvroRecord
import typr.internal.codegen.FileBridgeProjectionMapper.{ExternalField, ExternalRecord}

/** Adapts Avro computed types to the generic ExternalRecord format used by Bridge mappers. */
object BridgeAvroAdapter {

  /** Convert a ComputedAvroRecord to ExternalRecord for Bridge mapper generation.
    *
    * @param record
    *   The computed Avro record
    * @return
    *   An ExternalRecord with the same fields
    */
  def toExternalRecord(record: ComputedAvroRecord): ExternalRecord =
    ExternalRecord(
      tpe = record.tpe,
      fields = record.fields.map { field =>
        ExternalField(
          name = field.name,
          protoName = field.protoName,
          jvmType = field.jvmType,
          isNullable = field.isNullable
        )
      }
    )

  /** Create a lookup function from computed Avro records for Bridge mapper generation.
    *
    * The lookup matches by full name (namespace.name) or simple name.
    *
    * @param records
    *   List of computed Avro records
    * @return
    *   A lookup function that finds records by entity path
    */
  def createLookup(records: List[ComputedAvroRecord]): String => Option[ExternalRecord] = {
    val byFullName = records.map(r => r.fullName -> toExternalRecord(r)).toMap
    val bySimpleName = records.map(r => r.name -> toExternalRecord(r)).toMap

    entityPath => byFullName.get(entityPath).orElse(bySimpleName.get(entityPath))
  }
}
