package testapi.model

import io.circe.Decoder
import io.circe.Encoder

trait Animal {
  def animal_type: String
}

object Animal {
  implicit val decoder: Decoder[Animal] = {
    io.circe.Decoder.instance { cursor =>
      cursor.get[java.lang.String]("animal_type").flatMap {
        case "cat"  => cursor.as[Cat]
        case "dog"  => cursor.as[Dog]
        case "bird" => cursor.as[Bird]
        case other  => scala.Left(io.circe.DecodingFailure(s"Unknown discriminator value: $other", cursor.history))
      }
    }
  }

  implicit val encoder: Encoder[Animal] = {
    io.circe.Encoder.instance {
      case x: Cat  => io.circe.Encoder[Cat].apply(x)
      case x: Dog  => io.circe.Encoder[Dog].apply(x)
      case x: Bird => io.circe.Encoder[Bird].apply(x)
    }
  }
}
