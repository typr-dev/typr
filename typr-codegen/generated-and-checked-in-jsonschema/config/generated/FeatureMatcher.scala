package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Patterns support glob syntax (* and ?) and negation (!pattern) */
trait FeatureMatcher

object FeatureMatcher {
  implicit val decoder: Decoder[FeatureMatcher] =
    io.circe.Decoder[FeatureMatcherString].map(x => x: typr.config.generated.FeatureMatcher) or io.circe.Decoder[FeatureMatcherArray].map(x => x: typr.config.generated.FeatureMatcher) or io.circe
      .Decoder[FeatureMatcherObject]
      .map(x => x: typr.config.generated.FeatureMatcher)

  implicit val encoder: Encoder[FeatureMatcher] = {
    io.circe.Encoder.instance {
      case x: FeatureMatcherString => io.circe.Encoder[FeatureMatcherString].apply(x)
      case x: FeatureMatcherArray  => io.circe.Encoder[FeatureMatcherArray].apply(x)
      case x: FeatureMatcherObject => io.circe.Encoder[FeatureMatcherObject].apply(x)
    }
  }
}
