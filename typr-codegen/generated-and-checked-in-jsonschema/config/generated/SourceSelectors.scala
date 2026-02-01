package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Selectors for filtering tables and schemas at the source level */
case class SourceSelectors(
    /** Table names to exclude */
    exclude_tables: Option[List[String]],
    open_enums: Option[FeatureMatcher],
    precision_types: Option[FeatureMatcher],
    tables: Option[MatcherValue]
)

object SourceSelectors {
  implicit val decoder: Decoder[SourceSelectors] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.SourceSelectors]

  implicit val encoder: Encoder[SourceSelectors] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.SourceSelectors]
}
