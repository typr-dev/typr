package typr.cli.app

import io.circe.Json
import typr.MetaDb
import typr.TypoLogger
import typr.avro.AvroSchemaFile
import typr.cli.config.{ConfigParser, ParsedSource}
import typr.grpc.ProtoFile
import typr.openapi.ParsedSpec

import java.nio.file.Path
import scala.concurrent.ExecutionContext

/** Loaded form of a configured source. One case per source kind; each carries the format's typed-result value (a MetaDb, a ParsedSpec, a list of AvroSchemaFiles, …). Lives in [[AppApi.sourceCache]]
  * keyed by source name; every screen that needs source data reads from there and pattern-matches.
  *
  * The point of having this as a sum type rather than four parallel maps is twofold:
  *   1. **Dispatch in one place.** [[load]] is the single function that knows which fetcher to call per source kind; the rest of the codebase reads `Option[LoadedSource]` and matches.
  *   2. **Codec by construction.** Each variant binds the format's natural typed result; illegal cross-kind states (e.g. "spec data in the avro slot") cannot be expressed.
  */
sealed trait LoadedSource

object LoadedSource {

  final case class Db(metaDb: MetaDb) extends LoadedSource
  final case class Spec(spec: ParsedSpec) extends LoadedSource
  final case class Avro(files: List[AvroSchemaFile]) extends LoadedSource
  final case class Proto(files: List[ProtoFile]) extends LoadedSource

  /** Load one source synchronously. Caller decides on threading; this is a pure function (plus the unavoidable I/O for the fetcher itself).
    *
    * Dispatch goes through [[ConfigParser.parseSource]] which parses the JSON exactly once into a typed [[ParsedSource]], then picks the matching fetcher off the sealed trait.
    */
  def load(name: String, json: Json, buildDir: Path): Either[String, LoadedSource] = {
    given ExecutionContext = ExecutionContext.global
    ConfigParser.parseSource(json).flatMap {
      case _: ParsedSource.Database | _: ParsedSource.DuckDb | _: ParsedSource.Sqlite =>
        // MetaDbFetch.fetch can throw (Hikari connect, Python install, await). Wrap once.
        try Right(Db(MetaDbFetch.fetch(name, json, TypoLogger.Noop)))
        catch case e: Throwable => Left(Option(e.getMessage).getOrElse(e.toString))
      case _: ParsedSource.OpenApi | _: ParsedSource.JsonSchema =>
        SpecFetch.fetch(json, buildDir).map(Spec.apply)
      case _: ParsedSource.Avro =>
        AvroFetch.fetch(json, buildDir).map(Avro.apply)
      case _: ParsedSource.Grpc =>
        ProtoFetch.fetch(json, buildDir).map(Proto.apply)
    }
  }
}
