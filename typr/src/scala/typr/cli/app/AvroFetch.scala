package typr.cli.app

import io.circe.Json
import typr.avro.AvroSchemaFile
import typr.avro.parser.AvroParser
import typr.cli.config.{ConfigParser, ParsedSource}
import typr.config.generated.AvroBoundary

import java.nio.file.Path

/** Parse Avro schemas for one source. Decodes the source JSON via [[ConfigParser.parseSource]] into a typed [[AvroBoundary]]; collects directory paths from `schemas` (multi) and `schema` (single);
  * runs [[AvroParser.parseDirectory]] on each. Concatenates the file lists.
  *
  * Failures surface as `Left(message)`.
  */
object AvroFetch {

  def fetch(json: Json, buildDir: Path): Either[String, List[AvroSchemaFile]] =
    for {
      parsed <- ConfigParser.parseSource(json)
      boundary <- extractAvro(parsed)
      paths <- nonEmptyPaths(boundary)
      files <- collectAll(paths, buildDir)
    } yield files

  private def extractAvro(parsed: ParsedSource): Either[String, AvroBoundary] = parsed match {
    case ParsedSource.Avro(b) => Right(b)
    case other                => Left(s"not an avro source: ${other.sourceType}")
  }

  private def nonEmptyPaths(b: AvroBoundary): Either[String, List[String]] = {
    val paths = b.schemas.toList.flatten ::: b.schema.toList
    if (paths.isEmpty) Left("avro source has no `schemas` or `schema` field")
    else Right(paths)
  }

  /** Parse each path; bail on the first failure with a contextual error message. */
  private def collectAll(paths: List[String], buildDir: Path): Either[String, List[AvroSchemaFile]] = {
    val zero: Either[String, List[AvroSchemaFile]] = Right(Nil)
    val combined = paths.foldLeft(zero) { (acc, rel) =>
      acc.flatMap { soFar =>
        val resolved = buildDir.resolve(rel)
        if (!java.nio.file.Files.exists(resolved))
          Left(s"avro schema path not found: $resolved")
        else {
          val dir = if (java.nio.file.Files.isDirectory(resolved)) resolved else resolved.getParent
          AvroParser.parseDirectory(dir) match {
            case Right(files) => Right(soFar ++ files)
            case Left(error)  => Left(s"failed to parse $rel: $error")
          }
        }
      }
    }
    combined
  }

}
