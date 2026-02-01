package typr.cli.config

import io.circe.Json
import io.circe.yaml.v12.parser
import typr.config.generated.AvroBoundary
import typr.config.generated.DatabaseBoundary
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbBoundary
import typr.config.generated.DuckdbSource
import typr.config.generated.GrpcBoundary
import typr.config.generated.JsonschemaBoundary
import typr.config.generated.JsonschemaSource
import typr.config.generated.OpenapiBoundary
import typr.config.generated.OpenapiSource

object ConfigParser {
  def parse(yaml: String): Either[String, TyprConfig] =
    parser
      .parse(yaml)
      .flatMap(_.as[TyprConfig])
      .left
      .map(e => s"Failed to parse config: ${e.getMessage}")

  def parseSource(json: Json): Either[String, ParsedSource] = {
    val sourceType = json.hcursor.downField("type").as[String].toOption
    sourceType match {
      case Some("postgresql") | Some("mariadb") | Some("mysql") | Some("sqlserver") | Some("oracle") | Some("db2") =>
        json.as[DatabaseSource].map(ParsedSource.Database.apply).left.map(e => s"Failed to parse database source: ${e.getMessage}")
      case Some("duckdb") =>
        json.as[DuckdbSource].map(ParsedSource.DuckDb.apply).left.map(e => s"Failed to parse DuckDB source: ${e.getMessage}")
      case Some("openapi") =>
        json.as[OpenapiSource].map(ParsedSource.OpenApi.apply).left.map(e => s"Failed to parse OpenAPI source: ${e.getMessage}")
      case Some("jsonschema") =>
        json.as[JsonschemaSource].map(ParsedSource.JsonSchema.apply).left.map(e => s"Failed to parse JSON Schema source: ${e.getMessage}")
      case Some("avro") =>
        json.as[AvroBoundary].map(ParsedSource.Avro.apply).left.map(e => s"Failed to parse Avro source: ${e.getMessage}")
      case Some("grpc") =>
        json.as[GrpcBoundary].map(ParsedSource.Grpc.apply).left.map(e => s"Failed to parse gRPC source: ${e.getMessage}")
      case Some(other) => Left(s"Unknown source type: $other")
      case None        => Left("Source missing 'type' field")
    }
  }

  def parseBoundary(json: Json): Either[String, ParsedBoundary] = {
    val boundaryType = json.hcursor.downField("type").as[String].toOption
    boundaryType match {
      case Some("postgresql") | Some("mariadb") | Some("mysql") | Some("sqlserver") | Some("oracle") | Some("db2") =>
        json.as[DatabaseBoundary].map(ParsedBoundary.Database.apply).left.map(e => s"Failed to parse database boundary: ${e.getMessage}")
      case Some("duckdb") =>
        json.as[DuckdbBoundary].map(ParsedBoundary.DuckDb.apply).left.map(e => s"Failed to parse DuckDB boundary: ${e.getMessage}")
      case Some("openapi") =>
        json.as[OpenapiBoundary].map(ParsedBoundary.OpenApi.apply).left.map(e => s"Failed to parse OpenAPI boundary: ${e.getMessage}")
      case Some("jsonschema") =>
        json.as[JsonschemaBoundary].map(ParsedBoundary.JsonSchema.apply).left.map(e => s"Failed to parse JSON Schema boundary: ${e.getMessage}")
      case Some("avro") =>
        json.as[AvroBoundary].map(ParsedBoundary.Avro.apply).left.map(e => s"Failed to parse Avro boundary: ${e.getMessage}")
      case Some("grpc") =>
        json.as[GrpcBoundary].map(ParsedBoundary.Grpc.apply).left.map(e => s"Failed to parse gRPC boundary: ${e.getMessage}")
      case Some(other) => Left(s"Unknown boundary type: $other")
      case None        => Left("Boundary missing 'type' field")
    }
  }
}

sealed trait ParsedBoundary {
  def boundaryType: String
}

object ParsedBoundary {
  case class Database(config: DatabaseBoundary) extends ParsedBoundary {
    def boundaryType: String = config.`type`.getOrElse("unknown")
  }
  case class DuckDb(config: DuckdbBoundary) extends ParsedBoundary {
    def boundaryType: String = "duckdb"
  }
  case class OpenApi(config: OpenapiBoundary) extends ParsedBoundary {
    def boundaryType: String = "openapi"
  }
  case class JsonSchema(config: JsonschemaBoundary) extends ParsedBoundary {
    def boundaryType: String = "jsonschema"
  }
  case class Avro(config: AvroBoundary) extends ParsedBoundary {
    def boundaryType: String = "avro"
  }
  case class Grpc(config: GrpcBoundary) extends ParsedBoundary {
    def boundaryType: String = "grpc"
  }
}

sealed trait ParsedSource {
  def sourceType: String
}

object ParsedSource {
  case class Database(config: DatabaseSource) extends ParsedSource {
    def sourceType: String = config.`type`.getOrElse("unknown")
  }
  case class DuckDb(config: DuckdbSource) extends ParsedSource {
    def sourceType: String = "duckdb"
  }
  case class OpenApi(config: OpenapiSource) extends ParsedSource {
    def sourceType: String = "openapi"
  }
  case class JsonSchema(config: JsonschemaSource) extends ParsedSource {
    def sourceType: String = "jsonschema"
  }
  case class Avro(config: AvroBoundary) extends ParsedSource {
    def sourceType: String = "avro"
  }
  case class Grpc(config: GrpcBoundary) extends ParsedSource {
    def sourceType: String = "grpc"
  }
}
