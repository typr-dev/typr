package typr
package internal
package db2

import anorm.Column
import anorm.RowParser
import anorm.SqlStringInterpolation
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** DB2-specific metadata extraction using Anorm
  *
  * DB2 LUW uses SYSCAT views for metadata:
  *   - SYSCAT.TABLES - Table information
  *   - SYSCAT.COLUMNS - Column information
  *   - SYSCAT.TABCONST - Table constraints
  *   - SYSCAT.KEYCOLUSE - Key column usage
  *   - SYSCAT.REFERENCES - Foreign key references
  *   - SYSCAT.INDEXES - Index information
  */
object Db2MetaDb {

  case class Db2Column(
      tableSchema: Option[String],
      tableName: String,
      columnName: String,
      ordinalPosition: Int,
      columnDefault: Option[String],
      isNullable: String, // Y or N
      typeName: String,
      length: Option[Int],
      scale: Option[Int],
      typeSchemaName: Option[String],
      typeModuleName: Option[String],
      identity: String, // Y or N
      generated: String, // A=always, D=by default, (blank)=not generated
      remarks: Option[String]
  )

  object Db2Column {
    def rowParser(idx: Int): RowParser[Db2Column] = RowParser[Db2Column] { row =>
      Success(
        Db2Column(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim),
          tableName = row(idx + 1)(Column.columnToString).trim,
          columnName = row(idx + 2)(Column.columnToString).trim,
          ordinalPosition = row(idx + 3)(Column.columnToInt),
          columnDefault = row(idx + 4)(Column.columnToOption(Column.columnToString)),
          isNullable = row(idx + 5)(Column.columnToString).trim,
          typeName = row(idx + 6)(Column.columnToString).trim,
          length = row(idx + 7)(Column.columnToOption(Column.columnToInt)),
          scale = row(idx + 8)(Column.columnToOption(Column.columnToInt)),
          typeSchemaName = row(idx + 9)(Column.columnToOption(Column.columnToString)).map(_.trim),
          typeModuleName = None, // TYPEMODULENAME doesn't exist in DB2 11.5
          identity = row(idx + 10)(Column.columnToString).trim,
          generated = row(idx + 11)(Column.columnToString).trim,
          remarks = row(idx + 12)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Identity column attributes from SYSCAT.COLIDENTATTRIBUTES */
  case class Db2IdentityAttribute(
      tableSchema: String,
      tableName: String,
      columnName: String,
      start: Long,
      increment: Long,
      minValue: Long,
      maxValue: Long,
      cycle: String // Y or N
  )

  object Db2IdentityAttribute {
    def rowParser(idx: Int): RowParser[Db2IdentityAttribute] = RowParser[Db2IdentityAttribute] { row =>
      Success(
        Db2IdentityAttribute(
          tableSchema = row(idx + 0)(Column.columnToString).trim,
          tableName = row(idx + 1)(Column.columnToString).trim,
          columnName = row(idx + 2)(Column.columnToString).trim,
          start = row(idx + 3)(Column.columnToLong),
          increment = row(idx + 4)(Column.columnToLong),
          minValue = row(idx + 5)(Column.columnToLong),
          maxValue = row(idx + 6)(Column.columnToLong),
          cycle = row(idx + 7)(Column.columnToString).trim
        )
      )
    }
  }

  case class Db2Table(
      tableSchema: Option[String],
      tableName: String,
      tableType: String, // T=table, V=view, S=materialized query table (MQT), A=alias, etc.
      remarks: Option[String]
  )

  object Db2Table {
    def rowParser(idx: Int): RowParser[Db2Table] = RowParser[Db2Table] { row =>
      Success(
        Db2Table(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim),
          tableName = row(idx + 1)(Column.columnToString).trim,
          tableType = row(idx + 2)(Column.columnToString).trim,
          remarks = row(idx + 3)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Distinct type (user-defined type) from SYSCAT.DATATYPES */
  case class Db2DistinctType(
      typeSchema: String,
      typeName: String,
      sourceTypeName: String,
      sourceTypeSchema: String,
      length: Option[Int],
      scale: Option[Int],
      remarks: Option[String]
  )

  object Db2DistinctType {
    def rowParser(idx: Int): RowParser[Db2DistinctType] = RowParser[Db2DistinctType] { row =>
      Success(
        Db2DistinctType(
          typeSchema = row(idx + 0)(Column.columnToString).trim,
          typeName = row(idx + 1)(Column.columnToString).trim,
          sourceTypeName = row(idx + 2)(Column.columnToString).trim,
          sourceTypeSchema = row(idx + 3)(Column.columnToString).trim,
          length = row(idx + 4)(Column.columnToOption(Column.columnToInt)),
          scale = row(idx + 5)(Column.columnToOption(Column.columnToInt)),
          remarks = row(idx + 6)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Check constraint from SYSCAT.CHECKS */
  case class Db2Check(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      checkClause: String
  )

  object Db2Check {
    def rowParser(idx: Int): RowParser[Db2Check] = RowParser[Db2Check] { row =>
      val tabSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim)
      Success(
        Db2Check(
          constraintSchema = tabSchema,
          constraintName = row(idx + 1)(Column.columnToString).trim,
          tableSchema = tabSchema,
          tableName = row(idx + 2)(Column.columnToString).trim,
          checkClause = row(idx + 3)(Column.columnToString)
        )
      )
    }
  }

  case class Db2Constraint(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      constraintType: String // P=primary key, U=unique, F=foreign key, K=check
  )

  object Db2Constraint {
    def rowParser(idx: Int): RowParser[Db2Constraint] = RowParser[Db2Constraint] { row =>
      val tabSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim)
      Success(
        Db2Constraint(
          constraintSchema = tabSchema, // DB2 doesn't have CONSTSCHEMA, use TABSCHEMA
          constraintName = row(idx + 1)(Column.columnToString).trim,
          tableSchema = tabSchema,
          tableName = row(idx + 2)(Column.columnToString).trim,
          constraintType = row(idx + 3)(Column.columnToString).trim
        )
      )
    }
  }

  case class Db2KeyColumn(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      columnName: String,
      ordinalPosition: Int
  )

  object Db2KeyColumn {
    def rowParser(idx: Int): RowParser[Db2KeyColumn] = RowParser[Db2KeyColumn] { row =>
      val tabSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim)
      Success(
        Db2KeyColumn(
          constraintSchema = tabSchema, // DB2 doesn't have CONSTSCHEMA, use TABSCHEMA
          constraintName = row(idx + 1)(Column.columnToString).trim,
          tableSchema = tabSchema,
          tableName = row(idx + 2)(Column.columnToString).trim,
          columnName = row(idx + 3)(Column.columnToString).trim,
          ordinalPosition = row(idx + 4)(Column.columnToInt)
        )
      )
    }
  }

  case class Db2Reference(
      constraintSchema: Option[String],
      constraintName: String,
      tableSchema: Option[String],
      tableName: String,
      refTableSchema: Option[String],
      refTableName: String,
      refConstraintName: String
  )

  object Db2Reference {
    def rowParser(idx: Int): RowParser[Db2Reference] = RowParser[Db2Reference] { row =>
      val tabSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim)
      Success(
        Db2Reference(
          constraintSchema = tabSchema, // DB2 doesn't have CONSTSCHEMA, use TABSCHEMA
          constraintName = row(idx + 1)(Column.columnToString).trim,
          tableSchema = tabSchema,
          tableName = row(idx + 2)(Column.columnToString).trim,
          refTableSchema = row(idx + 3)(Column.columnToOption(Column.columnToString)).map(_.trim),
          refTableName = row(idx + 4)(Column.columnToString).trim,
          refConstraintName = row(idx + 5)(Column.columnToString).trim
        )
      )
    }
  }

  case class Db2View(
      tableSchema: Option[String],
      tableName: String,
      viewDefinition: Option[String]
  )

  object Db2View {
    def rowParser(idx: Int): RowParser[Db2View] = RowParser[Db2View] { row =>
      Success(
        Db2View(
          tableSchema = row(idx + 0)(Column.columnToOption(Column.columnToString)).map(_.trim),
          tableName = row(idx + 1)(Column.columnToString).trim,
          viewDefinition = row(idx + 2)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class AnalyzedView(
      row: Db2View,
      decomposedSql: DecomposedSql,
      jdbcMetadata: Db2JdbcMetadata
  )

  case class Input(
      tables: List[Db2Table],
      columns: List[Db2Column],
      constraints: List[Db2Constraint],
      keyColumns: List[Db2KeyColumn],
      references: List[Db2Reference],
      analyzedViews: Map[db.RelationName, AnalyzedView],
      distinctTypes: List[Db2DistinctType],
      checks: List[Db2Check],
      identityAttributes: List[Db2IdentityAttribute]
  ) {
    // Build a lookup map for identity attributes
    val identityAttrsMap: Map[(Option[String], String, String), Db2IdentityAttribute] =
      identityAttributes.map(ia => (Some(ia.tableSchema): Option[String], ia.tableName, ia.columnName) -> ia).toMap

    def filter(schemaMode: SchemaMode): Input = {
      schemaMode match {
        case SchemaMode.MultiSchema => this
        case SchemaMode.SingleSchema(wantedSchema) =>
          def keep(os: Option[String]): Boolean = os.contains(wantedSchema)
          def keepStr(s: String): Boolean = s == wantedSchema

          Input(
            tables = tables.collect { case t if keep(t.tableSchema) => t.copy(tableSchema = None) },
            columns = columns.collect { case c if keep(c.tableSchema) => c.copy(tableSchema = None, typeSchemaName = c.typeSchemaName.flatMap(ts => if (ts == wantedSchema) None else Some(ts))) },
            constraints = constraints.collect { case c if keep(c.tableSchema) || keep(c.constraintSchema) => c.copy(tableSchema = None, constraintSchema = None) },
            keyColumns = keyColumns.collect {
              case k if keep(k.tableSchema) || keep(k.constraintSchema) =>
                k.copy(tableSchema = None, constraintSchema = None)
            },
            references = references.collect {
              case r if keep(r.tableSchema) || keep(r.constraintSchema) =>
                r.copy(tableSchema = None, constraintSchema = None, refTableSchema = if (keep(r.refTableSchema)) None else r.refTableSchema)
            },
            analyzedViews = analyzedViews.collect {
              case (k, v) if keep(k.schema) =>
                k.copy(schema = None) -> v.copy(row = v.row.copy(tableSchema = None))
            },
            distinctTypes = distinctTypes.collect { case dt if keepStr(dt.typeSchema) => dt.copy(typeSchema = "") },
            checks = checks.collect {
              case c if keep(c.tableSchema) || keep(c.constraintSchema) =>
                c.copy(tableSchema = None, constraintSchema = None)
            },
            identityAttributes = identityAttributes.filter(ia => keepStr(ia.tableSchema))
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
        references <- logger.timed("fetching references")(ds.run(implicit c => fetchReferences(c)))
        analyzedViews <- logger.timed("fetching and analyzing views")(analyzeViews(ds, viewSelector))
        distinctTypes <- logger.timed("fetching distinct types")(ds.run(implicit c => fetchDistinctTypes(c)))
        checks <- logger.timed("fetching check constraints")(ds.run(implicit c => fetchChecks(c)))
        identityAttributes <- logger.timed("fetching identity attributes")(ds.run(implicit c => fetchIdentityAttributes(c)))
      } yield {
        val input = Input(tables, columns, constraints, keyColumns, references, analyzedViews, distinctTypes, checks, identityAttributes)
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
            Db2JdbcMetadata.from(sqlContent) match {
              case Right(jdbcMetadata) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata))
              case Left(_) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, Db2JdbcMetadata(MaybeReturnsRows.Update)))
            }
          } else None
        }.toMap
      }
    }

    private def fetchViews(implicit conn: Connection): List[Db2View] = {
      val sql = SQL"""SELECT VIEWSCHEMA, VIEWNAME, TEXT
                      FROM SYSCAT.VIEWS
                      WHERE VIEWSCHEMA NOT LIKE 'SYS%'
                      ORDER BY VIEWSCHEMA, VIEWNAME"""
      sql.as(Db2View.rowParser(1).*)
    }

    private def fetchTables(implicit conn: Connection): List[Db2Table] = {
      // TYPE: T=table, V=view, S=materialized query table (MQT/summary table)
      val sql = SQL"""SELECT TABSCHEMA, TABNAME, TYPE, REMARKS
                      FROM SYSCAT.TABLES
                      WHERE TYPE IN ('T', 'V', 'S')
                        AND TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME"""
      sql.as(Db2Table.rowParser(1).*)
    }

    private def fetchColumns(implicit conn: Connection): List[Db2Column] = {
      // Note: TYPEMODULENAME doesn't exist in DB2 11.5, so we don't select it
      val sql = SQL"""SELECT TABSCHEMA, TABNAME, COLNAME, COLNO,
                             DEFAULT, NULLS, TYPENAME,
                             LENGTH, SCALE, TYPESCHEMA,
                             IDENTITY, GENERATED, REMARKS
                      FROM SYSCAT.COLUMNS
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, COLNO"""
      sql.as(Db2Column.rowParser(1).*)
    }

    private def fetchConstraints(implicit conn: Connection): List[Db2Constraint] = {
      // Note: DB2 11.5 doesn't have CONSTSCHEMA, constraints share schema with table
      val sql = SQL"""SELECT TABSCHEMA, CONSTNAME, TABNAME, TYPE
                      FROM SYSCAT.TABCONST
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, CONSTNAME"""
      sql.as(Db2Constraint.rowParser(1).*)
    }

    private def fetchKeyColumns(implicit conn: Connection): List[Db2KeyColumn] = {
      // Note: DB2 11.5 doesn't have CONSTSCHEMA, constraints share schema with table
      val sql = SQL"""SELECT TABSCHEMA, CONSTNAME, TABNAME, COLNAME, COLSEQ
                      FROM SYSCAT.KEYCOLUSE
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, CONSTNAME, COLSEQ"""
      sql.as(Db2KeyColumn.rowParser(1).*)
    }

    private def fetchReferences(implicit conn: Connection): List[Db2Reference] = {
      // Note: DB2 11.5 doesn't have CONSTSCHEMA, constraints share schema with table
      val sql = SQL"""SELECT TABSCHEMA, CONSTNAME, TABNAME,
                             REFTABSCHEMA, REFTABNAME, REFKEYNAME
                      FROM SYSCAT.REFERENCES
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, CONSTNAME"""
      sql.as(Db2Reference.rowParser(1).*)
    }

    private def fetchDistinctTypes(implicit conn: Connection): List[Db2DistinctType] = {
      // Distinct types are user-defined types with:
      // - METATYPE='T' (user-defined typed)
      // - SOURCENAME IS NOT NULL (has a source type, distinguishing from structured types)
      val sql = SQL"""SELECT TYPESCHEMA, TYPENAME, SOURCENAME, SOURCESCHEMA,
                             LENGTH, SCALE, REMARKS
                      FROM SYSCAT.DATATYPES
                      WHERE METATYPE = 'T'
                        AND SOURCENAME IS NOT NULL
                        AND TYPESCHEMA NOT LIKE 'SYS%'
                      ORDER BY TYPESCHEMA, TYPENAME"""
      sql.as(Db2DistinctType.rowParser(1).*)
    }

    private def fetchChecks(implicit conn: Connection): List[Db2Check] = {
      // Check constraints from SYSCAT.CHECKS
      val sql = SQL"""SELECT TABSCHEMA, CONSTNAME, TABNAME, TEXT
                      FROM SYSCAT.CHECKS
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, CONSTNAME"""
      sql.as(Db2Check.rowParser(1).*)
    }

    private def fetchIdentityAttributes(implicit conn: Connection): List[Db2IdentityAttribute] = {
      // Identity column attributes from SYSCAT.COLIDENTATTRIBUTES
      val sql = SQL"""SELECT TABSCHEMA, TABNAME, COLNAME, START, INCREMENT, MINVALUE, MAXVALUE, CYCLE
                      FROM SYSCAT.COLIDENTATTRIBUTES
                      WHERE TABSCHEMA NOT LIKE 'SYS%'
                      ORDER BY TABSCHEMA, TABNAME, COLNAME"""
      sql.as(Db2IdentityAttribute.rowParser(1).*)
    }
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    Input.fromDb(logger, ds, viewSelector, schemaMode).map(input => fromInput(input))
  }

  def fromInput(input: Input): MetaDb = {
    import scala.collection.immutable.SortedSet

    // Build distinct types map for type resolution
    val distinctTypesMap: Map[(String, String), Db2DistinctType] =
      input.distinctTypes.map(dt => (dt.typeSchema, dt.typeName) -> dt).toMap

    // Build domains from distinct types
    val domains: List[db.Domain] = input.distinctTypes.map { dt =>
      val typeMapper = Db2TypeMapperDb()
      val sourceType = typeMapper.dbTypeFrom(
        typeName = dt.sourceTypeName,
        length = dt.length,
        scale = dt.scale,
        typeSchemaName = Some(dt.sourceTypeSchema),
        typeModuleName = None
      )
      db.Domain(
        name = db.RelationName(Option(dt.typeSchema).filter(_.nonEmpty), dt.typeName),
        tpe = sourceType,
        originalType = dt.sourceTypeName,
        isNotNull = Nullability.NullableUnknown, // DB2 distinct types don't have NOT NULL constraint
        hasDefault = false, // DB2 distinct types don't have defaults
        constraintDefinition = None
      )
    }

    // Build check constraints map: (table, column) -> List[Constraint]
    val checkConstraints: Map[(db.RelationName, db.ColName), List[db.Constraint]] = {
      // DB2 check constraints can reference multiple columns, but we associate with the first column mentioned
      // For simplicity, we'll parse the TEXT to find column references
      input.checks
        .flatMap { check =>
          val relationName = db.RelationName(check.tableSchema, check.tableName)
          // Extract column names from the check clause - simple heuristic
          // Check constraints in DB2 look like: "COL_NAME > 0" or "COL_NAME IS NOT NULL"
          val colNamesInTable = input.columns
            .filter(c => c.tableSchema == check.tableSchema && c.tableName == check.tableName)
            .map(c => db.ColName(c.columnName))
            .toSet

          // Find which columns are mentioned in the check clause
          val mentionedCols = colNamesInTable.filter(col => check.checkClause.toUpperCase.contains(col.value.toUpperCase))
          if (mentionedCols.nonEmpty) {
            val constraint = db.Constraint(
              name = check.constraintName,
              columns = SortedSet.empty[db.ColName] ++ mentionedCols,
              checkClause = check.checkClause
            )
            mentionedCols.map(col => (relationName, col) -> constraint)
          } else Nil
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    val typeMapper = Db2TypeMapperDb(distinctTypesMap)

    // Build primary keys
    val primaryKeys: Map[db.RelationName, db.PrimaryKey] = {
      val pkConstraints = input.constraints.filter(_.constraintType == "P")
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

    // Build foreign keys using REFERENCES table
    val foreignKeys: Map[db.RelationName, List[db.ForeignKey]] = {
      val fkConstraints = input.constraints.filter(_.constraintType == "F")

      fkConstraints
        .flatMap { c =>
          // Get columns for this FK
          val fkCols = input.keyColumns
            .filter(kc => kc.constraintName == c.constraintName && kc.tableSchema == c.tableSchema && kc.tableName == c.tableName)
            .sortBy(_.ordinalPosition)
            .map(kc => db.ColName(kc.columnName))

          // Get reference info
          val ref = input.references.find(r => r.constraintName == c.constraintName && r.tableSchema == c.tableSchema && r.tableName == c.tableName)

          ref.flatMap { r =>
            // Get columns from referenced PK
            val refCols = input.keyColumns
              .filter(kc => kc.constraintName == r.refConstraintName && kc.tableSchema == r.refTableSchema && kc.tableName == r.refTableName)
              .sortBy(_.ordinalPosition)
              .map(kc => db.ColName(kc.columnName))

            NonEmptyList.fromList(fkCols).flatMap { nelFkCols =>
              NonEmptyList.fromList(refCols).map { nelRefCols =>
                val name = db.RelationName(c.tableSchema, c.tableName)
                val refTable = db.RelationName(r.refTableSchema, r.refTableName)
                val constraintName = db.RelationName(c.constraintSchema, c.constraintName)
                name -> db.ForeignKey(nelFkCols, refTable, nelRefCols, constraintName)
              }
            }
          }
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Build unique keys
    val uniqueKeys: Map[db.RelationName, List[db.UniqueKey]] = {
      val ukConstraints = input.constraints.filter(_.constraintType == "U")

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
    val columnsByTable: Map[db.RelationName, List[Db2Column]] =
      input.columns.groupBy(c => db.RelationName(c.tableSchema, c.tableName))

    // Build tables (type T)
    val baseTables = input.tables.filter(_.tableType == "T")

    val tables: Map[db.RelationName, Lazy[db.Table]] =
      baseTables.flatMap { table =>
        val relationName = db.RelationName(table.tableSchema, table.tableName)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)).map { columns =>
          val lazyAnalysis = Lazy {
            val mappedCols: NonEmptyList[db.Col] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.isNullable match {
                case "Y" => Nullability.Nullable
                case "N" => Nullability.NoNulls
                case _   => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                typeName = c.typeName,
                length = c.length,
                scale = c.scale,
                typeSchemaName = c.typeSchemaName,
                typeModuleName = c.typeModuleName
              )

              // Detect identity columns
              val generated: Option[db.Generated] =
                if (c.identity == "Y") {
                  // Look up identity attributes from SYSCAT.COLIDENTATTRIBUTES
                  val identityAttrs = input.identityAttrsMap.get((c.tableSchema, c.tableName, c.columnName))
                  Some(
                    db.Generated.Identity(
                      identityGeneration = if (c.generated == "A") "ALWAYS" else "BY DEFAULT",
                      identityStart = identityAttrs.map(_.start.toString),
                      identityIncrement = identityAttrs.map(_.increment.toString),
                      identityMaximum = identityAttrs.map(_.maxValue.toString),
                      identityMinimum = identityAttrs.map(_.minValue.toString)
                    )
                  )
                } else if (c.generated == "A" || c.generated == "D") {
                  // Generated column (computed)
                  Some(
                    db.Generated.IsGenerated(
                      generation = if (c.generated == "A") "ALWAYS" else "BY DEFAULT",
                      expression = c.columnDefault
                    )
                  )
                } else None

              val colConstraints = checkConstraints.getOrElse((relationName, db.ColName(c.columnName)), Nil)

              db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.typeName),
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = generated,
                comment = c.remarks,
                constraints = colConstraints,
                jsonDescription = DebugJson(Map("typeName" -> c.typeName, "identity" -> c.identity, "generated" -> c.generated))
              )
            }

            db.Table(
              name = relationName,
              comment = table.remarks,
              cols = mappedCols,
              primaryKey = primaryKeys.get(relationName),
              uniqueKeys = uniqueKeys.getOrElse(relationName, Nil),
              foreignKeys = foreignKeys.getOrElse(relationName, Nil)
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // Build views (type V) and materialized query tables (type S)
    val viewTables = input.tables.filter(t => t.tableType == "V" || t.tableType == "S")

    val views: Map[db.RelationName, Lazy[db.View]] =
      viewTables.flatMap { view =>
        val relationName = db.RelationName(view.tableSchema, view.tableName)
        val maybeAnalyzed = input.analyzedViews.get(relationName)
        val isMaterialized = view.tableType == "S" // S = Summary table (MQT)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.ordinalPosition)).map { columns =>
          val lazyAnalysis = Lazy {
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
                case "Y" => Nullability.Nullable
                case "N" => Nullability.NoNulls
                case _   => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                typeName = c.typeName,
                length = c.length,
                scale = c.scale,
                typeSchemaName = c.typeSchemaName,
                typeModuleName = c.typeModuleName
              )

              val col = db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.typeName),
                nullability = nullability,
                columnDefault = c.columnDefault,
                maybeGenerated = None,
                comment = c.remarks,
                constraints = Nil,
                jsonDescription = DebugJson(Map("typeName" -> c.typeName))
              )

              (col, parsedName)
            }

            db.View(
              name = relationName,
              comment = view.remarks,
              decomposedSql = decomposedSql,
              cols = cols,
              deps = deps,
              isMaterialized = isMaterialized
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // DB2 doesn't have PostgreSQL-style enums, but has distinct types (similar to domains)
    MetaDb(DbType.DB2, tables ++ views, enums = Nil, domains = domains)
  }
}
