package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Header schema definition */
case class HeaderSchema(
    /** Header fields */
    fields: List[HeaderField]
)

object HeaderSchema {
  implicit val decoder: Decoder[HeaderSchema] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.HeaderSchema]

  implicit val encoder: Encoder[HeaderSchema] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.HeaderSchema]
}
