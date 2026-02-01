package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class Source()

object Source {
  implicit val decoder: Decoder[Source] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.Source]

  implicit val encoder: Encoder[Source] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.Source]
}
