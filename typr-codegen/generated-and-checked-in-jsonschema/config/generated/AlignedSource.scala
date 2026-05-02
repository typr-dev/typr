package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Configuration for how a domain type aligns with a specific boundary entity */
case class AlignedSource(
    /** Flow direction: 'in' = data flows from boundary to domain, 'out' = domain to boundary, 'in-out' = bidirectional */
    direction: Option[String],
    /** The entity path in the boundary. For DB: 'schema.table' or just 'table'. For OpenAPI/JSON Schema: schema name. For Avro: fully qualified record name. */
    entity: Option[String],
    /** Boundary fields to ignore (not considered drift) */
    exclude: Option[List[String]],
    /** Per-field overrides for mapping behavior. Keys are domain field names. */
    field_overrides: Option[Map[String, FieldOverride]],
    /** Extra boundary fields to include as parameters in toBoundary mapper (useful for DB audit columns) */
    include_extra: Option[List[String]],
    /** Explicit field name mappings when auto-alignment fails. Keys are domain type field names, values are boundary field names. */
    mappings: Option[Map[String, String]],
    /** Compatibility mode: 'exact' = boundary must match exactly, 'superset' = boundary can have extra fields, 'subset' = boundary can have fewer fields */
    mode: Option[String],
    /** Whether this alignment is read-only (no toBoundary mapper generated) */
    readonly: Option[Boolean],
    /** Default type compatibility policy for field mappings in this boundary */
    type_policy: Option[String]
)

object AlignedSource {
  implicit val decoder: Decoder[AlignedSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.AlignedSource]

  implicit val encoder: Encoder[AlignedSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.AlignedSource]
}
