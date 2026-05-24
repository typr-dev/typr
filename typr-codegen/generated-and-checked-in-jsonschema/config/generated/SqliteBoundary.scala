package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class SqliteBoundary(
    /** Path to SQLite file or :memory: for in-memory */
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

object SqliteBoundary {
  implicit val decoder: Decoder[SqliteBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.SqliteBoundary]

  implicit val encoder: Encoder[SqliteBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.SqliteBoundary]
}
