package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Specification for a field in a domain type. Can be compact string or expanded object. */
trait FieldSpec

object FieldSpec {
  implicit val decoder: Decoder[FieldSpec] =
    io.circe.Decoder[FieldSpecString].map(x => x: typr.config.generated.FieldSpec) or io.circe.Decoder[FieldSpecObject].map(x => x: typr.config.generated.FieldSpec)

  implicit val encoder: Encoder[FieldSpec] = {
    io.circe.Encoder.instance {
      case x: FieldSpecString => io.circe.Encoder[FieldSpecString].apply(x)
      case x: FieldSpecObject => io.circe.Encoder[FieldSpecObject].apply(x)
    }
  }
}
