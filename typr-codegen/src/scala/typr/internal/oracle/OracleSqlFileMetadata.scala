package typr
package internal
package oracle

import _root_.anorm.*
import _root_.typr.internal.analysis.*
import _root_.typr.internal.external.ExternalTools
import _root_.typr.internal.sqlfiles.SqlFile
import _root_.typr.internal.sqlglot.*

import java.nio.file.{Files, Path}
import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** Oracle-specific SQL file metadata extraction using sqlglot for analysis.
  *
  * Flow:
  *   1. Read table schema from ALL_TAB_COLUMNS 2. Find all SQL files and parse with DecomposedSql 3. Pass schema + SQL contents (rendered with :param syntax) to sqlglot 4. Use sqlglot results for:
  *      query type, column types, parameter types, nullability, lineage
  */
object OracleSqlFileMetadata {

  /** Table column info from ALL_TAB_COLUMNS */
  private case class TableColumnInfo(
      owner: String,
      tableName: String,
      columnName: String,
      dataType: String,
      dataLength: Int,
      dataPrecision: Option[Int],
      dataScale: Option[Int],
      nullable: Boolean
  )

  private object TableColumnInfo {
    def rowParser(idx: Int): RowParser[TableColumnInfo] = RowParser[TableColumnInfo] { row =>
      Success(
        TableColumnInfo(
          owner = row(idx + 0)(Column.columnToString),
          tableName = row(idx + 1)(Column.columnToString),
          columnName = row(idx + 2)(Column.columnToString),
          dataType = row(idx + 3)(Column.columnToString),
          dataLength = row(idx + 4)(Column.columnToInt),
          dataPrecision = row(idx + 5)(Column.columnToOption(Column.columnToInt)),
          dataScale = row(idx + 6)(Column.columnToOption(Column.columnToInt)),
          nullable = row(idx + 7)(Column.columnToString) == "Y"
        )
      )
    }
  }

