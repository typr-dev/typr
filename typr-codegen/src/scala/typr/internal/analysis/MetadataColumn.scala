package typr
package internal
package analysis

import play.api.libs.json.{Json, Writes}

/** Analyzed from postgres metadata for prepared statements through jdbc
  */
case class MetadataColumn(
    baseColumnName: Option[db.ColName],
    baseRelationName: Option[db.RelationName],
    catalogName: Option[String],
    columnClassName: String,
    columnDisplaySize: Int,
    parsedColumnName: ParsedName,
    columnName: db.ColName,
    columnType: JdbcType,
    columnTypeName: String,
    format: Int,
    isAutoIncrement: Boolean,
    isCaseSensitive: Boolean,
    isCurrency: Boolean,
    isDefinitelyWritable: Boolean,
    isNullable: ColumnNullable,
    isReadOnly: Boolean,
    isSearchable: Boolean,
    isSigned: Boolean,
    isWritable: Boolean,
    precision: Int,
    scale: Int,
    schemaName: Option[String],
    tableName: Option[String],
    isExpression: Boolean = false // True if this is a computed expression (not a direct column reference)
) {
  def name: db.ColName = parsedColumnName.name
}

object MetadataColumn {
  implicit val oformat: Writes[MetadataColumn] = (x: MetadataColumn) =>
    Json.obj(
      "baseColumnName" -> x.baseColumnName.map(_.value),
      "baseRelationName" -> x.baseRelationName.map(_.value),
      "catalogName" -> x.catalogName,
      "columnClassName" -> x.columnClassName,
      "columnDisplaySize" -> x.columnDisplaySize,
      "parsedColumnName" -> Json.toJson(x.parsedColumnName),
      "columnName" -> x.columnName.value,
      "columnType" -> x.columnType.toString,
      "columnTypeName" -> x.columnTypeName,
      "format" -> x.format,
      "isAutoIncrement" -> x.isAutoIncrement,
      "isCaseSensitive" -> x.isCaseSensitive,
      "isCurrency" -> x.isCurrency,
      "isDefinitelyWritable" -> x.isDefinitelyWritable,
      "isNullable" -> x.isNullable.toString,
      "isReadOnly" -> x.isReadOnly,
      "isSearchable" -> x.isSearchable,
      "isSigned" -> x.isSigned,
      "isWritable" -> x.isWritable,
      "precision" -> x.precision,
      "scale" -> x.scale,
      "schemaName" -> x.schemaName,
      "tableName" -> x.tableName
    )
}
