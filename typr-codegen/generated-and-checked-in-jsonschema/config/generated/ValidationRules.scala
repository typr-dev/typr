package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

/** Validation rules for field types */
case class ValidationRules(
    /** Enumeration of allowed values */
    allowed_values: Option[List[Json]],
    /** Maximum numeric value (exclusive) */
    exclusive_max: Option[Double],
    /** Minimum numeric value (exclusive) */
    exclusive_min: Option[Double],
    /** Maximum numeric value (inclusive) */
    max: Option[Double],
    /** Maximum string length */
    max_length: Option[Long],
    /** Minimum numeric value (inclusive) */
    min: Option[Double],
    /** Minimum string length */
    min_length: Option[Long],
    /** Regex pattern the value must match */
    pattern: Option[String]
)

object ValidationRules {
  implicit val decoder: Decoder[ValidationRules] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.ValidationRules]

  implicit val encoder: Encoder[ValidationRules] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.ValidationRules]
}
