package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class FeatureMatcherArray(value: List[String]) extends FeatureMatcher

object FeatureMatcherArray {
  implicit val decoder: Decoder[FeatureMatcherArray] = io.circe.Decoder[scala.List[java.lang.String]].map(FeatureMatcherArray.apply)

  implicit val encoder: Encoder[FeatureMatcherArray] = io.circe.Encoder[scala.List[java.lang.String]].contramap(_.value)
}
