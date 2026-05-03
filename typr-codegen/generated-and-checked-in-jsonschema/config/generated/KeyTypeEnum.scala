package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class KeyTypeEnum(value: String) extends KeyType

object KeyTypeEnum {
  implicit val decoder: Decoder[KeyTypeEnum] = io.circe.Decoder[java.lang.String].map(KeyTypeEnum.apply)

  implicit val encoder: Encoder[KeyTypeEnum] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
