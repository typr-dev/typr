package typr
package internal
package duckdb

import anorm.Column
import anorm.RowParser
import anorm.SqlStringInterpolation
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** DuckDB-specific metadata extraction using Anorm */
object DuckDbMetaDb {

  case class DuckDbColumn(
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
      comment: Option[String]
  )

  object DuckDbColumn {
    def rowParser(idx: Int): RowParser[DuckDbColumn] = RowParser[DuckDbColumn] { row =>
      Success(
        DuckDbColumn(
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
          comment = row(idx + 10)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class DuckDbTable(
      tableSchema: Option[String],
      tableName: String,
      tableType: String,
      comment: Option[String]
  )

  object DuckDbTable {
    def rowParser(idx: Int): RowParser[DuckDbTable] = RowParser[DuckDbTable] { row =>
      Success(
        DuckDbTable(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          tableType = row(idx + 2)(Column.columnToString),
          comment = row(idx + 3)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class DuckDbConstraint(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      constraintType: String,
      constraintColumnNames: List[String]
  )

  object DuckDbConstraint {
    def rowParser(idx: Int): RowParser[DuckDbConstraint] = RowParser[DuckDbConstraint] { row =>
      val colNamesStr = row(idx + 5)(Column.columnToString)
      val colNames = colNamesStr.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty).toList

      Success(
        DuckDbConstraint(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableSchema = row(idx + 2)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 3)(Column.columnToString),
          constraintType = row(idx + 4)(Column.columnToString),
          constraintColumnNames = colNames
        )
      )
    }
  }

  case class DuckDbForeignKey(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      columnNames: List[String],
      referencedTableSchema: Option[String],
      referencedTableName: String,
      referencedColumnNames: List[String]
  )

  object DuckDbForeignKey {
    def rowParser(idx: Int): RowParser[DuckDbForeignKey] = RowParser[DuckDbForeignKey] { row =>
      def parseColList(str: String): List[String] =
        str.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty).toList

      Success(
        DuckDbForeignKey(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableSchema = row(idx + 2)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 3)(Column.columnToString),
          columnNames = parseColList(row(idx + 4)(Column.columnToString)),
          referencedTableSchema = row(idx + 5)(Column.columnToOption(Column.columnToString)),
          referencedTableName = row(idx + 6)(Column.columnToString),
          referencedColumnNames = parseColList(row(idx + 7)(Column.columnToString))
        )
      )
    }
  }

  case class DuckDbView(
      tableSchema: Option[String],
      tableName: String,
      viewDefinition: Option[String]
  )

  object DuckDbView {
    def rowParser(idx: Int): RowParser[DuckDbView] = RowParser[DuckDbView] { row =>
      Success(
        DuckDbView(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          viewDefinition = row(idx + 2)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class DuckDbEnum(
      enumSchema: Option[String],
      enumName: String,
      enumValues: List[String]
  )

  object DuckDbEnum {
    def rowParser(idx: Int): RowParser[DuckDbEnum] = RowParser[DuckDbEnum] { row =>
      val valuesStr = row(idx + 2)(Column.columnToString)
      val values = valuesStr.stripPrefix("[").stripSuffix("]").split(",").map(_.trim.stripPrefix("'").stripSuffix("'")).filter(_.nonEmpty).toList

      Success(
        DuckDbEnum(
          enumSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          enumName = row(idx + 1)(Column.columnToString),
          enumValues = values
        )
      )
    }
  }

  case class AnalyzedView(
      row: DuckDbView,
      decomposedSql: DecomposedSql,
      jdbcMetadata: DuckDbJdbcMetadata
  )

  case class Input(
      tables: List[DuckDbTable],
      columns: List[DuckDbColumn],
      constraints: List[DuckDbConstraint],
      foreignKeys: List[DuckDbForeignKey],
      enums: List[DuckDbEnum],
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
            foreignKeys = foreignKeys.collect {
              case fk if keep(fk.tableSchema) || keep(fk.constraintSchema) =>
                fk.copy(tableSchema = None, constraintSchema = None, referencedTableSchema = if (keep(fk.referencedTableSchema)) None else fk.referencedTableSchema)
            },
            enums = enums.collect { case e if keep(e.enumSchema) => e.copy(enumSchema = None) },
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
        foreignKeys <- logger.timed("fetching foreign keys")(ds.run(implicit c => fetchForeignKeys(c)))
        enums <- logger.timed("fetching enums")(ds.run(implicit c => fetchEnums(c)))
        analyzedViews <- logger.timed("fetching and analyzing views")(analyzeViews(ds, viewSelector))
      } yield {
        val input = Input(tables, columns, constraints, foreignKeys, enums, analyzedViews)
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
            DuckDbJdbcMetadata.from(sqlContent) match {
              case Right(jdbcMetadata) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata))
              case Left(_) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, DuckDbJdbcMetadata(MaybeReturnsRows.Update)))
            }
          } else None
        }.toMap
      }
    }

