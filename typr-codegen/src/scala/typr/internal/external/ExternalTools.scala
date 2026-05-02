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
    config: ExternalToolsConfig,
    python: Python,
    sqlglot: Sqlglot,
    sqlglotDb2: Option[SqlglotDb2]
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

    ExternalTools(config, python, sqlglot, sqlglotDb2 = None)
  }

  /** Ensure sqlglot-db2-dialect is available (download if needed). Returns a new ExternalTools with sqlglotDb2 populated.
    */
  def withDb2Dialect(logger: TypoLogger, tools: ExternalTools): ExternalTools = synchronized {
    tools.sqlglotDb2 match {
      case Some(_) => tools // Already downloaded
      case None =>
        logger.info("Ensuring sqlglot-db2-dialect is available...")

        if (!Files.exists(tools.config.downloadsDir)) {
          val _ = Files.createDirectories(tools.config.downloadsDir)
        }

        val osArch = OsArch.current
        val coursier = TypoCoursier(logger, tools.config.downloadsDir, osArch)

        val sqlglotDb2 = SqlglotDb2.fetch(coursier) match {
          case Left(err)         => throw new RuntimeException(s"Failed to download sqlglot-db2-dialect: $err")
          case Right(sqlglotDb2) => sqlglotDb2
        }
        logger.info(s"sqlglot-db2-dialect available at: ${sqlglotDb2.folder}")

        tools.copy(sqlglotDb2 = Some(sqlglotDb2))
    }
  }
}
