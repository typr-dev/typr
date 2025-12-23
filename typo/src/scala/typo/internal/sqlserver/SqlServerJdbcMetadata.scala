package typo
package internal
package sqlserver

import typo.internal.analysis.*

import java.sql.{Connection, PreparedStatement, ResultSetMetaData}

/** SQL Server-specific JDBC metadata extraction for views and queries */
case class SqlServerJdbcMetadata(columns: MaybeReturnsRows[NonEmptyList[MetadataColumn]])

object SqlServerJdbcMetadata {

  def from(sql: String)(implicit c: Connection): Either[String, SqlServerJdbcMetadata] = {
    val ps = c.prepareStatement(sql)
    try from(ps)
    finally ps.close()
  }

  def from(ps: PreparedStatement): Either[String, SqlServerJdbcMetadata] = {
    def nonEmpty(str: String): Option[String] = if (str == null || str.isEmpty) None else Some(str)

    Option(ps.getMetaData) match {
      case None =>
        Right(SqlServerJdbcMetadata(MaybeReturnsRows.Update))

      case Some(metadata: ResultSetMetaData) =>
        val cols = 0
          .until(metadata.getColumnCount)
          .map(_ + 1)
          .map { n =>
            // SQL Server JDBC driver provides base table/column info via standard JDBC methods
            // getTableName returns the original table name (for views, this is the underlying table)
            // getColumnName returns the original column name
            val baseTableName = nonEmpty(metadata.getTableName(n))
            val baseSchemaName = nonEmpty(metadata.getSchemaName(n))
            val baseColumnName = nonEmpty(metadata.getColumnName(n))

            // The column label is the alias (AS name) if present, otherwise the column name
            val columnLabel = metadata.getColumnLabel(n)

            MetadataColumn(
              baseColumnName = baseColumnName.map(db.ColName.apply),
              baseRelationName = baseTableName.map(name => db.RelationName(baseSchemaName, name)),
              catalogName = nonEmpty(metadata.getCatalogName(n)),
              columnClassName = metadata.getColumnClassName(n),
              columnDisplaySize = metadata.getColumnDisplaySize(n),
              parsedColumnName = ParsedName.of(columnLabel),
              columnName = db.ColName(columnLabel),
              columnType = JdbcType.fromInt(metadata.getColumnType(n)),
              columnTypeName = metadata.getColumnTypeName(n),
              format = 0, // SQL Server doesn't have this PostgreSQL-specific field
              isAutoIncrement = metadata.isAutoIncrement(n),
              isCaseSensitive = metadata.isCaseSensitive(n),
              isCurrency = metadata.isCurrency(n),
              isDefinitelyWritable = metadata.isDefinitelyWritable(n),
              isNullable = ColumnNullable.fromInt(metadata.isNullable(n)).getOrElse {
                sys.error(s"Couldn't understand metadata.isNullable($n) = ${metadata.isNullable(n)}")
              },
              isReadOnly = metadata.isReadOnly(n),
              isSearchable = metadata.isSearchable(n),
              isSigned = metadata.isSigned(n),
              isWritable = metadata.isWritable(n),
              precision = metadata.getPrecision(n),
              scale = metadata.getScale(n),
              schemaName = baseSchemaName,
              tableName = baseTableName
            )
          }

        NonEmptyList.fromList(cols.toList) match {
          case Some(cols) =>
            Right(SqlServerJdbcMetadata(MaybeReturnsRows.Query(cols)))
          case None =>
            Left(s"found no columns for query")
        }
    }
  }
}
