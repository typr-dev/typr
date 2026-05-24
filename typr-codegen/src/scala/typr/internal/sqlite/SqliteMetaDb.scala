package typr
package internal
package sqlite

import anorm.Column
import anorm.RowParser
import anorm.SqlStringInterpolation
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** SQLite metadata extraction.
  *
  * SQLite has a single namespace per database (no schemas in the SQL-standard sense — the `main`, `temp`, and attached database names aren't user-defined schemas). We surface every relation under
  * `RelationName(None, name)`.
  *
  * The metadata comes from:
  *   - `sqlite_master` (or `sqlite_schema`) for tables, views, and their DDL
  *   - `pragma_table_info(name)` for column info (type, notnull, dflt_value, pk-ordinal)
  *   - `pragma_foreign_key_list(name)` for foreign keys
  *   - `pragma_index_list(name)` + `pragma_index_info(idx)` for unique indexes
  *
  * SQLite has no native ENUM types or DOMAINs, so those are always empty.
  */
object SqliteMetaDb {

  case class SqliteMaster(
      objectType: String, // "table" | "view"
      name: String,
      sql: Option[String]
  )

  object SqliteMaster {
    def rowParser(idx: Int): RowParser[SqliteMaster] = RowParser[SqliteMaster] { row =>
      Success(
        SqliteMaster(
          objectType = row(idx + 0)(Column.columnToString),
          name = row(idx + 1)(Column.columnToString),
          sql = row(idx + 2)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** One row from `PRAGMA table_info(name)`. */
  case class SqliteColumn(
      tableName: String,
      cid: Int,
      columnName: String,
      declaredType: String,
      notNull: Boolean,
      defaultValue: Option[String],
      pkOrdinal: Int // 0 = not PK; >0 = position in composite PK
  )

  /** One row from `PRAGMA foreign_key_list(name)`. */
  case class SqliteForeignKey(
      tableName: String,
      id: Int, // groups multi-column FKs together
      seq: Int, // ordinal within the FK
      refTable: String,
      fromColumn: String,
      toColumn: Option[String] // null when the FK targets the PK without explicit column listing
  )

  /** Unique-index column from `pragma_index_list` + `pragma_index_info`.
    *
    * `origin` distinguishes:
    *   - "pk" — the primary-key index (we already have PK info from PRAGMA table_info)
    *   - "u" — UNIQUE constraint
    *   - "c" — explicit CREATE UNIQUE INDEX
    */
  case class SqliteUniqueIndex(
      tableName: String,
      indexName: String,
      origin: String,
      seqno: Int,
      columnName: String
  )

  case class AnalyzedView(
      row: SqliteMaster,
      decomposedSql: DecomposedSql,
      jdbcMetadata: SqliteJdbcMetadata
  )

  case class Input(
      tables: List[SqliteMaster],
      columns: List[SqliteColumn],
      foreignKeys: List[SqliteForeignKey],
      uniqueIndexes: List[SqliteUniqueIndex],
      analyzedViews: Map[db.RelationName, AnalyzedView]
  )

  object Input {
    def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector)(implicit ec: ExecutionContext): Future[Input] = {
      for {
        master <- logger.timed("fetching sqlite_master")(ds.run(implicit c => fetchMaster(c)))
        tableNames = master.filter(m => m.objectType == "table" || m.objectType == "view").map(_.name)
        columns <- logger.timed("fetching columns")(ds.run(implicit c => fetchColumns(c, tableNames)))
        foreignKeys <- logger.timed("fetching foreign keys")(ds.run(implicit c => fetchForeignKeys(c, tableNames)))
        uniqueIndexes <- logger.timed("fetching unique indexes")(ds.run(implicit c => fetchUniqueIndexes(c, tableNames)))
        analyzedViews <- logger.timed("analysing views")(analyzeViews(ds, master, viewSelector))
      } yield Input(
        tables = master.filter(_.objectType == "table"),
        columns = columns,
        foreignKeys = foreignKeys,
        uniqueIndexes = uniqueIndexes,
        analyzedViews = analyzedViews
      )
    }

    private def analyzeViews(ds: TypoDataSource, master: List[SqliteMaster], viewSelector: Selector)(implicit ec: ExecutionContext): Future[Map[db.RelationName, AnalyzedView]] = {
      ds.run { implicit c =>
        master
          .filter(_.objectType == "view")
          .flatMap { row =>
            val name = db.RelationName(None, row.name)
            if (row.sql.isDefined && viewSelector.include(name)) {
              val sqlContent = row.sql.get
              val decomposed = DecomposedSql.parse(sqlContent)
              val jdbcMetadata = SqliteJdbcMetadata.from(s"SELECT * FROM ${quoteIdent(row.name)}") match {
                case Right(meta) => meta
                case Left(_)     => SqliteJdbcMetadata(MaybeReturnsRows.Update)
              }
              Some(name -> AnalyzedView(row, decomposed, jdbcMetadata))
            } else None
          }
          .toMap
      }
    }

    private def quoteIdent(name: String): String = s""""${name.replace("\"", "\"\"")}""""

    private def fetchMaster(implicit conn: Connection): List[SqliteMaster] =
      SQL"""SELECT type, name, sql
            FROM sqlite_master
            WHERE type IN ('table', 'view')
              AND name NOT LIKE 'sqlite_%'
            ORDER BY type, name"""
        .as(SqliteMaster.rowParser(1).*)

    private def fetchColumns(conn: Connection, tableNames: List[String]): List[SqliteColumn] =
      tableNames.flatMap { t =>
        val sql = s"""SELECT cid, name, type, "notnull", dflt_value, pk
                      FROM pragma_table_info(${quoteLiteral(t)})
                      ORDER BY cid"""
        val rows = executeAll(conn, sql) { rs =>
          SqliteColumn(
            tableName = t,
            cid = rs.getInt("cid"),
            columnName = rs.getString("name"),
            declaredType = Option(rs.getString("type")).getOrElse(""),
            notNull = rs.getInt("notnull") != 0,
            defaultValue = Option(rs.getString("dflt_value")),
            pkOrdinal = rs.getInt("pk")
          )
        }
        rows
      }

    private def fetchForeignKeys(conn: Connection, tableNames: List[String]): List[SqliteForeignKey] =
      tableNames.flatMap { t =>
        val sql = s"""SELECT id, seq, "table", "from", "to"
                      FROM pragma_foreign_key_list(${quoteLiteral(t)})
                      ORDER BY id, seq"""
        executeAll(conn, sql) { rs =>
          SqliteForeignKey(
            tableName = t,
            id = rs.getInt("id"),
            seq = rs.getInt("seq"),
            refTable = rs.getString("table"),
            fromColumn = rs.getString("from"),
            toColumn = Option(rs.getString("to"))
          )
        }
      }

    private def fetchUniqueIndexes(conn: Connection, tableNames: List[String]): List[SqliteUniqueIndex] = {
      case class IdxInfo(name: String, origin: String)

      tableNames.flatMap { t =>
        val listSql = s"""SELECT name, origin
                          FROM pragma_index_list(${quoteLiteral(t)})
                          WHERE "unique" = 1
                          ORDER BY seq"""
        val idxRows = executeAll(conn, listSql) { rs =>
          IdxInfo(rs.getString("name"), rs.getString("origin"))
        }

        idxRows.flatMap { idx =>
          val infoSql = s"""SELECT seqno, name
                            FROM pragma_index_info(${quoteLiteral(idx.name)})
                            ORDER BY seqno"""
          executeAll(conn, infoSql) { rs =>
            SqliteUniqueIndex(
              tableName = t,
              indexName = idx.name,
              origin = idx.origin,
              seqno = rs.getInt("seqno"),
              columnName = rs.getString("name")
            )
          }
        }
      }
    }

    private def executeAll[T](conn: Connection, sql: String)(read: java.sql.ResultSet => T): List[T] = {
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(sql)
        try {
          val out = List.newBuilder[T]
          while (rs.next()) out += read(rs)
          out.result()
        } finally rs.close()
      } finally stmt.close()
    }

    private def quoteLiteral(s: String): String = "'" + s.replace("'", "''") + "'"
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    // SQLite has no schemas; schemaMode is ignored.
    val _ = schemaMode
    Input.fromDb(logger, ds, viewSelector).map(fromInput)
  }

  def fromInput(input: Input): MetaDb = {
    val typeMapper = SqliteTypeMapperDb()

    val columnsByTable: Map[db.RelationName, List[SqliteColumn]] =
      input.columns.groupBy(c => db.RelationName(None, c.tableName))

    val primaryKeys: Map[db.RelationName, db.PrimaryKey] =
      input.columns
        .filter(_.pkOrdinal > 0)
        .groupBy(c => db.RelationName(None, c.tableName))
        .flatMap { case (rel, cols) =>
          NonEmptyList
            .fromList(cols.sortBy(_.pkOrdinal).map(c => db.ColName(c.columnName)))
            .map(nel => rel -> db.PrimaryKey(nel, db.RelationName(None, rel.name + "_pkey")))
        }

    val uniqueKeys: Map[db.RelationName, List[db.UniqueKey]] =
      input.uniqueIndexes
        .filter(u => u.origin == "u" || u.origin == "c") // skip "pk" — covered above
        .groupBy(u => (db.RelationName(None, u.tableName), u.indexName))
        .toList
        .flatMap { case ((rel, idxName), cols) =>
          NonEmptyList
            .fromList(cols.sortBy(_.seqno).map(c => db.ColName(c.columnName)))
            .map(nel => rel -> db.UniqueKey(nel, db.RelationName(None, idxName)))
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2) }

    val foreignKeys: Map[db.RelationName, List[db.ForeignKey]] =
      input.foreignKeys
        .groupBy(fk => (db.RelationName(None, fk.tableName), fk.id))
        .toList
        .flatMap { case ((rel, fkId), rows) =>
          val refTable = db.RelationName(None, rows.head.refTable)
          val sorted = rows.sortBy(_.seq)
          val fromCols = NonEmptyList.fromList(sorted.map(r => db.ColName(r.fromColumn)))
          val toCols = NonEmptyList.fromList(
            sorted
              .flatMap(r =>
                r.toColumn.orElse {
                  // Implicit FK to PK — pick the PK columns from primaryKeys, in order
                  primaryKeys.get(refTable).map(pk => pk.colNames.toList(r.seq).value)
                }
              )
              .map(db.ColName.apply)
          )
          for {
            fc <- fromCols
            tc <- toCols
          } yield rel -> db.ForeignKey(fc, refTable, tc, db.RelationName(None, s"${rel.name}_fk_$fkId"))
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2) }

    def colToDbCol(c: SqliteColumn): db.Col = {
      val parsedName = ParsedName.of(c.columnName)
      val nullability =
        if (c.pkOrdinal > 0) Nullability.NoNulls
        else if (c.notNull) Nullability.NoNulls
        else Nullability.Nullable

      val parsedType = typeMapper.parseType(c.declaredType, characterMaximumLength = None) { () => () }

      db.Col(
        parsedName = parsedName,
        tpe = parsedType,
        udtName = Some(c.declaredType),
        nullability = nullability,
        columnDefault = c.defaultValue,
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson(Map("declaredType" -> c.declaredType))
      )
    }

    val tables: Map[db.RelationName, Lazy[db.Relation]] =
      input.tables.flatMap { tbl =>
        val rel = db.RelationName(None, tbl.name)
        NonEmptyList.fromList(columnsByTable.getOrElse(rel, Nil).sortBy(_.cid)).map { cols =>
          val lazyT: Lazy[db.Relation] = Lazy {
            db.Table(
              name = rel,
              comment = None,
              cols = cols.map(colToDbCol),
              primaryKey = primaryKeys.get(rel),
              uniqueKeys = uniqueKeys.getOrElse(rel, Nil),
              foreignKeys = foreignKeys.getOrElse(rel, Nil)
            )
          }
          rel -> lazyT
        }
      }.toMap

    val views: Map[db.RelationName, Lazy[db.Relation]] =
      input.analyzedViews.flatMap { case (rel, analyzed) =>
        NonEmptyList.fromList(columnsByTable.getOrElse(rel, Nil).sortBy(_.cid)).map { cols =>
          val lazyV: Lazy[db.Relation] = Lazy {
            val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
              analyzed.jdbcMetadata.columns match {
                case MaybeReturnsRows.Query(metadataCols) =>
                  metadataCols.toList.flatMap { col =>
                    col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))
                  }.toMap
                case MaybeReturnsRows.Update => Map.empty
              }

            val viewCols: NonEmptyList[(db.Col, ParsedName)] = cols.map { c =>
              val parsed = ParsedName.of(c.columnName)
              (colToDbCol(c), parsed)
            }

            db.View(
              name = rel,
              comment = None,
              decomposedSql = analyzed.decomposedSql,
              cols = viewCols,
              deps = deps,
              isMaterialized = false
            )
          }
          rel -> lazyV
        }
      }

    MetaDb(
      dbType = DbType.SQLite,
      relations = tables ++ views,
      enums = Nil,
      domains = Nil
    )
  }
}
