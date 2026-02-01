package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class MatcherValueString(value: String) extends MatcherValue

object MatcherValueString {
  implicit val decoder: Decoder[MatcherValueString] = io.circe.Decoder[java.lang.String].map(MatcherValueString.apply)

  implicit val encoder: Encoder[MatcherValueString] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
