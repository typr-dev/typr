package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class DuckdbSource(
    /** Path to DuckDB file or :memory: */
    path: String,
    /** Path to schema SQL file to execute on startup */
    schema_sql: Option[String],
    selectors: Option[SourceSelectors],
    /** Path to SQL scripts directory */
    sql_scripts: Option[String],
    `type`: Option[String],
    /** Source-level type definitions */
    types: Option[Map[String, FieldType]]
)

object DuckdbSource {
  implicit val decoder: Decoder[DuckdbSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DuckdbSource]

  implicit val encoder: Encoder[DuckdbSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DuckdbSource]
}
