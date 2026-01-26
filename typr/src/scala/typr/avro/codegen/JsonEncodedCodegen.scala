package typr.avro.codegen

import typr.avro._
import typr.jvm
import typr.Lang
import typr.openapi.codegen.JsonLibSupport

/** JSON wire format code generation.
  *
  * For JSON wire format, records are serialized as JSON using Jackson/Circe annotations. This differs from Avro binary formats:
  *   - No SCHEMA field is generated (JSON is self-describing via annotations)
  *   - No toGenericRecord/fromGenericRecord methods (framework handles serialization)
  *   - Records get JSON annotations (@JsonProperty, etc.) via JsonLibSupport
  *
  * Users are expected to use framework-provided JSON serializers (Spring's JsonSerializer, Quarkus's auto-generated serdes) rather than custom Kafka Serializer implementations.
  */
class JsonEncodedCodegen(
    val lang: Lang,
    val jsonLib: JsonLibSupport
) extends AvroWireFormatSupport {

  override def recordImports: List[jvm.Type.Qualified] = Nil

  override def schemaField(record: AvroRecord, jsonSchema: String): jvm.ClassMember = {
    // JSON wire format doesn't need Avro schema - return a comment placeholder
    // This will be filtered out by RecordCodegen for JSON wire format
    jvm.Value(
      annotations = Nil,
      name = jvm.Ident("_JSON_WIRE_FORMAT_MARKER"),
      tpe = jvm.Type.Void,
      body = None,
      isLazy = false,
      isOverride = false
    )
  }

  override def toGenericRecordMethod(record: AvroRecord): jvm.Method = {
    // JSON wire format doesn't need toGenericRecord - return a marker method
    // This will be filtered out by RecordCodegen for JSON wire format
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("_JSON_WIRE_FORMAT_MARKER"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  override def fromGenericRecordMethod(record: AvroRecord): jvm.ClassMember = {
    // JSON wire format doesn't need fromGenericRecord - return a marker method
    // This will be filtered out by RecordCodegen for JSON wire format
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("_JSON_WIRE_FORMAT_MARKER"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  override def dependencies: List[KafkaDependency] = List(
    KafkaDependency("org.apache.kafka", "kafka-clients", "3.9.0"),
    KafkaDependency("com.fasterxml.jackson.core", "jackson-databind", "2.17.0")
  )

  /** Check if this is a JSON wire format (used by RecordCodegen to add annotations) */
  override def isJsonWireFormat: Boolean = true
}
