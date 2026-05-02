package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class FeatureMatcherString(value: String) extends FeatureMatcher

object FeatureMatcherString {
  implicit val decoder: Decoder[FeatureMatcherString] = io.circe.Decoder[java.lang.String].map(FeatureMatcherString.apply)

  implicit val encoder: Encoder[FeatureMatcherString] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
