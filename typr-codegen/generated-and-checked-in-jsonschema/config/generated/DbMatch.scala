package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Pattern-based matching for database columns (used by field types) */
case class DbMatch(
    annotation: Option[StringOrArray],
    column: Option[StringOrArray],
    comment: Option[StringOrArray],
    db_type: Option[StringOrArray],
    domain: Option[StringOrArray],
    /** Match nullability */
    nullable: Option[Boolean],
    /** Match only primary key columns */
    primary_key: Option[Boolean],
    references: Option[StringOrArray],
    schema: Option[StringOrArray],
    source: Option[StringOrArray],
    table: Option[StringOrArray]
)

object DbMatch {
  implicit val decoder: Decoder[DbMatch] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DbMatch]

  implicit val encoder: Encoder[DbMatch] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DbMatch]
}
