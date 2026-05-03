package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Header field definition */
case class HeaderField(
    /** Header field name */
    name: String,
    /** Whether this header field is required */
    required: Option[Boolean],
    /** Header field type */
    `type`: String
)

object HeaderField {
  implicit val decoder: Decoder[HeaderField] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.HeaderField]

  implicit val encoder: Encoder[HeaderField] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.HeaderField]
}
