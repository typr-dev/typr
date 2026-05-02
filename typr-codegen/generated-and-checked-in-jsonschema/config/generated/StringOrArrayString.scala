package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class StringOrArrayString(value: String) extends StringOrArray

object StringOrArrayString {
  implicit val decoder: Decoder[StringOrArrayString] = io.circe.Decoder[java.lang.String].map(StringOrArrayString.apply)

  implicit val encoder: Encoder[StringOrArrayString] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
