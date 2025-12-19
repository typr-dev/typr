package typo
package internal
package pg

import typo.generated.custom.comments.{CommentsSqlRepoImpl, CommentsSqlRow}
import typo.generated.custom.constraints.{ConstraintsSqlRepoImpl, ConstraintsSqlRow}
import typo.generated.custom.domains.{DomainsSqlRepoImpl, DomainsSqlRow}
import typo.generated.custom.enums.{EnumsSqlRepoImpl, EnumsSqlRow}
import typo.generated.custom.table_comments.*
import typo.generated.custom.view_find_all.*
import typo.generated.information_schema.columns.{ColumnsViewRepoImpl, ColumnsViewRow}
import typo.generated.information_schema.key_column_usage.{KeyColumnUsageViewRepoImpl, KeyColumnUsageViewRow}
import typo.generated.information_schema.referential_constraints.{ReferentialConstraintsViewRepoImpl, ReferentialConstraintsViewRow}
import typo.generated.information_schema.table_constraints.{TableConstraintsViewRepoImpl, TableConstraintsViewRow}
import typo.generated.information_schema.tables.{TablesViewRepoImpl, TablesViewRow}
import typo.internal.analysis.*
import typo.internal.external.ExternalTools
import typo.internal.sqlglot.*

import scala.collection.immutable.SortedSet
import scala.concurrent.{ExecutionContext, Future}

/** PostgreSQL-specific metadata extraction */
object PgMetaDb {
  case class Input(
      tableConstraints: List[TableConstraintsViewRow],
      keyColumnUsage: List[KeyColumnUsageViewRow],
      referentialConstraints: List[ReferentialConstraintsViewRow],
      pgEnums: List[EnumsSqlRow],
      tables: List[TablesViewRow],
      columns: List[ColumnsViewRow],
      views: Map[db.RelationName, AnalyzedView],
      domains: List[DomainsSqlRow],
      columnComments: List[CommentsSqlRow],
      constraints: List[ConstraintsSqlRow],
      tableComments: List[TableCommentsSqlRow]
  ) {
    def filter(schemaMode: SchemaMode): Input = {
      schemaMode match {
        case SchemaMode.MultiSchema => this
        case SchemaMode.SingleSchema(wantedSchema) =>
          def keep(os: Option[String]): Boolean = os.contains(wantedSchema)

          Input(
            tableConstraints = tableConstraints.collect {
              case x if keep(x.tableSchema) || keep(x.constraintSchema) =>
                x.copy(tableSchema = None, constraintSchema = None)
            },
            keyColumnUsage = keyColumnUsage.collect {
              case x if keep(x.tableSchema) || keep(x.constraintSchema) =>
                x.copy(tableSchema = None, constraintSchema = None)
            },
            referentialConstraints = referentialConstraints.collect {
              case x if keep(x.constraintSchema) =>
                x.copy(constraintSchema = None)
            },
            pgEnums = pgEnums.collect { case x if keep(x.enumSchema) => x.copy(enumSchema = None) },
            tables = tables.collect { case x if keep(x.tableSchema) => x.copy(tableSchema = None) },
            columns = columns.collect {
              case x if keep(x.tableSchema) =>
                x.copy(
                  tableSchema = None,
                  characterSetSchema = None,
                  collationSchema = None,
                  domainSchema = None,
                  udtSchema = None,
                  scopeSchema = None
                )
            },
            views = views.collect { case (k, v) if keep(k.schema) => k.copy(schema = None) -> v.copy(row = v.row.copy(tableSchema = None)) },
            domains = domains.collect { case x if keep(x.schema) => x.copy(schema = None) },
            columnComments = columnComments.collect { case c if keep(c.tableSchema) => c.copy(tableSchema = None) },
            constraints = constraints.collect { case c if keep(c.tableSchema) => c.copy(tableSchema = None) },
            tableComments = tableComments.collect { case c if keep(c.schema) => c.copy(schema = None) }
          )
      }
    }
  }

  /** Analyzed view data using sqlglot */
  case class AnalyzedView(
      row: ViewFindAllSqlRow,
      decomposedSql: DecomposedSql,
      sqlglotResult: SqlglotFileResult
  )

