package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Patterns support glob syntax (* and ?) and negation (!pattern) */
trait StringOrArray

object StringOrArray {
  implicit val decoder: Decoder[StringOrArray] =
    io.circe.Decoder[StringOrArrayString].map(x => x: typr.config.generated.StringOrArray) or io.circe.Decoder[StringOrArrayArray].map(x => x: typr.config.generated.StringOrArray)

  implicit val encoder: Encoder[StringOrArray] = {
    io.circe.Encoder.instance {
      case x: StringOrArrayString => io.circe.Encoder[StringOrArrayString].apply(x)
      case x: StringOrArrayArray  => io.circe.Encoder[StringOrArrayArray].apply(x)
    }
  }
}
