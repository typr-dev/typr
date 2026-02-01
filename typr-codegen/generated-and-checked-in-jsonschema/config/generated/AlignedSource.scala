package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Configuration for how a domain type aligns with a specific source entity */
case class AlignedSource(
    /** The entity path in the source. For DB: 'schema.table' or just 'table'. For OpenAPI/JSON Schema: schema name. For Avro: fully qualified record name. */
    entity: Option[String],
    /** Source fields to ignore (not considered drift) */
    exclude: Option[List[String]],
    /** Extra source fields to include as parameters in toSource mapper (useful for DB audit columns) */
    include_extra: Option[List[String]],
    /** Explicit field name mappings when auto-alignment fails. Keys are domain type field names, values are source field names. */
    mappings: Option[Map[String, String]],
    /** Compatibility mode: 'exact' = source must match exactly, 'superset' = source can have extra fields, 'subset' = source can have fewer fields */
    mode: Option[String],
    /** Whether this alignment is read-only (no toSource mapper generated) */
    readonly: Option[Boolean]
)

object AlignedSource {
  implicit val decoder: Decoder[AlignedSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.AlignedSource]

  implicit val encoder: Encoder[AlignedSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.AlignedSource]
}