  object Input {
    def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode, externalTools: ExternalTools)(implicit ev: ExecutionContext): Future[Input] = {
      val tableConstraints = logger.timed("fetching tableConstraints")(ds.run(implicit c => (new TableConstraintsViewRepoImpl).selectAll))
      val keyColumnUsage = logger.timed("fetching keyColumnUsage")(ds.run(implicit c => (new KeyColumnUsageViewRepoImpl).selectAll))
      val referentialConstraints = logger.timed("fetching referentialConstraints")(ds.run(implicit c => (new ReferentialConstraintsViewRepoImpl).selectAll))
      val pgEnums = logger.timed("fetching pgEnums")(ds.run(implicit c => (new EnumsSqlRepoImpl).apply(c)))
      val tables = logger.timed("fetching tables")(ds.run(implicit c => (new TablesViewRepoImpl).selectAll.filter(_.tableType.contains("BASE TABLE"))))
      val columns = logger.timed("fetching columns")(ds.run(implicit c => (new ColumnsViewRepoImpl).selectAll))
      val domains = logger.timed("fetching domains")(ds.run(implicit c => (new DomainsSqlRepoImpl).apply(c)))
      val columnComments = logger.timed("fetching columnComments")(ds.run(implicit c => (new CommentsSqlRepoImpl).apply(c)))
      val constraints = logger.timed("fetching constraints")(ds.run(implicit c => (new ConstraintsSqlRepoImpl).apply(c)))
      val tableComments = logger.timed("fetching tableComments")(ds.run(implicit c => (new TableCommentsSqlRepoImpl).apply(c)))

      // Fetch view rows - we'll analyze them with sqlglot after getting the schema
      val viewRows = logger.timed("fetching view rows")(ds.run(implicit c => (new ViewFindAllSqlRepoImpl).apply(c)))

      for {
        tableConstraints <- tableConstraints
        keyColumnUsage <- keyColumnUsage
        referentialConstraints <- referentialConstraints
        pgEnums <- pgEnums
        tables <- tables
        columns <- columns
        viewRows <- viewRows
        domains <- domains
        columnComments <- columnComments
        constraints <- constraints
        tableComments <- tableComments
      } yield {
        // Build schema for sqlglot from columns
        val schema = buildSchemaForSqlglot(columns)

        // Analyze views with sqlglot
        val views = analyzeViewsWithSqlglot(logger, externalTools, schema, viewRows, viewSelector)

        val input = Input(
          tableConstraints,
          keyColumnUsage,
          referentialConstraints,
          pgEnums,
          tables,
          columns,
          views,
          domains,
          columnComments,
          constraints,
          tableComments
        )
        // todo: do this at SQL level instead for performance
        input.filter(schemaMode)
      }
    }

    /** Build schema map from columns for sqlglot */
    private def buildSchemaForSqlglot(columns: List[ColumnsViewRow]): Map[String, Map[String, SqlglotColumnSchema]] = {
      columns
        .groupBy(c => {
          val schema = c.tableSchema.getOrElse("public")
          val table = c.tableName.get
          s"$schema.$table"
        })
        .map { case (tableName, cols) =>
          val columnMap = cols.map { c =>
            val isNullable = c.isNullable.contains("YES")
            // Keep original type name (lowercase) - sqlglot will normalize it but we'll get it back in source_type
            c.columnName.get -> SqlglotColumnSchema(
              `type` = c.udtName.getOrElse("text"),
              nullable = isNullable,
              primaryKey = false // We don't have PK info readily available here, but sqlglot doesn't need it for views
            )
          }.toMap
          tableName -> columnMap
        }
    }

