package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Selectors for filtering tables and schemas at the boundary level */
case class BoundarySelectors(
    /** Table names to exclude */
    exclude_tables: Option[List[String]],
    open_enums: Option[FeatureMatcher],
    precision_types: Option[FeatureMatcher],
    tables: Option[MatcherValue]
)

object BoundarySelectors {
  implicit val decoder: Decoder[BoundarySelectors] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.BoundarySelectors]

  implicit val encoder: Encoder[BoundarySelectors] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.BoundarySelectors]
}
