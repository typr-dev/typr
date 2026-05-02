package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Override for how a specific field is mapped between domain type and boundary */
trait FieldOverride

object FieldOverride {
  implicit val decoder: Decoder[FieldOverride] =
    io.circe.Decoder[FieldOverrideEnum].map(x => x: typr.config.generated.FieldOverride) or io.circe.Decoder[FieldOverrideObject].map(x => x: typr.config.generated.FieldOverride)

  implicit val encoder: Encoder[FieldOverride] = {
    io.circe.Encoder.instance {
      case x: FieldOverrideEnum   => io.circe.Encoder[FieldOverrideEnum].apply(x)
      case x: FieldOverrideObject => io.circe.Encoder[FieldOverrideObject].apply(x)
    }
  }
}