    private def fetchViews(implicit conn: Connection): List[DuckDbView] = {
      val sql = SQL"""SELECT schema_name, view_name, sql
                      FROM duckdb_views()
                      WHERE NOT internal
                      ORDER BY schema_name, view_name"""
      sql.as(DuckDbView.rowParser(1).*)
    }

    private def fetchTables(implicit conn: Connection): List[DuckDbTable] = {
      val sql = SQL"""SELECT schema_name, table_name,
                             CASE WHEN temporary THEN 'TEMPORARY TABLE' ELSE 'BASE TABLE' END as table_type,
                             NULL as comment
                      FROM duckdb_tables()
                      WHERE NOT internal
                      ORDER BY schema_name, table_name"""
      sql.as(DuckDbTable.rowParser(1).*)
    }

    private def fetchColumns(implicit conn: Connection): List[DuckDbColumn] = {
      val sql = SQL"""SELECT schema_name, table_name, column_name, column_index + 1 as ordinal_position,
                             column_default, CASE WHEN is_nullable THEN 'YES' ELSE 'NO' END as is_nullable,
                             data_type, character_maximum_length, numeric_precision, numeric_scale,
                             comment
                      FROM duckdb_columns()
                      WHERE NOT internal
                      ORDER BY schema_name, table_name, column_index"""
      sql.as(DuckDbColumn.rowParser(1).*)
    }

    private def fetchConstraints(implicit conn: Connection): List[DuckDbConstraint] = {
      // Cast constraint_column_names array to VARCHAR for Anorm compatibility
      val sql = SQL"""SELECT database_name as constraint_schema, constraint_text as constraint_name,
                             schema_name as table_schema, table_name,
                             constraint_type, CAST(constraint_column_names AS VARCHAR) as constraint_column_names
                      FROM duckdb_constraints()
                      WHERE constraint_type IN ('PRIMARY KEY', 'UNIQUE')
                      ORDER BY schema_name, table_name, constraint_text"""
      sql.as(DuckDbConstraint.rowParser(1).*)
    }

    private def fetchForeignKeys(implicit conn: Connection): List[DuckDbForeignKey] = {
      // Cast constraint_column_names array to VARCHAR for Anorm compatibility
      val sql = SQL"""SELECT database_name as constraint_schema, constraint_text as constraint_name,
                             schema_name as table_schema, table_name, CAST(constraint_column_names AS VARCHAR) as constraint_column_names,
                             NULL as referenced_table_schema,
                             '' as referenced_table_name, '[]' as referenced_column_names
                      FROM duckdb_constraints()
                      WHERE constraint_type = 'FOREIGN KEY'
                      ORDER BY schema_name, table_name, constraint_text"""
      // Note: DuckDB's duckdb_constraints() doesn't directly expose referenced table info
      // We'd need to parse constraint_text or use a different approach
      sql.as(DuckDbForeignKey.rowParser(1).*)
    }

    private def fetchEnums(implicit conn: Connection): List[DuckDbEnum] = {
      // DuckDB stores enum values in 'labels' column, not 'tags'
      // Also logical_type = 'ENUM', not type_category
      // Cast labels array to VARCHAR for Anorm compatibility
      val sql = SQL"""SELECT schema_name, type_name, CAST(labels AS VARCHAR) as labels
                      FROM duckdb_types()
                      WHERE logical_type = 'ENUM' AND NOT internal
                      ORDER BY schema_name, type_name"""
      sql.as(DuckDbEnum.rowParser(1).*)
    }
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    Input.fromDb(logger, ds, viewSelector, schemaMode).map(input => fromInput(input))
  }

