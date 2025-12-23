package typr
package internal
package external

import java.nio.file.{Files, Path}

/** Configuration for external tools */
case class ExternalToolsConfig(
    downloadsDir: Path,
    pythonVersion: String,
    pythonStandaloneBuildDate: String,
    sqlglotVersion: String
)

object ExternalToolsConfig {
  val default: ExternalToolsConfig = ExternalToolsConfig(
    downloadsDir = Path.of(".typo"),
    pythonVersion = "3.13.2",
    pythonStandaloneBuildDate = "20250317",
    sqlglotVersion = "28.1.0"
  )
}

/** Container for initialized external tools (Python + sqlglot) */
case class ExternalTools(
    python: Python,
    sqlglot: Sqlglot
)

object ExternalTools {

  /** Initialize external tools, downloading Python and sqlglot if needed */
  def init(logger: TypoLogger, config: ExternalToolsConfig): ExternalTools = synchronized {
    logger.info("Initializing external tools for SQL analysis...")

    // Ensure downloads directory exists
    if (!Files.exists(config.downloadsDir)) {
      val _ = Files.createDirectories(config.downloadsDir)
    }

    val osArch = OsArch.current
    logger.info(s"Detected OS/Arch: ${osArch.os.value}/${osArch.arch}")

    val coursier = TypoCoursier(logger, config.downloadsDir, osArch)

    // Download Python
    logger.info(s"Ensuring Python ${config.pythonVersion} is available...")
    val python = Python.fetch(coursier, config.pythonVersion, config.pythonStandaloneBuildDate) match {
      case Left(err)     => throw new RuntimeException(s"Failed to download Python: $err")
      case Right(python) => python
    }
    logger.info(s"Python available at: ${python.binary}")

    // Download sqlglot
    logger.info(s"Ensuring sqlglot ${config.sqlglotVersion} is available...")
    val sqlglot = Sqlglot.fetch(coursier, config.sqlglotVersion) match {
      case Left(err)      => throw new RuntimeException(s"Failed to download sqlglot: $err")
      case Right(sqlglot) => sqlglot
    }
    logger.info(s"sqlglot available at: ${sqlglot.folder}")

    ExternalTools(python, sqlglot)
  }
}
