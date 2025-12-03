package typo
package internal
package mariadb

import anorm.*
import typo.internal.analysis.*
import typo.internal.sqlfiles.SqlFile
import typo.internal.sqlglot.*

import java.nio.file.{Files, Path}
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** MariaDB-specific SQL file metadata extraction using sqlglot for analysis.
  *
  * Flow:
  *   1. Read table schema from INFORMATION_SCHEMA 2. Find all SQL files and parse with DecomposedSql 3. Pass schema + SQL contents (rendered with :param syntax) to sqlglot 4. Use sqlglot results for:
  *      query type, column types, parameter types, nullability, lineage
  */
object MariaSqlFileMetadata {

  /** Table column info from INFORMATION_SCHEMA */
  private case class TableColumnInfo(
      tableSchema: Option[String],
      tableName: String,
      columnName: String,
      dataType: String,
      columnType: String, // Full type with modifiers like "smallint(5) unsigned"
      isNullable: Boolean,
      columnKey: String
  )

  private object TableColumnInfo {
    def rowParser(idx: Int): RowParser[TableColumnInfo] = RowParser[TableColumnInfo] { row =>
      Success(
        TableColumnInfo(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          columnName = row(idx + 2)(Column.columnToString),
          dataType = row(idx + 3)(Column.columnToString),
          columnType = row(idx + 4)(Column.columnToString),
          isNullable = row(idx + 5)(Column.columnToString) == "YES",
          columnKey = row(idx + 6)(Column.columnToString)
        )
      )
    }
  }

  def apply(
      logger: TypoLogger,
      scriptsPath: Path,
      ds: TypoDataSource
  )(implicit ec: ExecutionContext): Future[List[SqlFile]] = {
    // Step 1: Read table schema from database
    val schemaFuture = ds.run { implicit c =>
      logger.info("Reading table schema from MariaDB for sqlglot analysis")
      readTableSchema(c)
    }

    schemaFuture.flatMap { schema =>
      // Step 2: Find all SQL files and parse them
      val sqlFilePaths = findSqlFilesUnder(scriptsPath)
      if (sqlFilePaths.isEmpty) {
        Future.successful(Nil)
      } else {
        // Parse all files with DecomposedSql
        val parsedFiles = sqlFilePaths.map { path =>
          val content = Files.readString(path)
          val decomposed = DecomposedSql.parse(content)
          (path, decomposed)
        }

        // Step 3: Run sqlglot analysis on all files
        val sqlglotResults = runSqlglotAnalysis(logger, schema, parsedFiles)

        // Step 4: Build SqlFile objects from sqlglot results
        Future.successful(parsedFiles.flatMap { case (path, decomposed) =>
          buildSqlFile(logger, path, scriptsPath, decomposed, sqlglotResults.get(path.toString), schema)
        })
      }
    }
  }

  /** Read table schema from INFORMATION_SCHEMA for sqlglot */
  private def readTableSchema(implicit conn: Connection): Map[String, Map[String, SqlglotColumnSchema]] = {
    val sql = SQL"""
      SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
      ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
    """

    val columns = sql.as(TableColumnInfo.rowParser(1).*)

    // Group by table and build schema map
    columns
      .groupBy(c => c.tableSchema.map(s => s"$s.${c.tableName}").getOrElse(c.tableName))
      .map { case (tableName, cols) =>
        val columnMap = cols.map { c =>
          // Include UNSIGNED modifier from COLUMN_TYPE if present
          val isUnsigned = c.columnType.toLowerCase.contains("unsigned")
          val fullType = if (isUnsigned) s"${c.dataType.toUpperCase} UNSIGNED" else c.dataType.toUpperCase
          c.columnName -> SqlglotColumnSchema(
            `type` = fullType,
            nullable = c.isNullable,
            primaryKey = c.columnKey == "PRI"
          )
        }.toMap
        tableName -> columnMap
      }
  }

  /** Run sqlglot analysis on all SQL files */
  private def runSqlglotAnalysis(
      logger: TypoLogger,
      schema: Map[String, Map[String, SqlglotColumnSchema]],
      parsedFiles: List[(Path, DecomposedSql)]
  ): Map[String, SqlglotFileResult] = {
    val config = SqlglotAnalyzer.defaultConfig() match {
      case Some(c) => c
      case None =>
        logger.warn("Could not initialize sqlglot analyzer (Python not found or script resource missing)")
        return Map.empty
    }

    // Build input for sqlglot - render SQL with :param_name syntax
    val fileInputs = parsedFiles.map { case (path, decomposed) =>
      // Render with :param0, :param1, etc. for unnamed params, or :name for named params
      val sqlForSqlglot = renderForSqlglot(decomposed)
      SqlglotFileInput(path.toString, sqlForSqlglot)
    }

    val input = SqlglotAnalysisInput(
      dialect = "mysql",
      schema = schema,
      files = fileInputs
    )

    SqlglotAnalyzer.analyze(config, input) match {
      case SqlglotAnalyzer.AnalyzerResult.Success(output) =>
        output.results.map(r => r.path -> r).toMap
      case SqlglotAnalyzer.AnalyzerResult.PythonError(code, stderr) =>
        logger.warn(s"sqlglot analysis failed with exit code $code: $stderr")
        Map.empty
      case SqlglotAnalyzer.AnalyzerResult.JsonParseError(_, error) =>
        logger.warn(s"sqlglot analysis returned invalid JSON: $error")
        Map.empty
      case SqlglotAnalyzer.AnalyzerResult.ProcessError(msg) =>
        logger.warn(s"sqlglot analysis process error: $msg")
        Map.empty
    }
  }

