package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** A field Bridge type - matches columns/properties by pattern across all sources. Example: Email, CustomerId, Money */
case class FieldType(
    api: Option[ApiMatch],
    db: Option[DbMatch],
    model: Option[ModelMatch],
    /** The underlying primitive type (String, Long, BigDecimal, etc.) */
    underlying: Option[String],
    validation: Option[ValidationRules]
) extends BridgeType {
  override lazy val kind: String = "field"
}

object FieldType {
  implicit val decoder: Decoder[FieldType] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.FieldType]

  implicit val encoder: Encoder[FieldType] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.FieldType]
}
