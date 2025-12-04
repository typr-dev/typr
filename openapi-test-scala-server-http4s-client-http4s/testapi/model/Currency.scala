package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import org.http4s.Uri.Path.SegmentEncoder

/** ISO 4217 currency code */
case class Currency(value: String) extends AnyVal

object Currency {
  implicit val decoder: Decoder[Currency] = io.circe.Decoder[java.lang.String].map(Currency.apply)

  implicit val encoder: Encoder[Currency] = io.circe.Encoder[java.lang.String].contramap(_.value)

  given segmentEncoder: SegmentEncoder[Currency] = org.http4s.Uri.Path.SegmentEncoder[java.lang.String].contramap(_.value)

  /** Path extractor for Http4s routes */
  def unapply(str: String): Option[Currency] = scala.Some(new testapi.model.Currency(str))
}