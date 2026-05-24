package typr
package internal
package sqlite

import _root_.typr.internal.analysis.*
import _root_.typr.internal.external.ExternalTools
import _root_.typr.internal.sqlfiles.SqlFile
import _root_.typr.internal.sqlglot.*

import java.nio.file.{Files, Path}
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** SQLite-specific SQL file metadata extraction via sqlglot. Mirrors DuckDbSqlFileMetadata. */
object SqliteSqlFileMetadata {

  private case class TableColumnInfo(
      tableName: String,
      columnName: String,
      dataType: String,
      isNullable: Boolean,
      isPrimaryKey: Boolean
  )

  def apply(
      logger: TypoLogger,
      scriptsPath: Path,
      ds: TypoDataSource,
      externalTools: ExternalTools
  )(implicit ec: ExecutionContext): Future[List[SqlFile]] = {
    val schemaFuture = ds.run { implicit c =>
      logger.info("Reading table schema from SQLite for sqlglot analysis")
      readTableSchema(c)
    }

    schemaFuture.flatMap { schema =>
      val sqlFilePaths = findSqlFilesUnder(scriptsPath)
      if (sqlFilePaths.isEmpty) {
        Future.successful(Nil)
      } else {
        val parsedFiles = sqlFilePaths.map { path =>
          val content = Files.readString(path)
          val decomposed = DecomposedSql.parse(content)
          (path, decomposed)
        }

        val sqlglotResults = runSqlglotAnalysis(logger, externalTools, schema, parsedFiles)

        Future.successful(parsedFiles.flatMap { case (path, decomposed) =>
          buildSqlFile(logger, path, scriptsPath, decomposed, sqlglotResults.get(path.toString), schema)
        })
      }
    }
  }

  /** Read SQLite schema by walking sqlite_master + PRAGMA table_info. */
  private def readTableSchema(implicit conn: Connection): Map[String, Map[String, SqlglotColumnSchema]] = {
    val tableNames: List[String] = {
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(
          """SELECT name FROM sqlite_master
             WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%'
             ORDER BY name"""
        )
        try {
          val out = List.newBuilder[String]
          while (rs.next()) out += rs.getString(1)
          out.result()
        } finally rs.close()
      } finally stmt.close()
    }

