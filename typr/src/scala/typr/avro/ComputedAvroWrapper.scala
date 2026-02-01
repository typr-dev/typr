package typr.avro

import typr.jvm

/** Computed wrapper type extracted from x-typr-wrapper annotations on Avro fields.
  *
  * This follows the Computed* pattern used by database codegen (ComputedDomain, etc.)
  */
case class ComputedAvroWrapper(
    /** Qualified wrapper type name (e.g., com.example.CustomerId) */
    tpe: jvm.Type.Qualified,
    /** Underlying JVM type (e.g., Long, String, java.util.UUID) */
    underlyingJvmType: jvm.Type,
    /** Underlying Avro type (for serialization logic) */
    underlyingAvroType: AvroType,
    /** Documentation from field */
    doc: Option[String]
)

object ComputedAvroWrapper {

  /** Collect all unique wrapper types from records.
    *
    * Scans all fields with x-typr-wrapper attributes and builds ComputedAvroWrapper instances.
    *
    * @param records
    *   All Avro records to scan
    * @param typeMapper
    *   Maps Avro types to JVM types
    * @param naming
    *   Naming conventions for generating qualified type names
    * @return
    *   List of unique wrapper types to generate
    */
  def collect(
      records: List[AvroRecord],
      typeMapper: typr.avro.codegen.AvroTypeMapper,
      naming: typr.Naming
  ): List[ComputedAvroWrapper] = {
    records
      .flatMap { record =>
        record.fields.flatMap { field =>
          field.wrapperType.map { wrapperName =>
            val underlyingAvroType = unwrapOptional(field.fieldType)
            val underlyingJvmType = typeMapper.mapType(underlyingAvroType)
            val tpe = naming.avroWrapperTypeName(wrapperName, record.namespace)
            ComputedAvroWrapper(
              tpe = tpe,
              underlyingJvmType = underlyingJvmType,
              underlyingAvroType = underlyingAvroType,
              doc = field.doc
            )
          }
        }
      }
      .distinctBy(_.tpe.value.idents.map(_.value).mkString("."))
  }

  /** Unwrap optional union types to get the base type.
    *
    * For a type like ["null", "string"], returns "string".
    */
  private def unwrapOptional(tpe: AvroType): AvroType = tpe match {
    case AvroType.Union(members) =>
      members.filterNot(_ == AvroType.Null) match {
        case List(single) => single
        case _            => tpe
      }
    case other => other
  }
}
