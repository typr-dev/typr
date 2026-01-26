package typr.avro.codegen

import typr.avro.AvroType
import typr.jvm

/** Avro serialization code generation - analogous to DbLib for databases and JsonLib for JSON.
  *
  * Generates Avro-specific serialization support for wrapper types that can convert to/from GenericRecord format.
  */
trait AvroLib {

  /** Generate Avro serialization instances for a wrapper type.
    *
    * @param wrapperType
    *   The qualified type name of the wrapper (e.g., com.example.CustomerId)
    * @param underlyingJvmType
    *   The underlying JVM type (e.g., Long, String)
    * @param underlyingAvroType
    *   The underlying Avro type (for serialization logic)
    * @return
    *   Instances containing any static methods, fields, and givens for Avro serialization
    */
  def wrapperTypeInstances(
      wrapperType: jvm.Type.Qualified,
      underlyingJvmType: jvm.Type,
      underlyingAvroType: AvroType
  ): AvroLib.Instances
}

object AvroLib {

  /** Instances generated for Avro serialization support.
    *
    * Currently minimal - wrapper types are simple value wrappers where serialization happens in the parent record's toGenericRecord/fromGenericRecord methods via unwrap()/valueOf() calls.
    *
    * Future extensions could add:
    *   - toAvro/fromAvro static methods on the wrapper itself
    *   - Type class instances for generic serialization
    */
  case class Instances(
      /** Static methods (e.g., toAvro/fromAvro helpers) */
      methods: List[jvm.Method],
      /** Static fields/values */
      fields: List[jvm.Value],
      /** Givens/implicits for Avro serialization */
      givens: List[jvm.Given]
  )

  object Instances {
    val Empty: Instances = Instances(Nil, Nil, Nil)
  }
}
