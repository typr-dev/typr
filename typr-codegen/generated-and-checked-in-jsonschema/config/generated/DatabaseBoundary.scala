package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class DatabaseBoundary(
    /** Connection timeout in seconds */
    connection_timeout: Option[Long],
    database: Option[String],
    encrypt: Option[Boolean],
    host: Option[String],
    password: Option[String],
    port: Option[Long],
    /** Schema mode: 'multi_schema' or 'single_schema:SCHEMA_NAME' */
    schema_mode: Option[String],
    /** Path to schema SQL file */
    schema_sql: Option[String],
    /** Schemas to include */
    schemas: Option[List[String]],
    selectors: Option[BoundarySelectors],
    /** Oracle service name */
    service: Option[String],
    /** Oracle SID */
    sid: Option[String],
    /** Path to SQL scripts directory */
    sql_scripts: Option[String],
    ssl: Option[Json],
    trust_server_certificate: Option[Boolean],
    `type`: Option[String],
    /** Type override mappings (table.column -> type) */
    type_override: Option[Map[String, String]],
    /** Boundary-level type definitions (scoped to this boundary only) */
    types: Option[Map[String, FieldType]],
    /** JDBC URL (alternative to host/port/database) */
    url: Option[String],
    username: Option[String]
)

object DatabaseBoundary {
  implicit val decoder: Decoder[DatabaseBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DatabaseBoundary]

  implicit val encoder: Encoder[DatabaseBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DatabaseBoundary]
}