  def apply(
      logger: TypoLogger,
      scriptsPath: Path,
      ds: TypoDataSource,
      externalTools: ExternalTools
  )(implicit ec: ExecutionContext): Future[List[SqlFile]] = {
    // Step 1: Read table schema from database
    val schemaFuture = ds.run { implicit c =>
      logger.info("Reading table schema from Oracle for sqlglot analysis")
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
        val sqlglotResults = runSqlglotAnalysis(logger, externalTools, schema, parsedFiles)

        // Step 4: Build SqlFile objects from sqlglot results
        Future.successful(parsedFiles.flatMap { case (path, decomposed) =>
          buildSqlFile(logger, path, scriptsPath, decomposed, sqlglotResults.get(path.toString), schema)
        })
      }
    }
  }

  /** Read table schema from ALL_TAB_COLUMNS for sqlglot */
  private def readTableSchema(implicit conn: Connection): Map[String, Map[String, SqlglotColumnSchema]] = {
    val sql = SQL"""
      SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE
      FROM ALL_TAB_COLUMNS
      WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS',
                          'WMSYS', 'EXFSYS', 'CTXSYS', 'ANONYMOUS', 'XDB', 'ORDSYS', 'ORDDATA',
                          'MDSYS', 'OLAPSYS', 'LBACSYS', 'DVSYS', 'GSMADMIN_INTERNAL')
      ORDER BY OWNER, TABLE_NAME, COLUMN_ID
    """

    val columns = sql.as(TableColumnInfo.rowParser(1).*)

    // Group by table and build schema map
    columns
      .groupBy(c => s"${c.owner}.${c.tableName}")
      .map { case (tableName, cols) =>
        val columnMap = cols.map { c =>
          // Build full Oracle type name
          val fullType = buildOracleTypeName(c)
          c.columnName -> SqlglotColumnSchema(
            `type` = fullType,
            nullable = c.nullable,
            primaryKey = false // Oracle doesn't expose this easily in ALL_TAB_COLUMNS
          )
        }.toMap
        tableName -> columnMap
      }
  }

  /** Build full Oracle type name from column info */
  private def buildOracleTypeName(c: TableColumnInfo): String = {
    c.dataType match {
      case "NUMBER" =>
        (c.dataPrecision, c.dataScale) match {
          case (Some(p), Some(s)) if s > 0 => s"NUMBER($p,$s)"
          case (Some(p), _)                => s"NUMBER($p)"
          case _                           => "NUMBER"
        }
      case "VARCHAR2" | "NVARCHAR2" | "CHAR" | "NCHAR" =>
        s"${c.dataType}(${c.dataLength})"
      case "RAW" =>
        s"RAW(${c.dataLength})"
      case other => other
    }
  }

  /** Run sqlglot analysis on all SQL files */
  private def runSqlglotAnalysis(
      logger: TypoLogger,
      externalTools: ExternalTools,
      schema: Map[String, Map[String, SqlglotColumnSchema]],
      parsedFiles: List[(Path, DecomposedSql)]
  ): Map[String, SqlglotFileResult] = {
    val config = SqlglotAnalyzer.configFromExternalTools(externalTools)

    // Build input for sqlglot - render SQL with :param_name syntax
    val fileInputs = parsedFiles.map { case (path, decomposed) =>
      val sqlForSqlglot = renderForSqlglot(decomposed)
      SqlglotFileInput(path.toString, sqlForSqlglot)
    }

    val input = SqlglotAnalysisInput(
      dialect = "oracle",
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
            buildParams(logger, path, decomposed, result, schema).map { params =>
              val jdbcMetadata = JdbcMetadata(params, MaybeReturnsRows.Update)
              SqlFile(RelPath.relativeTo(scriptsPath, path), decomposed, jdbcMetadata, None)
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

    // Check that all columns have types
    val missingTypes = result.columns.filter(col => col.sourceType.orElse(col.inferredType).isEmpty)
    if (missingTypes.nonEmpty) {
      val missing = missingTypes.map(c => s"${c.name}").mkString(", ")
      logger.warn(s"Columns without type information in $path: $missing, skipping")
      return None
    }

    val cols = result.columns.map { col =>
      val parsedName = ParsedName.of(col.alias.getOrElse(col.name))
      // For expressions (aggregates, computed columns), prefer inferredType since sourceType is the input column type
      // For direct column references, prefer sourceType (actual database type from schema) over inferredType
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

  /** Build parameter metadata from sqlglot results */
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
      val sqlglotParam = matchingParams.find(p => p.sourceTable.isDefined && p.sourceColumn.isDefined).orElse(matchingParams.headOption)

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
        case OverriddenType.Primitive(p) => Some(oracleTypeNameFor(p))
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

  /** Map well-known primitives to Oracle type names */
  private def oracleTypeNameFor(p: WellKnownPrimitive): String = p match {
    case WellKnownPrimitive.String        => "VARCHAR2(4000)"
    case WellKnownPrimitive.Boolean       => "NUMBER(1)"
    case WellKnownPrimitive.Byte          => "NUMBER(3)"
    case WellKnownPrimitive.Short         => "NUMBER(5)"
    case WellKnownPrimitive.Int           => "NUMBER(10)"
    case WellKnownPrimitive.Long          => "NUMBER(19)"
    case WellKnownPrimitive.Float         => "BINARY_FLOAT"
    case WellKnownPrimitive.Double        => "BINARY_DOUBLE"
    case WellKnownPrimitive.BigDecimal    => "NUMBER"
    case WellKnownPrimitive.LocalDate     => "DATE"
    case WellKnownPrimitive.LocalTime     => "TIMESTAMP"
    case WellKnownPrimitive.LocalDateTime => "TIMESTAMP"
    case WellKnownPrimitive.Instant       => "TIMESTAMP WITH TIME ZONE"
    case WellKnownPrimitive.UUID          => "RAW(16)"
  }
}
