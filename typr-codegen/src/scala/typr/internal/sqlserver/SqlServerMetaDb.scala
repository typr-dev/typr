package typr
package internal
package sqlserver

import anorm.Column
import anorm.RowParser
import anorm.SqlStringInterpolation
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** SQL Server-specific metadata extraction using Anorm */
object SqlServerMetaDb {

  case class SqlServerColumn(
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
      isIdentity: Boolean,
      identitySeed: Option[Long],
      identityIncrement: Option[Long],
      udtSchema: Option[String],
      udtName: Option[String]
  )

  object SqlServerColumn {
    def rowParser(idx: Int): RowParser[SqlServerColumn] = RowParser[SqlServerColumn] { row =>
      Success(
        SqlServerColumn(
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
          isIdentity = row(idx + 11)(Column.columnToBoolean),
          identitySeed = row(idx + 12)(Column.columnToOption(Column.columnToLong)),
          identityIncrement = row(idx + 13)(Column.columnToOption(Column.columnToLong)),
          udtSchema = row(idx + 14)(Column.columnToOption(Column.columnToString)),
          udtName = row(idx + 15)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class SqlServerTable(
      tableSchema: Option[String],
      tableName: String,
      tableType: String
  )

  object SqlServerTable {
    def rowParser(idx: Int): RowParser[SqlServerTable] = RowParser[SqlServerTable] { row =>
      Success(
        SqlServerTable(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          tableType = row(idx + 2)(Column.columnToString)
        )
      )
    }
  }

  case class SqlServerConstraint(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      constraintType: String
  )

  object SqlServerConstraint {
    def rowParser(idx: Int): RowParser[SqlServerConstraint] = RowParser[SqlServerConstraint] { row =>
      Success(
        SqlServerConstraint(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableSchema = row(idx + 2)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 3)(Column.columnToString),
          constraintType = row(idx + 4)(Column.columnToString)
        )
      )
    }
  }

  /** CHECK constraint with its definition clause */
  case class SqlServerCheckConstraint(
      constraintSchema: Option[String],
      constraintName: String,
      checkClause: String
  )

  object SqlServerCheckConstraint {
    def rowParser(idx: Int): RowParser[SqlServerCheckConstraint] = RowParser[SqlServerCheckConstraint] { row =>
      Success(
        SqlServerCheckConstraint(
          constraintSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          checkClause = row(idx + 2)(Column.columnToString)
        )
      )
    }
  }

  case class SqlServerKeyColumn(
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

  object SqlServerKeyColumn {
    def rowParser(idx: Int): RowParser[SqlServerKeyColumn] = RowParser[SqlServerKeyColumn] { row =>
      Success(
        SqlServerKeyColumn(
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
  case class SqlServerView(
      tableSchema: Option[String],
      tableName: String,
      viewDefinition: Option[String]
  )

  object SqlServerView {
    def rowParser(idx: Int): RowParser[SqlServerView] = RowParser[SqlServerView] { row =>
      Success(
        SqlServerView(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          viewDefinition = row(idx + 2)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Alias type definition from sys.types (user-defined types based on system types) */
  case class SqlServerAliasType(
      schemaName: String,
      typeName: String,
      baseTypeName: String,
      maxLength: Option[Long],
      precision: Option[Long],
      scale: Option[Long],
      isNullable: Boolean
  )

  object SqlServerAliasType {
    def rowParser(idx: Int): RowParser[SqlServerAliasType] = RowParser[SqlServerAliasType] { row =>
      Success(
        SqlServerAliasType(
          schemaName = row(idx + 0)(Column.columnToString),
          typeName = row(idx + 1)(Column.columnToString),
          baseTypeName = row(idx + 2)(Column.columnToString),
          maxLength = row(idx + 3)(Column.columnToOption(Column.columnToLong)),
          precision = row(idx + 4)(Column.columnToOption(Column.columnToLong)),
          scale = row(idx + 5)(Column.columnToOption(Column.columnToLong)),
          isNullable = row(idx + 6)(Column.columnToBoolean)
        )
      )
    }
  }

  /** CLR type definition from sys.assembly_types */
  case class SqlServerClrType(
      schemaName: String,
      typeName: String,
      assemblyName: String,
      assemblyClass: String
  )

  object SqlServerClrType {
    def rowParser(idx: Int): RowParser[SqlServerClrType] = RowParser[SqlServerClrType] { row =>
      Success(
        SqlServerClrType(
          schemaName = row(idx + 0)(Column.columnToString),
          typeName = row(idx + 1)(Column.columnToString),
          assemblyName = row(idx + 2)(Column.columnToString),
          assemblyClass = row(idx + 3)(Column.columnToString)
        )
      )
    }
  }

  /** Analyzed view with JDBC metadata for dependency tracking */
  case class AnalyzedView(
      row: SqlServerView,
      decomposedSql: DecomposedSql,
      jdbcMetadata: SqlServerJdbcMetadata
  )

  case class Input(
      tables: List[SqlServerTable],
      columns: List[SqlServerColumn],
      constraints: List[SqlServerConstraint],
      keyColumns: List[SqlServerKeyColumn],
      checkConstraints: List[SqlServerCheckConstraint],
      analyzedViews: Map[db.RelationName, AnalyzedView],
      aliasTypes: List[SqlServerAliasType],
      clrTypes: List[SqlServerClrType]
  ) {

    /** Check if a column has an ISJSON constraint */
    def hasIsJsonConstraint(tableSchema: Option[String], tableName: String, columnName: String): Boolean = {
      // Find CHECK constraints that reference this column with ISJSON
      val tableConstraints = constraints.filter { c =>
        c.constraintType == "CHECK" && c.tableSchema == tableSchema && c.tableName == tableName
      }
      tableConstraints.exists { c =>
        checkConstraints.find(_.constraintName == c.constraintName).exists { cc =>
          val clause = cc.checkClause.toLowerCase
          clause.contains("isjson") && clause.contains(columnName.toLowerCase)
        }
      }
    }

    def filter(schemaMode: SchemaMode): Input = {
      schemaMode match {
        case SchemaMode.MultiSchema => this
        case SchemaMode.SingleSchema(wantedSchema) =>
          def keep(os: Option[String]): Boolean = os.contains(wantedSchema)
          def keepString(s: String): Boolean = s == wantedSchema

          Input(
            tables = tables.collect { case t if keep(t.tableSchema) => t.copy(tableSchema = None) },
            columns = columns.collect { case c if keep(c.tableSchema) => c.copy(tableSchema = None) },
            constraints = constraints.collect { case c if keep(c.tableSchema) || keep(c.constraintSchema) => c.copy(tableSchema = None, constraintSchema = None) },
            keyColumns = keyColumns.collect {
              case k if keep(k.tableSchema) || keep(k.constraintSchema) =>
                k.copy(tableSchema = None, constraintSchema = None, referencedTableSchema = if (keep(k.referencedTableSchema)) None else k.referencedTableSchema)
            },
            checkConstraints = checkConstraints.collect { case c if keep(c.constraintSchema) => c.copy(constraintSchema = None) },
            analyzedViews = analyzedViews.collect {
              case (k, v) if keep(k.schema) =>
                k.copy(schema = None) -> v.copy(row = v.row.copy(tableSchema = None))
            },
            aliasTypes = aliasTypes.collect { case a if keepString(a.schemaName) => a },
            clrTypes = clrTypes.collect { case c if keepString(c.schemaName) => c }
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
        checkConstraints <- logger.timed("fetching check constraints")(ds.run(implicit c => fetchCheckConstraints(c)))
        analyzedViews <- logger.timed("fetching and analyzing views")(analyzeViews(ds, viewSelector))
        aliasTypes <- logger.timed("fetching alias types")(ds.run(implicit c => fetchAliasTypes(c)))
        clrTypes <- logger.timed("fetching CLR types")(ds.run(implicit c => fetchClrTypes(c)))
      } yield {
        val input = Input(tables, columns, constraints, keyColumns, checkConstraints, analyzedViews, aliasTypes, clrTypes)
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
            // Use SELECT * FROM view to get metadata (can't prepare CREATE VIEW in SQL Server)
            val selectSql = s"SELECT * FROM [${viewRow.tableSchema.getOrElse("dbo")}].[${viewRow.tableName}] WHERE 1=0"
            SqlServerJdbcMetadata.from(selectSql) match {
              case Right(jdbcMetadata) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata))
              case Left(_) =>
                // If we can't analyze the view SQL, create an empty analysis
                Some(name -> AnalyzedView(viewRow, decomposedSql, SqlServerJdbcMetadata(MaybeReturnsRows.Update)))
            }
          } else None
        }.toMap
      }
    }

    private def fetchViews(implicit conn: Connection): List[SqlServerView] = {
      val sql = SQL"""SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION
                      FROM INFORMATION_SCHEMA.VIEWS
                      WHERE TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME"""
      sql.as(SqlServerView.rowParser(1).*)
    }

    private def fetchTables(implicit conn: Connection): List[SqlServerTable] = {
      val sql = SQL"""SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE
                      FROM INFORMATION_SCHEMA.TABLES
                      WHERE TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME"""
      sql.as(SqlServerTable.rowParser(1).*)
    }

    private def fetchColumns(implicit conn: Connection): List[SqlServerColumn] = {
      // Join with sys.identity_columns to get IDENTITY information
      // Join with sys.types to get user-defined type names (alias types)
      val sql = SQL"""
        SELECT
          c.TABLE_SCHEMA,
          c.TABLE_NAME,
          c.COLUMN_NAME,
          c.ORDINAL_POSITION,
          c.COLUMN_DEFAULT,
          c.IS_NULLABLE,
          c.DATA_TYPE,
          c.CHARACTER_MAXIMUM_LENGTH,
          c.NUMERIC_PRECISION,
          c.NUMERIC_SCALE,
          c.DATETIME_PRECISION,
          CAST(ISNULL(ic.is_identity, 0) AS BIT) AS is_identity,
          ic.seed_value,
          ic.increment_value,
          CASE WHEN t.is_user_defined = 1 THEN s.name ELSE NULL END AS udt_schema,
          CASE WHEN t.is_user_defined = 1 THEN t.name ELSE NULL END AS udt_name
        FROM INFORMATION_SCHEMA.COLUMNS c
        LEFT JOIN sys.columns sc ON sc.object_id = OBJECT_ID(c.TABLE_SCHEMA + '.' + c.TABLE_NAME)
                                   AND sc.name = c.COLUMN_NAME
        LEFT JOIN sys.types t ON sc.user_type_id = t.user_type_id
        LEFT JOIN sys.schemas s ON t.schema_id = s.schema_id
        LEFT JOIN sys.identity_columns ic ON ic.object_id = sc.object_id
                                            AND ic.column_id = sc.column_id
        WHERE c.TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
        ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION"""
      sql.as(SqlServerColumn.rowParser(1).*)
    }

    private def fetchConstraints(implicit conn: Connection): List[SqlServerConstraint] = {
      val sql = SQL"""SELECT CONSTRAINT_SCHEMA, CONSTRAINT_NAME, TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_TYPE
                      FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                      WHERE TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
                      ORDER BY TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME"""
      sql.as(SqlServerConstraint.rowParser(1).*)
    }

    private def fetchCheckConstraints(implicit conn: Connection): List[SqlServerCheckConstraint] = {
      val sql = SQL"""SELECT CONSTRAINT_SCHEMA, CONSTRAINT_NAME, CHECK_CLAUSE
                      FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
                      WHERE CONSTRAINT_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
                      ORDER BY CONSTRAINT_SCHEMA, CONSTRAINT_NAME"""
      sql.as(SqlServerCheckConstraint.rowParser(1).*)
    }

    private def fetchKeyColumns(implicit conn: Connection): List[SqlServerKeyColumn] = {
      val sql = SQL"""SELECT kcu.CONSTRAINT_SCHEMA, kcu.CONSTRAINT_NAME, kcu.TABLE_SCHEMA, kcu.TABLE_NAME, kcu.COLUMN_NAME,
                             kcu.ORDINAL_POSITION,
                             fk.UNIQUE_CONSTRAINT_SCHEMA, fk.REFERENCED_TABLE_NAME, fk.REFERENCED_COLUMN_NAME
                      FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                      LEFT JOIN (
                        SELECT
                          rc.CONSTRAINT_SCHEMA,
                          rc.CONSTRAINT_NAME,
                          rc.UNIQUE_CONSTRAINT_SCHEMA,
                          kcu2.TABLE_NAME AS REFERENCED_TABLE_NAME,
                          kcu2.COLUMN_NAME AS REFERENCED_COLUMN_NAME,
                          kcu2.ORDINAL_POSITION
                        FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                        INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu2
                          ON rc.UNIQUE_CONSTRAINT_SCHEMA = kcu2.CONSTRAINT_SCHEMA
                          AND rc.UNIQUE_CONSTRAINT_NAME = kcu2.CONSTRAINT_NAME
                      ) fk ON kcu.CONSTRAINT_SCHEMA = fk.CONSTRAINT_SCHEMA
                           AND kcu.CONSTRAINT_NAME = fk.CONSTRAINT_NAME
                           AND kcu.ORDINAL_POSITION = fk.ORDINAL_POSITION
                      WHERE kcu.TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
                      ORDER BY kcu.TABLE_SCHEMA, kcu.TABLE_NAME, kcu.CONSTRAINT_NAME, kcu.ORDINAL_POSITION"""
      sql.as(SqlServerKeyColumn.rowParser(1).*)
    }

    private def fetchAliasTypes(implicit conn: Connection): List[SqlServerAliasType] = {
      val sql = SQL"""
        SELECT
          s.name AS schema_name,
          t.name AS type_name,
          bt.name AS base_type_name,
          CASE WHEN t.max_length = -1 THEN NULL ELSE t.max_length END AS max_length,
          CASE WHEN t.precision = 0 THEN NULL ELSE t.precision END AS precision,
          CASE WHEN t.scale = 0 THEN NULL ELSE t.scale END AS scale,
          t.is_nullable
        FROM sys.types t
        JOIN sys.schemas s ON t.schema_id = s.schema_id
        JOIN sys.types bt ON t.system_type_id = bt.user_type_id
        WHERE t.is_user_defined = 1
          AND t.is_table_type = 0
          AND t.is_assembly_type = 0
          AND s.name NOT IN ('sys', 'INFORMATION_SCHEMA')
        ORDER BY s.name, t.name"""
      sql.as(SqlServerAliasType.rowParser(1).*)
    }

    private def fetchClrTypes(implicit conn: Connection): List[SqlServerClrType] = {
      val sql = SQL"""
        SELECT
          s.name AS schema_name,
          t.name AS type_name,
          a.name AS assembly_name,
          at.assembly_class
        FROM sys.assembly_types at
        JOIN sys.types t ON at.user_type_id = t.user_type_id
        JOIN sys.schemas s ON t.schema_id = s.schema_id
        JOIN sys.assemblies a ON at.assembly_id = a.assembly_id
        WHERE s.name NOT IN ('sys', 'INFORMATION_SCHEMA')
        ORDER BY s.name, t.name"""
      sql.as(SqlServerClrType.rowParser(1).*)
    }
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    Input.fromDb(logger, ds, viewSelector, schemaMode).map(input => fromInput(input))
  }

  def fromInput(input: Input): MetaDb = {
    // Process alias types (SQL Server's version of domains)
    // Use typeMapper with empty domains list to avoid circular dependency
    val aliasDomains: List[db.Domain] = input.aliasTypes.map { aliasType =>
      val name = db.RelationName(Some(aliasType.schemaName), aliasType.typeName)

      // Map the base type to a db.SqlServerType
      val tpe = SqlServerTypeMapperDb(Nil).dbTypeFrom(
        dataType = aliasType.baseTypeName,
        characterMaximumLength = aliasType.maxLength,
        numericPrecision = aliasType.precision,
        numericScale = aliasType.scale,
        datetimePrecision = None
      )

      db.Domain(
        name = name,
        tpe = tpe,
        originalType = aliasType.baseTypeName,
        isNotNull = if (aliasType.isNullable) Nullability.Nullable else Nullability.NoNulls,
        hasDefault = false,
        constraintDefinition = None
      )
    }

    // Process CLR types as domains with byte[] underlying type
    val clrDomains: List[db.Domain] = input.clrTypes.map { clrType =>
      val name = db.RelationName(Some(clrType.schemaName), clrType.typeName)

      db.Domain(
        name = name,
        tpe = db.SqlServerType.VarBinary(None), // CLR types are serialized as binary
        originalType = s"CLR:${clrType.assemblyName}.${clrType.assemblyClass}",
        isNotNull = Nullability.NoNulls, // CLR types are typically non-nullable
        hasDefault = false,
        constraintDefinition = Some(s"Assembly: ${clrType.assemblyName}, Class: ${clrType.assemblyClass}")
      )
    }

    val domains = aliasDomains ++ clrDomains

    // Create typeMapper with domains for column processing
    val typeMapper = SqlServerTypeMapperDb(domains)

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
    val columnsByTable: Map[db.RelationName, List[SqlServerColumn]] =
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

              val baseTpe = typeMapper.col(
                udtSchema = c.udtSchema,
                udtName = c.udtName,
                dataType = c.dataType,
                characterMaximumLength = c.characterMaximumLength,
                numericPrecision = c.numericPrecision,
                numericScale = c.numericScale,
                datetimePrecision = c.datetimePrecision
              )

              // Detect ISJSON constraint and override type to Json
              val tpe = if (input.hasIsJsonConstraint(c.tableSchema, c.tableName, c.columnName)) {
                db.SqlServerType.Json
              } else baseTpe

              // Detect IDENTITY and ROWVERSION columns
              val generated: Option[db.Generated] =
                if (c.isIdentity && c.identitySeed.isDefined && c.identityIncrement.isDefined)
                  Some(db.Generated.SqlServerIdentity(c.identitySeed.get, c.identityIncrement.get))
                else if (c.dataType.toUpperCase == "ROWVERSION" || c.dataType.toUpperCase == "TIMESTAMP")
                  Some(db.Generated.SqlServerRowVersion)
                else None

              // Use UDT name if this column uses an alias type, otherwise use DATA_TYPE
              val udtName = c.udtName match {
                case Some(udt) => Some(c.udtSchema.map(_ + ".").getOrElse("") + udt)
                case None      => Some(c.dataType)
              }

              db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = udtName,
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = generated,
                comment = None, // SQL Server extended properties not yet implemented
                constraints = Nil,
                jsonDescription = DebugJson(Map("dataType" -> c.dataType, "isIdentity" -> c.isIdentity.toString))
              )
            }

            db.Table(
              name = relationName,
              comment = None, // SQL Server extended properties not yet implemented
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
    val viewTables = input.tables.filter(_.tableType == "VIEW")

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

              val tpe = typeMapper.col(
                udtSchema = c.udtSchema,
                udtName = c.udtName,
                dataType = c.dataType,
                characterMaximumLength = c.characterMaximumLength,
                numericPrecision = c.numericPrecision,
                numericScale = c.numericScale,
                datetimePrecision = c.datetimePrecision
              )

              // Use UDT name if this column uses an alias type, otherwise use DATA_TYPE
              val udtName = c.udtName match {
                case Some(udt) => Some(c.udtSchema.map(_ + ".").getOrElse("") + udt)
                case None      => Some(c.dataType)
              }

              val col = db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = udtName,
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = None,
                comment = None,
                constraints = Nil,
                jsonDescription = DebugJson(Map("dataType" -> c.dataType))
              )

              (col, parsedName)
            }

            db.View(
              name = relationName,
              comment = None,
              decomposedSql = decomposedSql,
              cols = cols,
              deps = deps,
              isMaterialized = false // SQL Server doesn't have materialized views (use indexed views instead)
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // SQL Server doesn't have PostgreSQL-style enums
    MetaDb(DbType.SqlServer, tables ++ views, enums = Nil, domains = domains)
  }
}
