package typr
package internal
package duckdb

import typr.internal.analysis.*

import java.sql.{Connection, PreparedStatement, ResultSetMetaData}

/** DuckDB-specific JDBC metadata extraction for views and queries */
case class DuckDbJdbcMetadata(columns: MaybeReturnsRows[NonEmptyList[MetadataColumn]])

object DuckDbJdbcMetadata {

  def from(sql: String)(implicit c: Connection): Either[String, DuckDbJdbcMetadata] = {
    val ps = c.prepareStatement(sql)
    try from(ps)
    finally ps.close()
  }

  def from(ps: PreparedStatement): Either[String, DuckDbJdbcMetadata] = {
    def nonEmpty(str: String): Option[String] = if (str == null || str.isEmpty) None else Some(str)

    Option(ps.getMetaData) match {
      case None =>
        Right(DuckDbJdbcMetadata(MaybeReturnsRows.Update))

      case Some(metadata: ResultSetMetaData) =>
        val cols = 0
          .until(metadata.getColumnCount)
          .map(_ + 1)
          .map { n =>
            val baseTableName = nonEmpty(metadata.getTableName(n))
            val baseSchemaName = nonEmpty(metadata.getSchemaName(n))
            val baseColumnName = nonEmpty(metadata.getColumnName(n))

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
              format = 0,
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
            Right(DuckDbJdbcMetadata(MaybeReturnsRows.Query(cols)))
          case None =>
            Left(s"found no columns for query")
        }
    }
  }
}
