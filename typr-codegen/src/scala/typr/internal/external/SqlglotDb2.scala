package typr
package internal
package external

import java.nio.file.{Files, Path}

/** Represents a downloaded sqlglot-db2-dialect installation (community fork with DB2 support).
  *
  * This is a fork of sqlglot that adds DB2 dialect support. See: https://github.com/thh6282/sqlglot-db2-dialect
  *
  * Note: The fork has the sqlglot module content directly in the root folder (not in a sqlglot/ subdirectory), so we need to set up a proper package structure by creating a symlink.
  */
case class SqlglotDb2(folder: Path, pythonPath: Path)

object SqlglotDb2 {

  /** The commit/tag from sqlglot-db2-dialect to use */
  val version: String = "main"

  def urlFor(osArch: OsArch): String = {
    val ext = osArch match {
      case OsArch.WindowsAmd64 => "zip"
      case _                   => "tar.gz"
    }
    // Download from main branch since there are no releases
    s"https://github.com/thh6282/sqlglot-db2-dialect/archive/refs/heads/main.$ext"
  }

  /** Fetch sqlglot-db2-dialect from GitHub */
  def fetch(coursier: TypoCoursier): Either[String, SqlglotDb2] = {
    val url = urlFor(coursier.osArch)
    coursier.fetchArtifact(url) match {
      case Left(err) =>
        Left(s"Couldn't download sqlglot-db2-dialect from url $url: $err")
      case Right(folder) =>
        // The extracted folder name is "sqlglot-db2-dialect-main"
        val extractedFolder = folder.toPath.resolve("sqlglot-db2-dialect-main")
        if (!Files.exists(extractedFolder)) {
          Left(s"Expected sqlglot-db2-dialect folder at $extractedFolder after extraction")
        } else {
          // The fork has module content directly in root, but Python expects a 'sqlglot' directory.
          // Create a wrapper directory with a symlink so 'import sqlglot' works.
          val wrapperDir = folder.toPath.resolve("pythonpath")
          val sqlglotLink = wrapperDir.resolve("sqlglot")

          if (!Files.exists(sqlglotLink)) {
            if (!Files.exists(wrapperDir)) {
              val _ = Files.createDirectories(wrapperDir)
            }
            // Create symlink: wrapperDir/sqlglot -> extractedFolder (using absolute path for reliability)
            val _ = Files.createSymbolicLink(sqlglotLink, extractedFolder.toAbsolutePath)
          }

          Right(SqlglotDb2(extractedFolder, wrapperDir))
        }
    }
  }
}
