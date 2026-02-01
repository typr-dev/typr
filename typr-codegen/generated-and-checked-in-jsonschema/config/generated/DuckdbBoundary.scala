package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class DuckdbBoundary(
    /** Path to DuckDB file or :memory: */
    path: String,
    /** Path to schema SQL file to execute on startup */
    schema_sql: Option[String],
    selectors: Option[BoundarySelectors],
    /** Path to SQL scripts directory */
    sql_scripts: Option[String],
    `type`: Option[String],
    /** Boundary-level type definitions */
    types: Option[Map[String, FieldType]]
)

object DuckdbBoundary {
  implicit val decoder: Decoder[DuckdbBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DuckdbBoundary]

  implicit val encoder: Encoder[DuckdbBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DuckdbBoundary]
}
