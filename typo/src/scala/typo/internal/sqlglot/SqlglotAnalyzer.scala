package typo
package internal
package sqlglot

import play.api.libs.json.Json
import typo.internal.analysis.NullabilityFromExplain

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
      sqlglotScript: Path
  )

  /** Extract Python script from resources to a temp file */
  private def extractScriptFromResources(): Option[Path] = {
    Try {
      val resourceStream = getClass.getResourceAsStream("/sqlglot_analyze.py")
      if (resourceStream == null) {
        None
      } else {
        val tempFile = Files.createTempFile("sqlglot_analyze", ".py")
        tempFile.toFile.deleteOnExit()
        Files.copy(resourceStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        resourceStream.close()
        Some(tempFile)
      }
    }.toOption.flatten
  }

  /** Create config using system Python and script from resources */
  def defaultConfig(): Option[Config] = {
    for {
      pythonBinary <- findPython()
      scriptPath <- extractScriptFromResources()
    } yield Config(pythonBinary, scriptPath)
  }

  /** Find Python binary */
  private def findPython(): Option[Path] = {
    val pythonCandidates = List("python3", "python")
    pythonCandidates.collectFirst {
      case cmd if Try(s"which $cmd".!!.trim).toOption.exists(_.nonEmpty) =>
        Path.of(s"which $cmd".!!.trim)
    }
  }

  /** Create config using system Python */
  def systemPythonConfig(scriptDir: Path): Option[Config] = {
    findPython().map { python =>
      Config(
        pythonBinary = python,
        sqlglotScript = scriptDir.resolve("sqlglot_analyze.py")
      )
    }
  }

  /** Run the sqlglot analyzer on a batch of SQL files */
  def analyze(config: Config, input: SqlglotAnalysisInput): AnalyzerResult = {
    val inputJson = Json.stringify(Json.toJson(input)(SqlglotAnalysisInput.writes))

    val stdout = new StringBuilder
    val stderr = new StringBuilder

    val processLogger = ProcessLogger(
      (line: String) => { stdout.append(line).append("\n"): Unit },
      (line: String) => { stderr.append(line).append("\n"): Unit }
    )

    Try {
      val inputStream = new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8))

      val exitCode = Process(
        Seq(config.pythonBinary.toString, config.sqlglotScript.toString),
        None,
        "PYTHONIOENCODING" -> "utf-8"
      ).#<(inputStream).!(processLogger)

      exitCode
    } match {
      case Failure(ex) =>
        AnalyzerResult.ProcessError(s"Failed to run Python process: ${ex.getMessage}")

      case Success(exitCode) if exitCode != 0 =>
        AnalyzerResult.PythonError(exitCode, stderr.toString())

      case Success(_) =>
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
