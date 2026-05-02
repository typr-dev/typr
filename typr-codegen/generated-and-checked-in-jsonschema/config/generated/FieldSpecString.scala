package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class FieldSpecString(value: String) extends FieldSpec

object FieldSpecString {
  implicit val decoder: Decoder[FieldSpecString] = io.circe.Decoder[java.lang.String].map(FieldSpecString.apply)

  implicit val encoder: Encoder[FieldSpecString] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
