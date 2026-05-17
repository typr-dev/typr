package typr.cli.app

import io.circe.Json
import typr.cli.config.{ConfigParser, ParsedSource}
import typr.config.generated.{JsonschemaBoundary, OpenapiBoundary}
import typr.openapi.ParsedSpec
import typr.openapi.parser.OpenApiParser

import java.nio.file.Path

/** Parse an OpenAPI or JSON-schema spec for one source. The source JSON is decoded via [[ConfigParser.parseSource]] into a typed [[OpenapiBoundary]] / [[JsonschemaBoundary]]; we then read `spec` (or
  * first of `specs`) and resolve it relative to the buildDir.
  *
  * All failures surface as `Left(message)` so the caller can render them in the UI rather than unwinding through a thread's uncaught exception handler.
  */
object SpecFetch {

  def fetch(json: Json, buildDir: Path): Either[String, ParsedSpec] =
    for {
      parsed <- ConfigParser.parseSource(json)
      specPath <- specPathFor(parsed)
      resolved = buildDir.resolve(specPath)
      _ <- Either.cond(java.nio.file.Files.exists(resolved), (), s"spec file not found: $resolved")
      spec <- OpenApiParser.parseFile(resolved).left.map(errs => s"failed to parse $specPath: ${errs.take(3).mkString("; ")}")
    } yield spec

  private def specPathFor(parsed: ParsedSource): Either[String, String] = parsed match {
    case ParsedSource.OpenApi(b)    => specPathFromOpenapi(b)
    case ParsedSource.JsonSchema(b) => specPathFromJsonschema(b)
    case other                      => Left(s"not an openapi / jsonschema source: ${other.sourceType}")
  }

  private def specPathFromOpenapi(b: OpenapiBoundary): Either[String, String] =
    b.spec
      .orElse(b.specs.flatMap(_.headOption))
      .toRight("openapi source has no `spec` or `specs` field")

  private def specPathFromJsonschema(b: JsonschemaBoundary): Either[String, String] =
    b.spec
      .orElse(b.specs.flatMap(_.headOption))
      .toRight("jsonschema source has no `spec` or `specs` field")

}
