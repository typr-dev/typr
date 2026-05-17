package typr.cli.app

import io.circe.Json
import typr.cli.config.{ConfigParser, ParsedSource}
import typr.config.generated.GrpcBoundary
import typr.grpc.ProtoFile
import typr.grpc.parser.ProtobufParser

import java.nio.file.Path

/** Parse a gRPC source. Decodes the source JSON via [[ConfigParser.parseSource]] into a typed [[GrpcBoundary]]; reads `proto_path` (or first of `proto_paths`) plus optional `include_paths`; invokes
  * protoc through [[ProtobufParser.parseDirectory]].
  *
  * Failures surface as `Left(message)`.
  */
object ProtoFetch {

  def fetch(json: Json, buildDir: Path): Either[String, List[ProtoFile]] =
    for {
      parsed <- ConfigParser.parseSource(json)
      boundary <- extractGrpc(parsed)
      protoPath <- protoPathFor(boundary)
      resolved = buildDir.resolve(protoPath)
      _ <- Either.cond(
        java.nio.file.Files.isDirectory(resolved),
        (),
        s"proto directory not found: $resolved"
      )
      includes = boundary.include_paths.toList.flatten.map(p => buildDir.resolve(p))
      files <- ProtobufParser.parseDirectory(resolved, includes).left.map(err => s"failed to parse $protoPath: $err")
    } yield files

  private def extractGrpc(parsed: ParsedSource): Either[String, GrpcBoundary] = parsed match {
    case ParsedSource.Grpc(b) => Right(b)
    case other                => Left(s"not a grpc source: ${other.sourceType}")
  }

  private def protoPathFor(b: GrpcBoundary): Either[String, String] =
    b.proto_path
      .orElse(b.proto_paths.flatMap(_.headOption))
      .toRight("grpc source has no `proto_path` or `proto_paths` field")

}
