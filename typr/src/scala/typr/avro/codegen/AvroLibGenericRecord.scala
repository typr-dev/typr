package typr.avro.codegen

import typr.avro.AvroType
import typr.jvm
import typr.Lang

/** AvroLib implementation for GenericRecord-based serialization.
  *
  * For wrapper types, the serialization is straightforward:
  *   - Wrappers are simple value holders with unwrap()/valueOf() methods
  *   - The actual conversion happens in the parent record's toGenericRecord/fromGenericRecord methods
  *   - No additional type class instances are needed on the wrapper itself
  *
  * Future extensions could add static toAvro/fromAvro methods on the wrapper for standalone usage.
  */
class AvroLibGenericRecord(lang: Lang) extends AvroLib {

  override def wrapperTypeInstances(
      wrapperType: jvm.Type.Qualified,
      underlyingJvmType: jvm.Type,
      underlyingAvroType: AvroType
  ): AvroLib.Instances = {
    AvroLib.Instances.Empty
  }
}
