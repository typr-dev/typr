package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import org.http4s.Uri.Path.SegmentEncoder

/** Email address wrapper */
case class Email(value: String) extends AnyVal

object Email {
  implicit val decoder: Decoder[Email] = io.circe.Decoder[java.lang.String].map(Email.apply)

  implicit val encoder: Encoder[Email] = io.circe.Encoder[java.lang.String].contramap(_.value)

  given segmentEncoder: SegmentEncoder[Email] = org.http4s.Uri.Path.SegmentEncoder[java.lang.String].contramap(_.value)

  /** Path extractor for Http4s routes */
  def unapply(str: String): Option[Email] = scala.Some(new testapi.model.Email(str))
}