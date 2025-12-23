package typr
package internal
package sqlglot

import play.api.libs.json.Json
import typr.internal.analysis.NullabilityFromExplain
import typr.internal.external.ExternalTools

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.sys.process._
import scala.util.{Failure, Success, Try}

/** Runs sqlglot analysis on SQL files using a Python subprocess */
object SqlglotAnalyzer {

  /** Result of running the analyzer */
  sealed trait AnalyzerResult
  object AnalyzerResult {
    case class Success(output: SqlglotAnalysisOutput) extends AnalyzerResult
    case class PythonError(exitCode: Int, stderr: String) extends AnalyzerResult
    case class JsonParseError(json: String, error: String) extends AnalyzerResult
    case class ProcessError(message: String) extends AnalyzerResult
  }

  /** Configuration for the analyzer */
  case class Config(
      pythonBinary: Path,
      sqlglotScript: Path,
      pythonPath: Option[Path]
  )

  /** Extract Python script from resources to a temp file */
  private def extractScriptFromResources(): Option[Path] = {
    Try {
      val resourceStream = getClass.getResourceAsStream("/sqlglot_analyze.py")
      if (resourceStream == null) {
        None
      } else {
        // Use UUID to ensure unique file name for parallel invocations
        val uuid = java.util.UUID.randomUUID().toString
        val tempFile = Files.createTempFile(s"sqlglot_analyze_${uuid}_", ".py")
        tempFile.toFile.deleteOnExit()
        Files.copy(resourceStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        resourceStream.close()
        Some(tempFile)
      }
    }.toOption.flatten
  }

  /** Create config from ExternalTools (downloaded Python + sqlglot) */
  def configFromExternalTools(tools: ExternalTools): Config = {
    val scriptPath = extractScriptFromResources().getOrElse(
      throw new RuntimeException("Could not extract sqlglot_analyze.py from resources")
    )
    Config(
      pythonBinary = tools.python.binary,
      sqlglotScript = scriptPath,
      pythonPath = Some(tools.sqlglot.pythonPath)
    )
  }

  /** Run the sqlglot analyzer on a batch of SQL files */
  def analyze(config: Config, input: SqlglotAnalysisInput): AnalyzerResult = {
    val inputJson = Json.stringify(Json.toJson(input)(SqlglotAnalysisInput.writes))

    val stdout = new StringBuilder
    val stderr = new StringBuilder

    val processLogger = ProcessLogger(
      (line: String) => {
        stdout.append(line).append("\n"): Unit
        // Don't print stdout in real-time since it's JSON
      },
      (line: String) => {
        stderr.append(line).append("\n"): Unit
        // Print stderr in real-time for debugging
        System.err.println(line)
      }
    )

    Try {
      val inputStream = new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8))

      // Create unique temp directory for this Python process to avoid conflicts
      val uuid = java.util.UUID.randomUUID().toString
      val tempDir = Files.createTempDirectory(s"typo_sqlglot_${uuid}_")
      tempDir.toFile.deleteOnExit()

      // Build environment variables
      val envVars = List(
        "PYTHONIOENCODING" -> "utf-8",
        "PYTHONDONTWRITEBYTECODE" -> "1", // Prevent .pyc file creation for parallel safety
        "TMPDIR" -> tempDir.toString, // Use unique temp dir for this process
        "TEMP" -> tempDir.toString, // Windows equivalent
        "TMP" -> tempDir.toString // Another Windows equivalent
      ) ++ config.pythonPath.map(p => "PYTHONPATH" -> p.toString).toList

      val exitCode = Process(
        Seq(config.pythonBinary.toString, config.sqlglotScript.toString),
        None,
        envVars*
      ).#<(inputStream).!(processLogger)

      exitCode
    } match {
      case Failure(ex) =>
        AnalyzerResult.ProcessError(s"Failed to run Python process: ${ex.getMessage}")

      case Success(exitCode) if exitCode != 0 =>
        AnalyzerResult.PythonError(exitCode, stderr.toString())

      case Success(_) =>
        // stderr is already printed in real-time by ProcessLogger
        val outputJson = stdout.toString().trim
        if (outputJson.isEmpty) {
          AnalyzerResult.JsonParseError("", "Empty output from Python script")
        } else {
          Try(Json.parse(outputJson)) match {
            case Failure(ex) =>
              AnalyzerResult.JsonParseError(outputJson, s"Failed to parse JSON: ${ex.getMessage}")
            case Success(json) =>
              json
                .validate[SqlglotAnalysisOutput]
                .fold(
                  errors => AnalyzerResult.JsonParseError(outputJson, s"JSON validation failed: ${errors.mkString(", ")}"),
                  output => AnalyzerResult.Success(output)
                )
          }
        }
    }
  }

  /** Build schema input from table metadata for MariaDB */
  def buildSchemaFromMariaTables(tables: Map[db.RelationName, Lazy[db.Table]]): Map[String, Map[String, SqlglotColumnSchema]] = {
    tables.flatMap { case (relationName, lazyTable) =>
      Try {
        val table = lazyTable.forceGet
        val tableName = relationName.schema match {
          case Some(schema) => s"$schema.${relationName.name}"
          case None         => relationName.name
        }

        val pkCols = table.primaryKey.map(_.colNames.toList.map(_.value).toSet).getOrElse(Set.empty)

        val columns = table.cols.toList.map { col =>
          val nullable = col.nullability match {
            case Nullability.NoNulls         => false
            case Nullability.Nullable        => true
            case Nullability.NullableUnknown => true
          }

          val colName = col.parsedName.originalName.value
          colName -> SqlglotColumnSchema(
            `type` = col.udtName.getOrElse("VARCHAR"),
            nullable = nullable,
            primaryKey = pkCols.contains(colName)
          )
        }.toMap

        tableName -> columns
      }.toOption
    }
  }

  /** Build schema input from table metadata for PostgreSQL */
  def buildSchemaFromPgTables(tables: Map[db.RelationName, Lazy[db.Table]]): Map[String, Map[String, SqlglotColumnSchema]] = {
    tables.flatMap { case (relationName, lazyTable) =>
      Try {
        val table = lazyTable.forceGet
        val tableName = relationName.schema match {
          case Some(schema) => s"$schema.${relationName.name}"
          case None         => s"public.${relationName.name}"
        }

        val pkCols = table.primaryKey.map(_.colNames.toList.map(_.value).toSet).getOrElse(Set.empty)

        val columns = table.cols.toList.map { col =>
          val nullable = col.nullability match {
            case Nullability.NoNulls         => false
            case Nullability.Nullable        => true
            case Nullability.NullableUnknown => true
          }

          val colName = col.parsedName.originalName.value
          colName -> SqlglotColumnSchema(
            `type` = col.udtName.getOrElse("VARCHAR"),
            nullable = nullable,
            primaryKey = pkCols.contains(colName)
          )
        }.toMap

        tableName -> columns
      }.toOption
    }
  }

  /** Extract nullable column indices from sqlglot analysis result */
  def extractNullableIndices(result: SqlglotFileResult): Option[NullabilityFromExplain.NullableIndices] = {
    if (!result.success) return None

    val nullableIndices = result.columns.zipWithIndex.collect {
      case (col, idx) if col.nullableFromJoin => idx
    }.toSet

    if (nullableIndices.isEmpty) None
    else Some(NullabilityFromExplain.NullableIndices(nullableIndices))
  }
}
