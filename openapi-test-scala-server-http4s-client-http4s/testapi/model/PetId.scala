package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import org.http4s.Uri.Path.SegmentEncoder

/** Unique pet identifier */
case class PetId(value: String) extends AnyVal

object PetId {
  implicit val decoder: Decoder[PetId] = io.circe.Decoder[java.lang.String].map(PetId.apply)

  implicit val encoder: Encoder[PetId] = io.circe.Encoder[java.lang.String].contramap(_.value)

  given segmentEncoder: SegmentEncoder[PetId] = org.http4s.Uri.Path.SegmentEncoder[java.lang.String].contramap(_.value)

  /** Path extractor for Http4s routes */
  def unapply(str: String): Option[PetId] = scala.Some(new testapi.model.PetId(str))
}