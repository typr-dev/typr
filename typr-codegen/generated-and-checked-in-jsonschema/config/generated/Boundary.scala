package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class Boundary()

object Boundary {
  implicit val decoder: Decoder[Boundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.Boundary]

  implicit val encoder: Encoder[Boundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.Boundary]
}
