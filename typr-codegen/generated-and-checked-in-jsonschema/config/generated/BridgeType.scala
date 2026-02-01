package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** A Bridge type that spans multiple sources. Can be field (pattern-based) or domain (projection-based). */
trait BridgeType {
  def kind: String
}

object BridgeType {
  implicit val decoder: Decoder[BridgeType] = {
    io.circe.Decoder.instance { cursor =>
      cursor.get[java.lang.String]("kind").flatMap {
        case "field"  => cursor.as[FieldType]
        case "domain" => cursor.as[DomainType]
        case other    => scala.Left(io.circe.DecodingFailure(s"Unknown discriminator value: $other", cursor.history))
      }
    }
  }

  implicit val encoder: Encoder[BridgeType] = {
    io.circe.Encoder.instance {
      case x: FieldType  => io.circe.Encoder[FieldType].apply(x).mapObject(_.add("kind", io.circe.Json.fromString("field")))
      case x: DomainType => io.circe.Encoder[DomainType].apply(x).mapObject(_.add("kind", io.circe.Json.fromString("domain")))
    }
  }
}
