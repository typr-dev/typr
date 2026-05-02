package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class KeyTypeObject(
    /** Schema name for composite key */
    schema: String
) extends KeyType

object KeyTypeObject {
  implicit val decoder: Decoder[KeyTypeObject] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.KeyTypeObject]

  implicit val encoder: Encoder[KeyTypeObject] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.KeyTypeObject]
}
