package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class FieldOverrideObject(
    /** The mapping action for this field */
    action: Option[String],
    /** Domain fields used to compute this field */
    computed_from: Option[List[String]],
    /** Direction override for this field */
    direction: Option[String],
    /** Description of enrichment logic for this field */
    enrichment: Option[String],
    /** Boundary fields to merge into this domain field */
    merge_from: Option[List[String]],
    /** Reason for dropping this field (when action is 'drop') */
    reason: Option[String],
    /** Boundary field name (if different from domain field name) */
    source_field: Option[String],
    /** Boundary field to split into this domain field */
    split_from: Option[String],
    /** Type policy override for this specific field */
    type_policy: Option[String]
) extends FieldOverride

object FieldOverrideObject {
  implicit val decoder: Decoder[FieldOverrideObject] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.FieldOverrideObject]

  implicit val encoder: Encoder[FieldOverrideObject] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.FieldOverrideObject]
}
