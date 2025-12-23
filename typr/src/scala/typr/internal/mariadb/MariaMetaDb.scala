package typr
package internal
package mariadb

import anorm.Column
import anorm.RowParser
import anorm.SqlStringInterpolation
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** MariaDB-specific metadata extraction using Anorm */
object MariaMetaDb {

  case class MariaColumn(
      tableSchema: Option[String],
      tableName: String,
      columnName: String,
      ordinalPosition: Int,
      columnDefault: Option[String],
      isNullable: String,
      dataType: String,
      characterMaximumLength: Option[Long],
      numericPrecision: Option[Long],
      numericScale: Option[Long],
      datetimePrecision: Option[Long],
      columnType: String,
      columnKey: String,
      extra: String,
      columnComment: Option[String]
  )

  object MariaColumn {
    def rowParser(idx: Int): RowParser[MariaColumn] = RowParser[MariaColumn] { row =>
      Success(
        MariaColumn(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          columnName = row(idx + 2)(Column.columnToString),
          ordinalPosition = row(idx + 3)(Column.columnToInt),
          columnDefault = row(idx + 4)(Column.columnToOption(Column.columnToString)),
          isNullable = row(idx + 5)(Column.columnToString),
          dataType = row(idx + 6)(Column.columnToString),
          characterMaximumLength = row(idx + 7)(Column.columnToOption(Column.columnToLong)),
          numericPrecision = row(idx + 8)(Column.columnToOption(Column.columnToLong)),
          numericScale = row(idx + 9)(Column.columnToOption(Column.columnToLong)),
          datetimePrecision = row(idx + 10)(Column.columnToOption(Column.columnToLong)),
          columnType = row(idx + 11)(Column.columnToString),
          columnKey = row(idx + 12)(Column.columnToString),
          extra = row(idx + 13)(Column.columnToString),
          columnComment = row(idx + 14)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class MariaTable(
      tableSchema: Option[String],
      tableName: String,
      tableType: String,
      tableComment: Option[String]
  )

  object MariaTable {
    def rowParser(idx: Int): RowParser[MariaTable] = RowParser[MariaTable] { row =>
      Success(
        MariaTable(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          tableType = row(idx + 2)(Column.columnToString),
          tableComment = row(idx + 3)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class MariaConstraint(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      constraintType: String
  )

  object MariaConstraint {
    def rowParser(idx: Int): RowParser[MariaConstraint] = RowParser[MariaConstraint] { row =>
      Success(
        MariaConstraint(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableSchema = row(idx + 2)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 3)(Column.columnToString),
          constraintType = row(idx + 4)(Column.columnToString)
        )
      )
    }
  }

  case class MariaKeyColumn(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      columnName: String,
      ordinalPosition: Int,
      referencedTableSchema: Option[String],
      referencedTableName: Option[String],
      referencedColumnName: Option[String]
  )

  object MariaKeyColumn {
    def rowParser(idx: Int): RowParser[MariaKeyColumn] = RowParser[MariaKeyColumn] { row =>
      Success(
        MariaKeyColumn(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableSchema = row(idx + 2)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 3)(Column.columnToString),
          columnName = row(idx + 4)(Column.columnToString),
          ordinalPosition = row(idx + 5)(Column.columnToInt),
          referencedTableSchema = row(idx + 6)(Column.columnToOption(Column.columnToString)),
          referencedTableName = row(idx + 7)(Column.columnToOption(Column.columnToString)),
          referencedColumnName = row(idx + 8)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** View definition from INFORMATION_SCHEMA.VIEWS */
  case class MariaView(
      tableSchema: Option[String],
      tableName: String,
      viewDefinition: Option[String]
  )

  object MariaView {
    def rowParser(idx: Int): RowParser[MariaView] = RowParser[MariaView] { row =>
      Success(
        MariaView(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          viewDefinition = row(idx + 2)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Analyzed view with JDBC metadata for dependency tracking */
  case class AnalyzedView(
      row: MariaView,
      decomposedSql: DecomposedSql,
      jdbcMetadata: MariaJdbcMetadata
  )

  case class Input(
      tables: List[MariaTable],
      columns: List[MariaColumn],
      constraints: List[MariaConstraint],
      keyColumns: List[MariaKeyColumn],
      analyzedViews: Map[db.RelationName, AnalyzedView]
  ) {
    def filter(schemaMode: SchemaMode): Input = {
      schemaMode match {
        case SchemaMode.MultiSchema => this
        case SchemaMode.SingleSchema(wantedSchema) =>
          def keep(os: Option[String]): Boolean = os.contains(wantedSchema)

          Input(
            tables = tables.collect { case t if keep(t.tableSchema) => t.copy(tableSchema = None) },
            columns = columns.collect { case c if keep(c.tableSchema) => c.copy(tableSchema = None) },
            constraints = constraints.collect { case c if keep(c.tableSchema) || keep(c.constraintSchema) => c.copy(tableSchema = None, constraintSchema = None) },
            keyColumns = keyColumns.collect {
              case k if keep(k.tableSchema) || keep(k.constraintSchema) =>
                k.copy(tableSchema = None, constraintSchema = None, referencedTableSchema = if (keep(k.referencedTableSchema)) None else k.referencedTableSchema)
            },
            analyzedViews = analyzedViews.collect {
              case (k, v) if keep(k.schema) =>
                k.copy(schema = None) -> v.copy(row = v.row.copy(tableSchema = None))
            }
          )
      }
    }
  }

  object Input {
    def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[Input] = {
      for {
        tables <- logger.timed("fetching tables")(ds.run(implicit c => fetchTables(c)))
        columns <- logger.timed("fetching columns")(ds.run(implicit c => fetchColumns(c)))
        constraints <- logger.timed("fetching constraints")(ds.run(implicit c => fetchConstraints(c)))
        keyColumns <- logger.timed("fetching keyColumns")(ds.run(implicit c => fetchKeyColumns(c)))
        analyzedViews <- logger.timed("fetching and analyzing views")(analyzeViews(ds, viewSelector))
      } yield {
        val input = Input(tables, columns, constraints, keyColumns, analyzedViews)
        input.filter(schemaMode)
      }
    }

    private def analyzeViews(ds: TypoDataSource, viewSelector: Selector)(implicit ec: ExecutionContext): Future[Map[db.RelationName, AnalyzedView]] = {
      ds.run { implicit c =>
        val viewRows = fetchViews(c)
        viewRows.flatMap { viewRow =>
          val name = db.RelationName(viewRow.tableSchema, viewRow.tableName)
          if (viewRow.viewDefinition.isDefined && viewSelector.include(name)) {
            val sqlContent = viewRow.viewDefinition.get
            val decomposedSql = DecomposedSql.parse(sqlContent)
            // Use JDBC metadata to get base table/column info for dependency tracking
            MariaJdbcMetadata.from(sqlContent) match {
              case Right(jdbcMetadata) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata))
              case Left(_) =>
                // If we can't analyze the view SQL, create an empty analysis
                Some(name -> AnalyzedView(viewRow, decomposedSql, MariaJdbcMetadata(MaybeReturnsRows.Update)))
            }
          } else None
        }.toMap
      }
    }

    private def fetchViews(implicit conn: Connection): List[MariaView] = {
      val sql = SQL"""SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION
                      FROM INFORMATION_SCHEMA.VIEWS
                      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME"""
      sql.as(MariaView.rowParser(1).*)
    }

    private def fetchTables(implicit conn: Connection): List[MariaTable] = {
      val sql = SQL"""SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT
                      FROM INFORMATION_SCHEMA.TABLES
                      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME"""
      sql.as(MariaTable.rowParser(1).*)
    }

    private def fetchColumns(implicit conn: Connection): List[MariaColumn] = {
      val sql = SQL"""SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION,
                             COLUMN_DEFAULT, IS_NULLABLE, DATA_TYPE,
                             CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE,
                             DATETIME_PRECISION, COLUMN_TYPE, COLUMN_KEY, EXTRA, COLUMN_COMMENT
                      FROM INFORMATION_SCHEMA.COLUMNS
                      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION"""
      sql.as(MariaColumn.rowParser(1).*)
    }

    private def fetchConstraints(implicit conn: Connection): List[MariaConstraint] = {
      val sql = SQL"""SELECT CONSTRAINT_SCHEMA, CONSTRAINT_NAME, TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_TYPE
                      FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME"""
      sql.as(MariaConstraint.rowParser(1).*)
    }

    private def fetchKeyColumns(implicit conn: Connection): List[MariaKeyColumn] = {
      val sql = SQL"""SELECT CONSTRAINT_SCHEMA, CONSTRAINT_NAME, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME,
                             ORDINAL_POSITION, REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                      FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                      WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION"""
      sql.as(MariaKeyColumn.rowParser(1).*)
    }
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    Input.fromDb(logger, ds, viewSelector, schemaMode).map(input => fromInput(input))
  }

  def fromInput(input: Input): MetaDb = {
    val typeMapper = MariaTypeMapperDb()

    // Build primary keys
    val primaryKeys: Map[db.RelationName, db.PrimaryKey] = {
      val pkConstraints = input.constraints.filter(_.constraintType == "PRIMARY KEY")
      val pkKeyColumns = input.keyColumns.filter(kc => pkConstraints.exists(c => c.constraintName == kc.constraintName && c.tableSchema == kc.tableSchema && c.tableName == kc.tableName))

      pkConstraints.flatMap { c =>
        val cols = pkKeyColumns
          .filter(kc => kc.constraintName == c.constraintName && kc.tableSchema == c.tableSchema && kc.tableName == c.tableName)
          .sortBy(_.ordinalPosition)
          .map(kc => db.ColName(kc.columnName))

        NonEmptyList.fromList(cols).map { nelCols =>
          val name = db.RelationName(c.tableSchema, c.tableName)
          val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
          name -> db.PrimaryKey(nelCols, constraintName)
        }
      }.toMap
    }

    // Build foreign keys
    val foreignKeys: Map[db.RelationName, List[db.ForeignKey]] = {
      val fkConstraints = input.constraints.filter(_.constraintType == "FOREIGN KEY")

      fkConstraints
        .flatMap { c =>
          val cols = input.keyColumns
            .filter(kc => kc.constraintName == c.constraintName && kc.tableSchema == c.tableSchema && kc.tableName == c.tableName)
            .sortBy(_.ordinalPosition)

          if (cols.nonEmpty && cols.forall(_.referencedTableName.isDefined)) {
            val fkCols = cols.map(kc => db.ColName(kc.columnName))
            val refCols = cols.map(kc => db.ColName(kc.referencedColumnName.get))
            val refTable = db.RelationName(cols.head.referencedTableSchema, cols.head.referencedTableName.get)

            NonEmptyList.fromList(fkCols).flatMap { nelCols =>
              NonEmptyList.fromList(refCols).map { nelRefCols =>
                val name = db.RelationName(c.tableSchema, c.tableName)
                val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
                name -> db.ForeignKey(nelCols, refTable, nelRefCols, constraintName)
              }
            }
          } else None
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Build unique keys
    val uniqueKeys: Map[db.RelationName, List[db.UniqueKey]] = {
      val ukConstraints = input.constraints.filter(_.constraintType == "UNIQUE")

      ukConstraints
        .flatMap { c =>
          val cols = input.keyColumns
            .filter(kc => kc.constraintName == c.constraintName && kc.tableSchema == c.tableSchema && kc.tableName == c.tableName)
            .sortBy(_.ordinalPosition)
            .map(kc => db.ColName(kc.columnName))

          NonEmptyList.fromList(cols).map { nelCols =>
            val name = db.RelationName(c.tableSchema, c.tableName)
            val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
            name -> db.UniqueKey(nelCols, constraintName)
          }
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Group columns by table
    val columnsByTable: Map[db.RelationName, List[MariaColumn]] =
      input.columns.groupBy(c => db.RelationName(c.tableSchema, c.tableName))

    // Build tables (excluding views)
    val baseTables = input.tables.filter(_.tableType == "BASE TABLE")

    val tables: Map[db.RelationName, Lazy[db.Table]] =
      baseTables.flatMap { table =>
        val relationName = db.RelationName(table.tableSchema, table.tableName)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)).map { columns =>
          val lazyAnalysis = Lazy {
            val mappedCols: NonEmptyList[db.Col] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.isNullable match {
                case "YES" => Nullability.Nullable
                case "NO"  => Nullability.NoNulls
                case _     => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                dataType = c.dataType,
                columnType = c.columnType,
                characterMaximumLength = c.characterMaximumLength,
                numericPrecision = c.numericPrecision,
                numericScale = c.numericScale,
                datetimePrecision = c.datetimePrecision
              )

              // Detect AUTO_INCREMENT
              val generated: Option[db.Generated] =
                if (c.extra.toLowerCase.contains("auto_increment")) Some(db.Generated.AutoIncrement)
                else None

              db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.columnType),
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = generated,
                comment = c.columnComment,
                constraints = Nil, // MariaDB doesn't have PostgreSQL-style check constraints easily accessible
                jsonDescription = DebugJson(Map("dataType" -> c.dataType, "columnType" -> c.columnType, "extra" -> c.extra))
              )
            }

            db.Table(
              name = relationName,
              comment = table.tableComment,
              cols = mappedCols,
              primaryKey = primaryKeys.get(relationName),
              uniqueKeys = uniqueKeys.getOrElse(relationName, Nil),
              foreignKeys = foreignKeys.getOrElse(relationName, Nil)
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // Build views from analyzed view data
    val viewTables = input.tables.filter(t => t.tableType == "VIEW" || t.tableType == "SYSTEM VIEW")

    val views: Map[db.RelationName, Lazy[db.View]] =
      viewTables.flatMap { view =>
        val relationName = db.RelationName(view.tableSchema, view.tableName)

        // Try to find analyzed view data (with JDBC metadata for dependencies)
        val maybeAnalyzed = input.analyzedViews.get(relationName)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)).map { columns =>
          val lazyAnalysis = Lazy {
            // Build deps from JDBC metadata if available
            val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
              maybeAnalyzed.map(_.jdbcMetadata.columns) match {
                case Some(MaybeReturnsRows.Query(metadataCols)) =>
                  metadataCols.toList.flatMap { col =>
                    col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))
                  }.toMap
                case _ =>
                  Map.empty
              }

            val decomposedSql = maybeAnalyzed.map(_.decomposedSql).getOrElse(DecomposedSql(Nil))

            val cols: NonEmptyList[(db.Col, ParsedName)] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.isNullable match {
                case "YES" => Nullability.Nullable
                case "NO"  => Nullability.NoNulls
                case _     => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                dataType = c.dataType,
                columnType = c.columnType,
                characterMaximumLength = c.characterMaximumLength,
                numericPrecision = c.numericPrecision,
                numericScale = c.numericScale,
                datetimePrecision = c.datetimePrecision
              )

              val col = db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.columnType),
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = None,
                comment = c.columnComment,
                constraints = Nil,
                jsonDescription = DebugJson(Map("dataType" -> c.dataType, "columnType" -> c.columnType))
              )

              (col, parsedName)
            }

            db.View(
              name = relationName,
              comment = view.tableComment,
              decomposedSql = decomposedSql,
              cols = cols,
              deps = deps,
              isMaterialized = false // MariaDB doesn't have materialized views
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // MariaDB doesn't have PostgreSQL-style enums or domains
    MetaDb(DbType.MariaDB, tables ++ views, enums = Nil, domains = Nil)
  }
}