    /** Analyze views using sqlglot */
    private def analyzeViewsWithSqlglot(
        logger: TypoLogger,
        externalTools: ExternalTools,
        schema: Map[String, Map[String, SqlglotColumnSchema]],
        viewRows: List[ViewFindAllSqlRow],
        viewSelector: Selector
    ): Map[db.RelationName, AnalyzedView] = {
      // Filter views that we want to analyze
      val viewsToAnalyze = viewRows.flatMap { viewRow =>
        val name = db.RelationName(viewRow.tableSchema, viewRow.tableName.get)
        if (viewRow.viewDefinition.isDefined && viewSelector.include(name)) {
          Some((name, viewRow))
        } else None
      }

      if (viewsToAnalyze.isEmpty) {
        return Map.empty
      }

      // Prepare inputs for sqlglot
      val fileInputs = viewsToAnalyze.map { case (name, viewRow) =>
        val sqlContent = viewRow.viewDefinition.get
        SqlglotFileInput(name.value, sqlContent)
      }

      val config = SqlglotAnalyzer.configFromExternalTools(externalTools)
      val input = SqlglotAnalysisInput(
        dialect = "postgres",
        schema = schema,
        files = fileInputs
      )

      val sqlglotResults: Map[String, SqlglotFileResult] = SqlglotAnalyzer.analyze(config, input) match {
        case SqlglotAnalyzer.AnalyzerResult.Success(output) =>
          output.results.map(r => r.path -> r).toMap
        case SqlglotAnalyzer.AnalyzerResult.PythonError(code, stderr) =>
          logger.warn(s"sqlglot analysis for views failed with exit code $code: $stderr")
          Map.empty
        case SqlglotAnalyzer.AnalyzerResult.JsonParseError(_, error) =>
          logger.warn(s"sqlglot analysis for views returned invalid JSON: $error")
          Map.empty
        case SqlglotAnalyzer.AnalyzerResult.ProcessError(msg) =>
          logger.warn(s"sqlglot analysis for views process error: $msg")
          Map.empty
      }

      // Build AnalyzedView from results
      viewsToAnalyze.flatMap { case (name, viewRow) =>
        val sqlContent = viewRow.viewDefinition.get
        val decomposedSql = DecomposedSql.parse(sqlContent)

        sqlglotResults.get(name.value) match {
          case Some(result) if result.success =>
            Some(name -> AnalyzedView(viewRow, decomposedSql, result))
          case Some(result) =>
            logger.warn(s"sqlglot failed to parse view ${name.value}: ${result.error.getOrElse("unknown error")}")
            None
          case None =>
            logger.warn(s"No sqlglot result for view ${name.value}")
            None
        }
      }.toMap
    }
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode, externalTools: ExternalTools)(implicit ec: ExecutionContext): Future[MetaDb] =
    Input.fromDb(logger, ds, viewSelector, schemaMode, externalTools).map(input => fromInput(logger, input))

  def fromInput(logger: TypoLogger, input: Input): MetaDb = {
    val foreignKeys = ForeignKeys(input.tableConstraints, input.keyColumnUsage, input.referentialConstraints)
    val primaryKeys = PrimaryKeys(input.tableConstraints, input.keyColumnUsage)
    val uniqueKeys = UniqueKeys(input.tableConstraints, input.keyColumnUsage)
    val enums = Enums(input.pgEnums)

    val domains = input.domains.map { d =>
      val tpe = PgTypeMapperDb(enums, Nil).dbTypeFrom(d.`type`, characterMaximumLength = None /* todo: this can likely be set */ ) { () =>
        logger.warn(s"Couldn't translate type from domain $d")
      }

      db.Domain(
        name = db.RelationName(d.schema, d.name),
        tpe = tpe,
        originalType = d.`type`,
        isNotNull = if (d.isNotNull) Nullability.NoNulls else Nullability.Nullable,
        hasDefault = d.default.isDefined,
        constraintDefinition = d.constraintDefinition
      )
    }

    val constraints: Map[(db.RelationName, db.ColName), List[db.Constraint]] =
      input.constraints
        .collect { case ConstraintsSqlRow(tableSchema, Some(tableName), Some(columns), Some(constraintName), Some(checkClause)) =>
          columns.map(column =>
            (db.RelationName(tableSchema, tableName), db.ColName(column)) -> db.Constraint(constraintName, SortedSet.empty[db.ColName] ++ columns.iterator.map(db.ColName.apply), checkClause)
          )
        }
        .flatten
        .groupBy { case (k, _) => k }
        .map { case (k, rows) => (k, rows.map { case (_, c) => c }.sortBy(_.name)) }

    val typeMapperDb = PgTypeMapperDb(enums, domains)

    val comments: Map[(db.RelationName, db.ColName), String] =
      input.columnComments.collect { case CommentsSqlRow(maybeSchema, Some(table), Some(column), description) =>
        (db.RelationName(maybeSchema, table), db.ColName(column)) -> description
      }.toMap

    val columnsByTable: Map[db.RelationName, List[ColumnsViewRow]] =
      input.columns.groupBy(c => db.RelationName(c.tableSchema, c.tableName.get))
    val tableCommentsByTable: Map[db.RelationName, String] =
      input.tableComments.flatMap(c => c.description.map(d => (db.RelationName(c.schema, c.name), d))).toMap

    // Helper to parse a table name into a RelationName
    // sqlglot now returns fully qualified names like "schema.table"
    def parseTableName(tableName: String): db.RelationName = {
      if (tableName.contains(".")) {
        val parts = tableName.split("\\.", 2)
        db.RelationName(Some(parts(0)), parts(1))
      } else {
        db.RelationName(None, tableName)
      }
    }

    // Build a set of known relation names (tables + views) for filtering column sources
    // This is used to filter out CTE aliases and subquery aliases
    val knownRelationNames: Set[db.RelationName] = columnsByTable.keySet ++ input.views.keySet

    // Helper to check if a relation name exists (filters out CTE aliases and subquery aliases)
    def relationExists(relName: db.RelationName): Boolean = {
      knownRelationNames.contains(relName)
    }

    val views: Map[db.RelationName, Lazy[db.View]] =
      input.views.map { case (relationName, AnalyzedView(viewRow, decomposedSql, sqlglotResult)) =>
        val lazyAnalysis = Lazy {
          // Build deps from sqlglot column lineage
          val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
            sqlglotResult.columns.flatMap { col =>
              val colName = db.ColName(col.alias.getOrElse(col.name))

              // Only include direct column references, not expressions like xpath() calls
              // When isExpression is true, the column is computed (function, expression, etc.)
              // and shouldn't generate "Points to" comments
              if (!col.isExpression) {
                // Filter out empty table/column names and subquery aliases
                col.sourceTable.filter(_.nonEmpty).zip(col.sourceColumn.filter(_.nonEmpty)).flatMap { case (table, column) =>
                  // Parse the fully qualified table name from sqlglot
                  val parsedName = parseTableName(table)

                  // Only include if the table actually exists (not a subquery alias like "granular")
                  if (relationExists(parsedName)) {
                    // sqlglot should now provide fully qualified names
                    Some(colName -> List((parsedName, db.ColName(column))))
                  } else {
                    None
                  }
                }
              } else {
                None
              }
            }.toMap

          // Build columns from sqlglot results
          val cols: NonEmptyList[(db.Col, ParsedName)] = {
            val colList = sqlglotResult.columns.map { col =>
              val colName = col.alias.getOrElse(col.name)
              val parsedName = ParsedName.of(colName)

              // Determine nullability
              // For system views (information_schema, pg_catalog), use PostgreSQL metadata
              // since sqlglot often gets nullability wrong for these
              val nullability: Nullability =
                parsedName.nullability.getOrElse {
                  // For information_schema and pg_catalog, prefer PostgreSQL metadata over sqlglot
                  val isSystemView = relationName.schema.exists(s => s == "information_schema" || s == "pg_catalog")
                  if (isSystemView) {
                    columnsByTable
                      .get(relationName)
                      .flatMap { viewCols =>
                        viewCols.find(_.columnName.contains(colName)).flatMap { dbCol =>
                          dbCol.isNullable.map {
                            case "YES" => Nullability.Nullable
                            case "NO"  => Nullability.NoNulls
                            case _     => Nullability.NullableUnknown
                          }
                        }
                      }
                      .getOrElse {
                        // Fallback to sqlglot if no PostgreSQL metadata
                        if (col.nullableFromJoin || col.nullableInSchema) Nullability.Nullable
                        else if (col.isExpression && col.sourceColumn.isEmpty) Nullability.NullableUnknown
                        else Nullability.NoNulls
                      }
                  } else {
                    // For user views, use sqlglot as before
                    if (col.nullableFromJoin || col.nullableInSchema) Nullability.Nullable
                    else if (col.isExpression && col.sourceColumn.isEmpty) Nullability.NullableUnknown
                    else Nullability.NoNulls
                  }
                }

              // Hybrid type resolution strategy:
              // 1. Try PostgreSQL metadata first (authoritative for views)
              // 2. Fall back to sqlglot's inferredType for computed expressions
              // 3. Otherwise use sourceType or text
              // This approach matches SQL files - use authoritative PostgreSQL types, enriched with sqlglot nullability/lineage
              val dbType = {
                // First, try PostgreSQL metadata (authoritative for all view columns)
                val dbMetadataType = columnsByTable.get(relationName).flatMap { viewCols =>
                  viewCols.find(_.columnName.contains(colName)).flatMap { dbCol =>
                    dbCol.udtName.map { udtName =>
                      typeMapperDb.dbTypeFrom(udtName, dbCol.characterMaximumLength) { () =>
                        logger.warn(s"Couldn't translate database type from view ${relationName.value} column $colName with udtName $udtName")
                      }
                    }
                  }
                }

                dbMetadataType.getOrElse {
                  // No PostgreSQL metadata - fall back to sqlglot inferred type
                  col.inferredType match {
                    case Some(inferredType) if inferredType.toUpperCase != "UNKNOWN" =>
                      typeMapperDb.dbTypeFromSqlglot(inferredType, None) { () =>
                        logger.warn(s"Couldn't translate inferred type from view ${relationName.value} column $colName with type $inferredType. Falling back to text")
                      }
                    case _ =>
                      // No inferred type - try sourceType
                      col.sourceType match {
                        case Some(sourceType) =>
                          typeMapperDb.dbTypeFrom(sourceType, None) { () =>
                            logger.warn(s"Couldn't translate source type from view ${relationName.value} column $colName with type $sourceType. Falling back to text")
                          }
                        case None =>
                          // No type information available, default to text
                          logger.warn(s"No type information available for view ${relationName.value} column $colName. Falling back to text")
                          typeMapperDb.dbTypeFromSqlglot("TEXT", None)(() => ())
                      }
                  }
                }
              }

              val coord = (relationName, db.ColName(colName))
              // Don't set udtName for views - it's only needed for custom types and the
              // sourceType/inferredType contains builtin type names which would cause
              // SqlCast.toPg to emit unnecessary casts like ::int4, ::timestamp
              val dbCol = db.Col(
                parsedName = parsedName,
                tpe = dbType,
                udtName = None,
                columnDefault = None,
                maybeGenerated = None,
                comment = comments.get(coord),
                jsonDescription = DebugJson(col),
                nullability = nullability,
                constraints = constraints.getOrElse(coord, Nil)
              )
              (dbCol, parsedName)
            }
            NonEmptyList.fromList(colList).getOrElse {
              // Fallback: For system views (like information_schema) where sqlglot can't analyze the SQL,
              // use PostgreSQL metadata directly
              columnsByTable.get(relationName) match {
                case Some(dbCols) if dbCols.nonEmpty =>
                  logger.warn(s"View ${relationName.value} has no columns from sqlglot analysis, falling back to PostgreSQL metadata")
                  NonEmptyList
                    .fromList(dbCols.toList.map { dbCol =>
                      val colName = dbCol.columnName.getOrElse(sys.error(s"Column without name in ${relationName.value}"))
                      val parsedName = ParsedName.of(colName)
                      val dbType = dbCol.udtName match {
                        case Some(udtName) =>
                          typeMapperDb.dbTypeFrom(udtName, dbCol.characterMaximumLength) { () =>
                            logger.warn(s"Couldn't translate database type from view ${relationName.value} column $colName with udtName $udtName")
                          }
                        case None =>
                          logger.warn(s"No type information available for view ${relationName.value} column $colName. Falling back to text")
                          typeMapperDb.dbTypeFromSqlglot("TEXT", None)(() => ())
                      }
                      // Use PostgreSQL's is_nullable field to determine nullability
                      val nullability = parsedName.nullability.getOrElse {
                        dbCol.isNullable match {
                          case Some("YES") => Nullability.Nullable
                          case Some("NO")  => Nullability.NoNulls
                          case _           => Nullability.NullableUnknown
                        }
                      }
                      val coord = (relationName, db.ColName(colName))
                      val col = db.Col(
                        parsedName = parsedName,
                        tpe = dbType,
                        udtName = None,
                        columnDefault = None,
                        maybeGenerated = None,
                        comment = comments.get(coord),
                        jsonDescription = DebugJson(dbCol),
                        nullability = nullability,
                        constraints = constraints.getOrElse(coord, Nil)
                      )
                      (col, parsedName)
                    })
                    .getOrElse(sys.error(s"View ${relationName.value} has no columns"))
                case _ =>
                  sys.error(s"View ${relationName.value} has no columns from sqlglot analysis or PostgreSQL metadata")
              }
            }
          }

          db.View(relationName, tableCommentsByTable.get(relationName), decomposedSql, cols, deps, isMaterialized = viewRow.relkind == "m")
        }
        (relationName, lazyAnalysis)
      }

    val tables: Map[db.RelationName, Lazy[db.Table]] =
      input.tables.flatMap { relation =>
        val relationName = db.RelationName(schema = relation.tableSchema, name = relation.tableName.get)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)) map { columns =>
          val lazyAnalysis = Lazy {
            val fks: List[db.ForeignKey] = foreignKeys.getOrElse(relationName, List.empty)

            val deps: Map[db.ColName, (db.RelationName, db.ColName)] =
              fks.flatMap { fk =>
                val otherTable: db.RelationName = fk.otherTable
                val value = fk.otherCols.map(cn => (otherTable, cn))
                fk.cols.zip(value).toList
              }.toMap

            val mappedCols: NonEmptyList[db.Col] =
              columns.map { c =>
                val jsonDescription = DebugJson(c)

                val parsedName = ParsedName.of(c.columnName.get)
                val nullability =
                  c.isNullable match {
                    case Some("YES") => Nullability.Nullable
                    case Some("NO")  => Nullability.NoNulls
                    case None        => Nullability.NullableUnknown
                    case other       => throw new Exception(s"Unknown nullability: $other")
                  }

                val tpe = typeMapperDb.col(c) { () =>
                  logger.warn(s"Couldn't translate type from table ${relationName.value} column ${parsedName.name.value} with type ${c.udtName}. Falling back to text")
                }

                val generated = c.identityGeneration
                  .map { value =>
                    db.Generated.Identity(
                      identityGeneration = value,
                      identityStart = c.identityStart,
                      identityIncrement = c.identityIncrement,
                      identityMaximum = c.identityMaximum,
                      identityMinimum = c.identityMinimum
                    )
                  }
                  .orElse(c.isGenerated.flatMap {
                    case "NEVER"   => None
                    case generated => Some(db.Generated.IsGenerated(generated, c.generationExpression))
                  })

                val coord = (relationName, parsedName.name)
                db.Col(
                  parsedName = parsedName,
                  tpe = tpe,
                  udtName = c.udtName,
                  nullability = nullability,
                  columnDefault = c.columnDefault,
                  maybeGenerated = generated,
                  comment = comments.get(coord),
                  constraints = constraints.getOrElse(coord, Nil) ++ deps.get(parsedName.name).flatMap(otherCoord => constraints.get(otherCoord)).getOrElse(Nil),
                  jsonDescription = jsonDescription
                )
              }

            db.Table(
              name = relationName,
              comment = tableCommentsByTable.get(relationName),
              cols = mappedCols,
              primaryKey = primaryKeys.get(relationName),
              uniqueKeys = uniqueKeys.getOrElse(relationName, List.empty),
              foreignKeys = fks
            )
          }

          (relationName, lazyAnalysis)
        }
      }.toMap

    MetaDb(DbType.PostgreSQL, tables ++ views, enums, domains)
  }
}
