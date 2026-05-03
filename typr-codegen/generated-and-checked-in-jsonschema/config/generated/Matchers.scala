package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class Matchers(
    field_values: Option[FeatureMatcher],
    mock_repos: Option[FeatureMatcher],
    primary_key_types: Option[FeatureMatcher],
    readonly: Option[FeatureMatcher],
    test_inserts: Option[FeatureMatcher]
)

object Matchers {
  implicit val decoder: Decoder[Matchers] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.Matchers]

  implicit val encoder: Encoder[Matchers] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.Matchers]
}