  def fromInput(input: Input): MetaDb = {
    val typeMapper = DuckDbTypeMapperDb()

    // Build enums
    val enums: List[db.StringEnum] = input.enums.flatMap { e =>
      NonEmptyList.fromList(e.enumValues).map { values =>
        db.StringEnum(db.RelationName(e.enumSchema, e.enumName), values)
      }
    }

    // Build a lookup map from enum values to enum name for resolving anonymous enums
    // DuckDB reports column types as ENUM('v1', 'v2', ...) without the name,
    // so we need to match by values to find the actual enum definition
    val enumByValues: Map[List[String], (Option[String], String)] =
      input.enums.map(e => e.enumValues -> (e.enumSchema, e.enumName)).toMap

    // Helper to resolve enum type names from enum definitions
    def resolveEnumType(tpe: db.Type): db.Type = tpe match {
      case e @ db.DuckDbType.Enum("", values) =>
        enumByValues.get(values) match {
          case Some((schema, name)) => e.copy(name = schema.map(_ + ".").getOrElse("") + name)
          case None                 => e.copy(name = values.mkString("_")) // Fallback if not found
        }
      case other => other
    }

    // Build primary keys
    val primaryKeys: Map[db.RelationName, db.PrimaryKey] = {
      input.constraints
        .filter(_.constraintType == "PRIMARY KEY")
        .flatMap { c =>
          NonEmptyList.fromList(c.constraintColumnNames.map(db.ColName.apply)).map { nelCols =>
            val name = db.RelationName(c.tableSchema, c.tableName)
            val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
            name -> db.PrimaryKey(nelCols, constraintName)
          }
        }
        .toMap
    }

    // Build unique keys
    val uniqueKeys: Map[db.RelationName, List[db.UniqueKey]] = {
      input.constraints
        .filter(_.constraintType == "UNIQUE")
        .flatMap { c =>
          NonEmptyList.fromList(c.constraintColumnNames.map(db.ColName.apply)).map { nelCols =>
            val name = db.RelationName(c.tableSchema, c.tableName)
            val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
            name -> db.UniqueKey(nelCols, constraintName)
          }
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Build foreign keys
    val foreignKeys: Map[db.RelationName, List[db.ForeignKey]] = {
      input.foreignKeys
        .flatMap { fk =>
          if (fk.referencedTableName.nonEmpty && fk.columnNames.nonEmpty && fk.referencedColumnNames.nonEmpty) {
            val fkCols = NonEmptyList.fromList(fk.columnNames.map(db.ColName.apply))
            val refCols = NonEmptyList.fromList(fk.referencedColumnNames.map(db.ColName.apply))
            val refTable = db.RelationName(fk.referencedTableSchema, fk.referencedTableName)

            for {
              nelCols <- fkCols
              nelRefCols <- refCols
            } yield {
              val name = db.RelationName(fk.tableSchema, fk.tableName)
              val constraintName = db.RelationName(fk.constraintSchema, fk.constraintName)
              name -> db.ForeignKey(nelCols, refTable, nelRefCols, constraintName)
            }
          } else None
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Group columns by table
    val columnsByTable: Map[db.RelationName, List[DuckDbColumn]] =
      input.columns.groupBy(c => db.RelationName(c.tableSchema, c.tableName))

    // Build tables
    val tables: Map[db.RelationName, Lazy[db.Table]] =
      input.tables
        .filter(_.tableType == "BASE TABLE")
        .flatMap { table =>
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

                val parsedType = typeMapper.parseType(c.dataType, c.characterMaximumLength.map(_.toInt)) { () => () }
                val tpe = resolveEnumType(parsedType)

                db.Col(
                  parsedName = parsedName,
                  tpe = tpe,
                  udtName = Some(c.dataType),
                  nullability = nullability,
                  columnDefault = c.columnDefault,
                  maybeGenerated = None,
                  comment = c.comment,
                  constraints = Nil,
                  jsonDescription = DebugJson(Map("dataType" -> c.dataType))
                )
              }

              db.Table(
                name = relationName,
                comment = table.comment,
                cols = mappedCols,
                primaryKey = primaryKeys.get(relationName),
                uniqueKeys = uniqueKeys.getOrElse(relationName, Nil),
                foreignKeys = foreignKeys.getOrElse(relationName, Nil)
              )
            }

            relationName -> lazyAnalysis
          }
        }
        .toMap

    // Build views
    val views: Map[db.RelationName, Lazy[db.View]] =
      input.analyzedViews.flatMap { case (relationName, analyzed) =>
        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)).map { columns =>
          val lazyAnalysis = Lazy {
            val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
              analyzed.jdbcMetadata.columns match {
                case MaybeReturnsRows.Query(metadataCols) =>
                  metadataCols.toList.flatMap { col =>
                    col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))
                  }.toMap
                case MaybeReturnsRows.Update =>
                  Map.empty
              }

            val cols: NonEmptyList[(db.Col, ParsedName)] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.isNullable match {
                case "YES" => Nullability.Nullable
                case "NO"  => Nullability.NoNulls
                case _     => Nullability.NullableUnknown
              }

              val parsedType = typeMapper.parseType(c.dataType, c.characterMaximumLength.map(_.toInt)) { () => () }
              val tpe = resolveEnumType(parsedType)

              val col = db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.dataType),
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = None,
                comment = c.comment,
                constraints = Nil,
                jsonDescription = DebugJson(Map("dataType" -> c.dataType))
              )

              (col, parsedName)
            }

            db.View(
              name = relationName,
              comment = None,
              decomposedSql = analyzed.decomposedSql,
              cols = cols,
              deps = deps,
              isMaterialized = false
            )
          }

          relationName -> lazyAnalysis
        }
      }

    MetaDb(DbType.DuckDB, tables ++ views, enums, domains = Nil)
  }
}
