package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Configuration for automatic name alignment between sources */
case class NameAlignmentConfig(
    /** Custom abbreviation expansions. Keys are abbreviations, values are full forms. */
    abbreviations: Option[Map[String, String]],
    /** How to handle case when matching names */
    case_sensitivity: Option[String],
    /** Names that should be treated as the same concept. Keys are canonical forms, values are arrays of alternative names. */
    equivalents: Option[Map[String, List[String]]],
    /** Enable Porter stemmer for word normalization (e.g., 'customers' -> 'customer') */
    stemming: Option[Boolean]
)

object NameAlignmentConfig {
  implicit val decoder: Decoder[NameAlignmentConfig] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.NameAlignmentConfig]

  implicit val encoder: Encoder[NameAlignmentConfig] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.NameAlignmentConfig]
}
