package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class StringOrArrayArray(value: List[String]) extends StringOrArray

object StringOrArrayArray {
  implicit val decoder: Decoder[StringOrArrayArray] = io.circe.Decoder[scala.List[java.lang.String]].map(StringOrArrayArray.apply)

  implicit val encoder: Encoder[StringOrArrayArray] = io.circe.Encoder[scala.List[java.lang.String]].contramap(_.value)
}