  /** Render DecomposedSql for sqlglot - uses :param_name syntax */
  private def renderForSqlglot(decomposed: DecomposedSql): String = {
    var unnamedIdx = 0
    decomposed.frags.map {
      case DecomposedSql.SqlText(text) => text
      case DecomposedSql.NotNamedParam =>
        val name = s":param$unnamedIdx"
        unnamedIdx += 1
        name
      case DecomposedSql.NamedParam(parsedName) =>
        // Use the base name without type annotations for sqlglot
        s":${parsedName.name.value}"
    }.mkString
  }

  /** Build SqlFile from sqlglot results */
  private def buildSqlFile(
      logger: TypoLogger,
      path: Path,
      scriptsPath: Path,
      decomposed: DecomposedSql,
      sqlglotResult: Option[SqlglotFileResult],
      schema: Map[String, Map[String, SqlglotColumnSchema]]
  ): Option[SqlFile] = {
    sqlglotResult match {
      case None =>
        logger.warn(s"No sqlglot result for $path, skipping")
        None

      case Some(result) if !result.success =>
        logger.warn(s"sqlglot failed to parse $path: ${result.error.getOrElse("unknown error")}, skipping")
        None

      case Some(result) =>
        val queryType = result.queryType.map(_.toUpperCase)

        queryType match {
          case Some("SELECT") | Some("WITH") =>
            buildSelectSqlFile(logger, path, scriptsPath, decomposed, result, schema)

          case Some("UPDATE") | Some("INSERT") | Some("DELETE") =>
            // Non-SELECT queries don't return rows
            buildParams(logger, path, decomposed, result, schema).map { params =>
              val jdbcMetadata = JdbcMetadata(params, MaybeReturnsRows.Update)
              SqlFile(RelPath.relativeTo(scriptsPath, path), decomposed, jdbcMetadata, None)
            }

          case Some(other) =>
            logger.warn(s"Unknown query type '$other' for $path, skipping")
            None

          case None =>
            // No query type detected - might be empty or unparseable
            if (
              result.columns.isEmpty && decomposed.frags.forall {
                case DecomposedSql.SqlText(t) => t.trim.isEmpty
                case _                        => false
              }
            ) {
              logger.info(s"Skipping $path because it's empty")
            } else {
              logger.warn(s"Could not determine query type for $path, skipping")
            }
            None
        }
    }
  }

  /** Build SqlFile for SELECT queries */
  private def buildSelectSqlFile(
      logger: TypoLogger,
      path: Path,
      scriptsPath: Path,
      decomposed: DecomposedSql,
      result: SqlglotFileResult,
      schema: Map[String, Map[String, SqlglotColumnSchema]]
  ): Option[SqlFile] = {
    if (result.columns.isEmpty) {
      logger.warn(s"sqlglot found no columns for $path, skipping")
      return None
    }

    // Check that all columns have types - fail early if any is missing
    val missingTypes = result.columns.filter(col => col.sourceType.orElse(col.inferredType).isEmpty)
    if (missingTypes.nonEmpty) {
      val missing = missingTypes.map(c => s"${c.name}").mkString(", ")
      logger.warn(s"Columns without type information in $path: $missing, skipping")
      return None
    }

    val cols = result.columns.map { col =>
      val parsedName = ParsedName.of(col.alias.getOrElse(col.name))
      // Prefer sourceType (actual database type from schema) over inferredType (sqlglot's inference)
      val effectiveType = col.sourceType.orElse(col.inferredType).get // Safe: checked above

      // Nullable if schema says so OR if from a LEFT JOIN
      val isNullable =
        if (col.nullableFromJoin) ColumnNullable.Nullable
        else if (col.nullableInSchema) ColumnNullable.Nullable
        else ColumnNullable.NoNulls

      MetadataColumn(
        baseColumnName = col.sourceColumn.map(db.ColName.apply),
        baseRelationName = col.sourceTable.map(t => db.RelationName(None, t)),
        catalogName = None,
        columnClassName = "", // Not used
        columnDisplaySize = 0,
        parsedColumnName = parsedName,
        columnName = db.ColName(col.name),
        columnType = JdbcType.VarChar, // Not used
        columnTypeName = effectiveType,
        format = 0,
        isAutoIncrement = false,
        isCaseSensitive = true,
        isCurrency = false,
        isDefinitelyWritable = false,
        isNullable = isNullable,
        isReadOnly = true,
        isSearchable = true,
        isSigned = true,
        isWritable = false,
        precision = 0,
        scale = 0,
        schemaName = None,
        tableName = col.sourceTable
      )
    }

    NonEmptyList.fromList(cols) match {
      case None =>
        logger.warn(s"No columns found for $path, skipping")
        None
      case Some(nel) =>
        buildParams(logger, path, decomposed, result, schema).map { params =>
          val jdbcMetadata = JdbcMetadata(params, MaybeReturnsRows.Query(nel))
          val nullableIndices = SqlglotAnalyzer.extractNullableIndices(result)
          SqlFile(RelPath.relativeTo(scriptsPath, path), decomposed, jdbcMetadata, nullableIndices)
        }
    }
  }

