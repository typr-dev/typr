package typr
package internal
package external

import java.nio.file.{Files, Path}

/** Represents a downloaded sqlglot installation */
case class Sqlglot(folder: Path) {

  /** The path to add to PYTHONPATH */
  def pythonPath: Path = folder
}

object Sqlglot {

  def urlFor(osArch: OsArch, version: String): String = {
    val ext = osArch match {
      case OsArch.WindowsAmd64 => "zip"
      case _                   => "tar.gz"
    }
    s"https://github.com/tobymao/sqlglot/archive/refs/tags/v$version.$ext"
  }

  /** Fetch sqlglot from GitHub releases */
  def fetch(coursier: TypoCoursier, version: String): Either[String, Sqlglot] = {
    val url = urlFor(coursier.osArch, version)
    coursier.fetchArtifact(url) match {
      case Left(err) =>
        Left(s"Couldn't download sqlglot $version from url $url: $err")
      case Right(folder) =>
        val sqlglotFolder = folder.toPath.resolve(s"sqlglot-$version")
        if (!Files.exists(sqlglotFolder)) {
          Left(s"Expected sqlglot folder at $sqlglotFolder after extraction")
        } else {
          Right(Sqlglot(sqlglotFolder))
        }
    }
  }
}
