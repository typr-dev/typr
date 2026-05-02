package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Key type for a topic. Can be a simple string enum or a schema reference object. */
trait KeyType

object KeyType {
  implicit val decoder: Decoder[KeyType] = io.circe.Decoder[KeyTypeEnum].map(x => x: typr.config.generated.KeyType) or io.circe.Decoder[KeyTypeObject].map(x => x: typr.config.generated.KeyType)

  implicit val encoder: Encoder[KeyType] = {
    io.circe.Encoder.instance {
      case x: KeyTypeEnum   => io.circe.Encoder[KeyTypeEnum].apply(x)
      case x: KeyTypeObject => io.circe.Encoder[KeyTypeObject].apply(x)
    }
  }
}
