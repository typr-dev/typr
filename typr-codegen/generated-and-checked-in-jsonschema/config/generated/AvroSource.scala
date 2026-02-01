package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Avro schema source for event types */
case class AvroSource(
    /** Path to single .avsc file */
    schema: Option[String],
    /** URL of Confluent Schema Registry */
    schema_registry: Option[String],
    /** Multiple .avsc file paths (glob patterns supported) */
    schemas: Option[List[String]],
    /** Schema Registry subjects to include (glob patterns supported) */
    subjects: Option[List[String]],
    `type`: Option[String],
    /** Source-level type definitions */
    types: Option[Map[String, FieldType]]
)

object AvroSource {
  implicit val decoder: Decoder[AvroSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.AvroSource]

  implicit val encoder: Encoder[AvroSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.AvroSource]
}
