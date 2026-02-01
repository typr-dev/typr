package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class MatcherValueArray(value: List[String]) extends MatcherValue

object MatcherValueArray {
  implicit val decoder: Decoder[MatcherValueArray] = io.circe.Decoder[scala.List[java.lang.String]].map(MatcherValueArray.apply)

  implicit val encoder: Encoder[MatcherValueArray] = io.circe.Encoder[scala.List[java.lang.String]].contramap(_.value)
}
