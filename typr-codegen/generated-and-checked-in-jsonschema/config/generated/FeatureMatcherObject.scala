package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class FeatureMatcherObject(
    exclude: Option[List[String]],
    include: Option[Json]
) extends FeatureMatcher

object FeatureMatcherObject {
  implicit val decoder: Decoder[FeatureMatcherObject] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.FeatureMatcherObject]

  implicit val encoder: Encoder[FeatureMatcherObject] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.FeatureMatcherObject]
}
