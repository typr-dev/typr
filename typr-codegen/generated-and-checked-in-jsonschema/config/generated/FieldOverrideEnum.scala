package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class FieldOverrideEnum(value: String) extends FieldOverride

object FieldOverrideEnum {
  implicit val decoder: Decoder[FieldOverrideEnum] = io.circe.Decoder[java.lang.String].map(FieldOverrideEnum.apply)

  implicit val encoder: Encoder[FieldOverrideEnum] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
