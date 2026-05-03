package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Patterns support glob syntax (* and ?) and negation (!pattern) */
trait MatcherValue

object MatcherValue {
  implicit val decoder: Decoder[MatcherValue] =
    io.circe.Decoder[MatcherValueString].map(x => x: typr.config.generated.MatcherValue) or io.circe.Decoder[MatcherValueArray].map(x => x: typr.config.generated.MatcherValue) or io.circe
      .Decoder[MatcherValueObject]
      .map(x => x: typr.config.generated.MatcherValue)

  implicit val encoder: Encoder[MatcherValue] = {
    io.circe.Encoder.instance {
      case x: MatcherValueString => io.circe.Encoder[MatcherValueString].apply(x)
      case x: MatcherValueArray  => io.circe.Encoder[MatcherValueArray].apply(x)
      case x: MatcherValueObject => io.circe.Encoder[MatcherValueObject].apply(x)
    }
  }
}
