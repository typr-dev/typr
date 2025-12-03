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

  case class AnalyzedView(
      row: ViewFindAllSqlRow,
      decomposedSql: DecomposedSql,
      jdbcMetadata: JdbcMetadata,
      nullabilityAnalysis: NullabilityFromExplain.NullableColumns
  )

  object Input {
    def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ev: ExecutionContext): Future[Input] = {
      val tableConstraints = logger.timed("fetching tableConstraints")(ds.run(implicit c => (new TableConstraintsViewRepoImpl).selectAll))
      val keyColumnUsage = logger.timed("fetching keyColumnUsage")(ds.run(implicit c => (new KeyColumnUsageViewRepoImpl).selectAll))
      val referentialConstraints = logger.timed("fetching referentialConstraints")(ds.run(implicit c => (new ReferentialConstraintsViewRepoImpl).selectAll))
      val pgEnums = logger.timed("fetching pgEnums")(ds.run(implicit c => (new EnumsSqlRepoImpl).apply()))
      val tables = logger.timed("fetching tables")(ds.run(implicit c => (new TablesViewRepoImpl).selectAll.filter(_.tableType.contains("BASE TABLE"))))
      val columns = logger.timed("fetching columns")(ds.run(implicit c => (new ColumnsViewRepoImpl).selectAll))
      val views = logger.timed("fetching and analyzing views")(ds.run(implicit c => (new ViewFindAllSqlRepoImpl).apply())).flatMap { viewRows =>
        val analyzedRows: List[Future[(db.RelationName, AnalyzedView)]] = viewRows.flatMap { viewRow =>
          val name = db.RelationName(viewRow.tableSchema, viewRow.tableName.get)
          if (viewRow.viewDefinition.isDefined && viewSelector.include(name)) Some {
            val sqlContent = viewRow.viewDefinition.get
            val decomposedSql = DecomposedSql.parse(sqlContent)
            val jdbcMetadata = ds.run(implicit c => JdbcMetadata.from(sqlContent))
            val nullabilityAnalysis = ds.run(implicit c => NullabilityFromExplain.from(decomposedSql, Nil))
            for {
              jdbcMetadata <- jdbcMetadata.map {
                case Left(str)    => sys.error(str)
                case Right(value) => value
              }
              nullabilityAnalysis <- nullabilityAnalysis
            } yield name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata, nullabilityAnalysis)
          }
          else None
        }
        Future.sequence(analyzedRows).map(_.toMap)
      }
      val domains = logger.timed("fetching domains")(ds.run(implicit c => (new DomainsSqlRepoImpl).apply()))
      val columnComments = logger.timed("fetching columnComments")(ds.run(implicit c => (new CommentsSqlRepoImpl).apply()))
      val constraints = logger.timed("fetching constraints")(ds.run(implicit c => (new ConstraintsSqlRepoImpl).apply()))
      val tableComments = logger.timed("fetching tableComments")(ds.run(implicit c => (new TableCommentsSqlRepoImpl).apply()))
      for {
        tableConstraints <- tableConstraints
        keyColumnUsage <- keyColumnUsage
        referentialConstraints <- referentialConstraints
        pgEnums <- pgEnums
        tables <- tables
        columns <- columns
        views <- views
        domains <- domains
        columnComments <- columnComments
        constraints <- constraints
        tableComments <- tableComments
      } yield {
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
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] =
    Input.fromDb(logger, ds, viewSelector, schemaMode: SchemaMode).map(input => fromInput(logger, input))

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
    val views: Map[db.RelationName, Lazy[db.View]] =
      input.views.map { case (relationName, AnalyzedView(viewRow, decomposedSql, jdbcMetadata, nullabilityAnalysis)) =>
        val lazyAnalysis = Lazy {
          val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
            jdbcMetadata.columns match {
              case MaybeReturnsRows.Query(columns) =>
                columns.toList.flatMap(col => col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))).toMap
              case MaybeReturnsRows.Update =>
                Map.empty
            }

          val cols: NonEmptyList[(db.Col, ParsedName)] =
            jdbcMetadata.columns match {
              case MaybeReturnsRows.Query(metadataCols) =>
                metadataCols.zipWithIndex.map { case (mdCol, idx) =>
                  val nullability: Nullability =
                    mdCol.parsedColumnName.nullability.getOrElse {
                      if (nullabilityAnalysis.nullableIndices.exists(_.values(idx))) Nullability.Nullable
                      else mdCol.isNullable.toNullability
                    }

                  val dbType = typeMapperDb.dbTypeFrom(mdCol.columnTypeName, Some(mdCol.precision)) { () =>
                    logger.warn(s"Couldn't translate type from view ${relationName.value} column ${mdCol.name.value} with type ${mdCol.columnTypeName}. Falling back to text")
                  }

                  val coord = (relationName, mdCol.name)
                  val dbCol = db.Col(
                    parsedName = mdCol.parsedColumnName,
                    tpe = dbType,
                    udtName = None,
                    columnDefault = None,
                    maybeGenerated = None,
                    comment = comments.get(coord),
                    jsonDescription = DebugJson(mdCol),
                    nullability = nullability,
                    constraints = constraints.getOrElse(coord, Nil)
                  )
                  (dbCol, mdCol.parsedColumnName)
                }

              case MaybeReturnsRows.Update => ???
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