    tableNames.map { tableName =>
      val pragmaSql = s"""SELECT name, type, "notnull", pk
                          FROM pragma_table_info('${tableName.replace("'", "''")}')"""
      val cols: List[TableColumnInfo] = {
        val stmt = conn.createStatement()
        try {
          val rs = stmt.executeQuery(pragmaSql)
          try {
            val out = List.newBuilder[TableColumnInfo]
            while (rs.next()) {
              out += TableColumnInfo(
                tableName = tableName,
                columnName = rs.getString("name"),
                dataType = Option(rs.getString("type")).getOrElse(""),
                isNullable = rs.getInt("notnull") == 0,
                isPrimaryKey = rs.getInt("pk") > 0
              )
            }
            out.result()
          } finally rs.close()
        } finally stmt.close()
      }

      val columnMap = cols.map { c =>
        c.columnName -> SqlglotColumnSchema(
          `type` = if (c.dataType.isEmpty) "TEXT" else c.dataType.toUpperCase,
          nullable = c.isNullable && !c.isPrimaryKey,
          primaryKey = c.isPrimaryKey
        )
      }.toMap

      tableName -> columnMap
    }.toMap
  }

  private def runSqlglotAnalysis(
      logger: TypoLogger,
      externalTools: ExternalTools,
      schema: Map[String, Map[String, SqlglotColumnSchema]],
      parsedFiles: List[(Path, DecomposedSql)]
  ): Map[String, SqlglotFileResult] = {
    val config = SqlglotAnalyzer.configFromExternalTools(externalTools)

    val fileInputs = parsedFiles.map { case (path, decomposed) =>
      SqlglotFileInput(path.toString, renderForSqlglot(decomposed))
    }

    val input = SqlglotAnalysisInput(
      dialect = "sqlite",
      schema = schema,
      files = fileInputs
    )

    SqlglotAnalyzer.analyze(config, input, Some(logger)) match {
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

  private def renderForSqlglot(decomposed: DecomposedSql): String = {
    var unnamedIdx = 0
    decomposed.frags.map {
      case DecomposedSql.SqlText(text) => text
      case DecomposedSql.NotNamedParam =>
        val name = s":param$unnamedIdx"
        unnamedIdx += 1
        name
      case DecomposedSql.NamedParam(parsedName) =>
        s":${parsedName.name.value}"
    }.mkString
  }

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
        result.queryType.map(_.toUpperCase) match {
          case Some("SELECT") | Some("WITH") =>
            buildSelectSqlFile(logger, path, scriptsPath, decomposed, result, schema)

          case Some("UPDATE") | Some("INSERT") | Some("DELETE") =>
            if (result.columns.nonEmpty) {
              buildSelectSqlFile(logger, path, scriptsPath, decomposed, result, schema)
            } else {
              buildParams(logger, path, decomposed, result, schema).map { params =>
                val jdbcMetadata = JdbcMetadata(params, MaybeReturnsRows.Update)
                SqlFile(RelPath.relativeTo(scriptsPath, path), decomposed, jdbcMetadata, None)
              }
            }

          case Some(other) =>
            logger.warn(s"Unknown query type '$other' for $path, skipping")
            None

          case None =>
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

    val missingTypes = result.columns.filter(col => col.sourceType.orElse(col.inferredType).isEmpty)
    if (missingTypes.nonEmpty) {
      val missing = missingTypes.map(c => s"${c.name}").mkString(", ")
      logger.warn(s"Columns without type information in $path: $missing, skipping")
      return None
    }

    val cols = result.columns.map { col =>
      val parsedName = ParsedName.of(col.alias.getOrElse(col.name))
      val effectiveType =
        if (col.isExpression) col.inferredType.orElse(col.sourceType).get
        else col.sourceType.orElse(col.inferredType).get

      val isNullable =
        if (col.nullableFromJoin) ColumnNullable.Nullable
        else if (col.nullableInSchema) ColumnNullable.Nullable
        else ColumnNullable.NoNulls

      MetadataColumn(
        baseColumnName = col.sourceColumn.map(db.ColName.apply),
        baseRelationName = col.sourceTable.map(t => db.RelationName(None, t)),
        catalogName = None,
        columnClassName = "",
        columnDisplaySize = 0,
        parsedColumnName = parsedName,
        columnName = db.ColName(col.name),
        columnType = JdbcType.VarChar,
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
        tableName = col.sourceTable,
        isExpression = col.isExpression
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

  private def buildParams(
      logger: TypoLogger,
      path: Path,
      decomposed: DecomposedSql,
      result: SqlglotFileResult,
      schema: Map[String, Map[String, SqlglotColumnSchema]]
  ): Option[List[MetadataParameterColumn]] = {
    val paramsByName: Map[String, List[SqlglotParameterInfo]] = result.parameters.groupBy(_.name)

    val paramsWithTypes = decomposed.params.zipWithIndex.map { case (param, idx) =>
      val (paramName, userOverriddenType) = param match {
        case DecomposedSql.NotNamedParam          => (s"param$idx", None)
        case DecomposedSql.NamedParam(parsedName) => (parsedName.name.value, parsedName.overriddenType)
      }

      val matchingParams = paramsByName.getOrElse(paramName, Nil)
      val sqlglotParam =
        matchingParams
          .find(p => p.sourceTable.isDefined && p.sourceColumn.isDefined)
          .orElse(matchingParams.find(_.inferredType.isDefined))
          .orElse(matchingParams.headOption)

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

      val fallbackFromUserType: Option[String] = userOverriddenType.flatMap {
        case OverriddenType.Primitive(p) => Some(sqliteTypeNameFor(p))
        case OverriddenType.Qualified(_) => None
      }

      val effectiveType = schemaType.orElse(sqlglotParam.flatMap(_.inferredType)).orElse(fallbackFromUserType)
      val hasNullableHint = matchingParams.exists(_.nullableHint)

      (paramName, effectiveType, hasNullableHint)
    }

    val missingTypes = paramsWithTypes.filter(_._2.isEmpty).map(_._1)
    if (missingTypes.nonEmpty) {
      logger.warn(s"Parameters without type information in $path: ${missingTypes.mkString(", ")}, skipping")
      return None
    }

    Some(paramsWithTypes.map { case (_, effectiveTypeOpt, hasNullableHint) =>
      val typeName = effectiveTypeOpt.get
      MetadataParameterColumn(
        isNullable = if (hasNullableHint) ParameterNullable.Nullable else ParameterNullable.NullableUnknown,
        isSigned = true,
        parameterMode = ParameterMode.ModeIn,
        parameterType = JdbcType.VarChar,
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

  private def sqliteTypeNameFor(p: WellKnownPrimitive): String = p match {
    case WellKnownPrimitive.String        => "TEXT"
    case WellKnownPrimitive.Boolean       => "BOOLEAN"
    case WellKnownPrimitive.Byte          => "TINYINT"
    case WellKnownPrimitive.Short         => "SMALLINT"
    case WellKnownPrimitive.Int           => "INT"
    case WellKnownPrimitive.Long          => "INTEGER"
    case WellKnownPrimitive.Float         => "FLOAT"
    case WellKnownPrimitive.Double        => "REAL"
    case WellKnownPrimitive.BigDecimal    => "NUMERIC"
    case WellKnownPrimitive.LocalDate     => "DATE"
    case WellKnownPrimitive.LocalTime     => "TIME"
    case WellKnownPrimitive.LocalDateTime => "DATETIME"
    case WellKnownPrimitive.Instant       => "TIMESTAMP"
    case WellKnownPrimitive.UUID          => "UUID"
  }
}
