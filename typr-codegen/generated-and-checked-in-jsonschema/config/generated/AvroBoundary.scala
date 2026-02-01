package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

/** Avro schema boundary for event types */
case class AvroBoundary(
    /** Path to single .avsc file */
    schema: Option[String],
    /** URL of Confluent Schema Registry */
    schema_registry: Option[String],
    /** Multiple .avsc file paths (glob patterns supported) */
    schemas: Option[List[String]],
    /** Schema Registry subjects to include (glob patterns supported) */
    subjects: Option[List[String]],
    `type`: Option[String],
    /** Boundary-level type definitions */
    types: Option[Map[String, FieldType]],
    /** Avro wire format: confluent_registry (default), binary (raw Avro), json (JSON encoding) */
    wire_format: Option[String],
    /** JSON library to use when wire_format is 'json' */
    json_lib: Option[String],
    /** Generate record classes (event types) */
    generate_records: Option[Boolean],
    /** Generate Serializer/Deserializer classes */
    generate_serdes: Option[Boolean],
    /** Generate type-safe producer classes */
    generate_producers: Option[Boolean],
    /** Generate type-safe consumer classes */
    generate_consumers: Option[Boolean],
    /** Generate Topics object with typed topic constants */
    generate_topic_bindings: Option[Boolean],
    /** Generate typed header classes */
    generate_headers: Option[Boolean],
    /** Generate schema compatibility validation utility */
    generate_schema_validator: Option[Boolean],
    /** Generate RPC service interfaces from .avpr protocol files */
    generate_protocols: Option[Boolean],
    /** Generate sealed types for complex union types */
    generate_union_types: Option[Boolean],
    /** Topic mapping: schema name -> topic name */
    topic_mapping: Option[Map[String, String]],
    /** Multi-event topic grouping: topic name -> list of schema names */
    topic_groups: Option[Map[String, List[String]]],
    /** Key types per topic */
    topic_keys: Option[Map[String, Json]],
    /** Default key type when not specified per-topic */
    default_key_type: Option[Json],
    /** Header schemas by name */
    header_schemas: Option[Map[String, HeaderSchema]],
    /** Topic to header schema mapping */
    topic_headers: Option[Map[String, String]],
    /** Default header schema name */
    default_header_schema: Option[String],
    /** Schema evolution strategy */
    schema_evolution: Option[String],
    /** Schema compatibility mode */
    compatibility_mode: Option[String],
    /** Add @Since annotations for new fields */
    mark_new_fields: Option[Boolean],
    /** Generate precise wrapper types for constrained Avro types */
    enable_precise_types: Option[Boolean],
    /** Generate framework-specific event publishers/listeners */
    generate_kafka_events: Option[Boolean],
    /** Generate framework-specific RPC client/server implementations */
    generate_kafka_rpc: Option[Boolean]
)

object AvroBoundary {
  implicit val decoder: Decoder[AvroBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.AvroBoundary]

  implicit val encoder: Encoder[AvroBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.AvroBoundary]
}
