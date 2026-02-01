package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class FieldSpecObject(
    /** Whether this is an array/list of the type */
    array: Option[Boolean],
    /** Default value for this field when not provided */
    default: Option[Json],
    /** Human-readable description of this field */
    description: Option[String],
    /** Whether this field can be null */
    nullable: Option[Boolean],
    /** The type name (primitive, field Bridge type, or domain Bridge type) */
    `type`: String
) extends FieldSpec

object FieldSpecObject {
  implicit val decoder: Decoder[FieldSpecObject] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.FieldSpecObject]

  implicit val encoder: Encoder[FieldSpecObject] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.FieldSpecObject]
}
