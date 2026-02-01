package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class MatcherValueObject(
    exclude: Option[List[String]],
    include: Option[Json]
) extends MatcherValue

object MatcherValueObject {
  implicit val decoder: Decoder[MatcherValueObject] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.MatcherValueObject]

  implicit val encoder: Encoder[MatcherValueObject] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.MatcherValueObject]
}