  /** Build parameter metadata from sqlglot results. Returns None if any parameter is missing type info. */
  private def buildParams(
      logger: TypoLogger,
      path: Path,
      decomposed: DecomposedSql,
      result: SqlglotFileResult,
      schema: Map[String, Map[String, SqlglotColumnSchema]]
  ): Option[List[MetadataParameterColumn]] = {
    // Group all sqlglot parameters by name for efficient lookup
    val paramsByName: Map[String, List[SqlglotParameterInfo]] = result.parameters.groupBy(_.name)

    val paramsWithTypes = decomposed.params.zipWithIndex.map { case (param, idx) =>
      // Match by position or name
      val (paramName, hasUserOverride) = param match {
        case DecomposedSql.NotNamedParam          => (s"param$idx", false)
        case DecomposedSql.NamedParam(parsedName) => (parsedName.name.value, parsedName.bareOverriddenType.isDefined)
      }

      val matchingParams = paramsByName.getOrElse(paramName, Nil)

      // Prefer parameters that have source info (table/column) over those that don't
      val sqlglotParam = matchingParams.find(p => p.sourceTable.isDefined && p.sourceColumn.isDefined).orElse(matchingParams.headOption)

      // Try to get the actual type from the schema using sourceTable and sourceColumn
      val schemaType: Option[String] = for {
        p <- sqlglotParam
        sourceTable <- p.sourceTable
        sourceColumn <- p.sourceColumn
        tableSchema <- schema
          .find { case (tableName, _) =>
            tableName.endsWith(s".$sourceTable") || tableName == sourceTable
          }
          .map(_._2)
        columnSchema <- tableSchema.get(sourceColumn)
      } yield columnSchema.`type`

      // If user provided a type override, we can use a placeholder type (the actual type will be resolved later)
      // Otherwise, prefer schema type > sqlglot inferred type
      val effectiveType =
        if (hasUserOverride) Some("VARCHAR") // Placeholder - user override will take precedence in ComputedSqlFile
        else schemaType.orElse(sqlglotParam.flatMap(_.inferredType))

      // Check nullability from any parameter occurrence (any with nullable hint means nullable)
      val hasNullableHint = matchingParams.exists(_.nullableHint)

      (paramName, effectiveType, hasNullableHint)
    }

    // Check for missing types
    val missingTypes = paramsWithTypes.filter(_._2.isEmpty).map(_._1)
    if (missingTypes.nonEmpty) {
      logger.warn(s"Parameters without type information in $path: ${missingTypes.mkString(", ")}, skipping")
      return None
    }

    Some(paramsWithTypes.map { case (_, effectiveTypeOpt, hasNullableHint) =>
      val typeName = effectiveTypeOpt.get // Safe: checked above

      MetadataParameterColumn(
        isNullable = if (hasNullableHint) ParameterNullable.Nullable else ParameterNullable.NullableUnknown,
        isSigned = true,
        parameterMode = ParameterMode.ModeIn,
        parameterType = JdbcType.VarChar, // Not used
        parameterTypeName = typeName,
        precision = 0,
        scale = 0
      )
    })
  }

  def findSqlFilesUnder(scriptsPath: Path): List[Path] = {
    if (!Files.exists(scriptsPath)) Nil
    else {
      val found = List.newBuilder[Path]
      Files.walkFileTree(
        scriptsPath,
        new java.nio.file.SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult = {
            if (file.toString.endsWith(".sql")) found += file
            java.nio.file.FileVisitResult.CONTINUE
          }
        }
      )
      found.result()
    }
  }
}
