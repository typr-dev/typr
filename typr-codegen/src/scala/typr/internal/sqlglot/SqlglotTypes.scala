package typr
package internal
package sqlglot

import play.api.libs.json._

/** Types for communicating with the sqlglot Python script */

/** Column origin - where a column value comes from */
sealed trait ColumnOrigin
object ColumnOrigin {
  case class Column(table: String, column: String) extends ColumnOrigin
  case class Literal(value: String) extends ColumnOrigin
  case class Function(name: String) extends ColumnOrigin
  case class Expression(expression: String) extends ColumnOrigin

  implicit val reads: Reads[ColumnOrigin] = Reads { json =>
    (json \ "origin_type").asOpt[String] match {
      case Some("column") =>
        for {
          table <- (json \ "table").validate[String]
          column <- (json \ "column").validate[String]
        } yield Column(table, column)
      case Some("literal") =>
        (json \ "value").validate[String].map(Literal.apply)
      case Some("function") =>
        (json \ "function_name").validate[String].map(Function.apply)
      case Some("expression") =>
        (json \ "expression").validate[String].map(Expression.apply)
      case other =>
        JsError(s"Unknown origin_type: $other")
    }
  }
}

/** Information about a result column */
case class SqlglotColumnInfo(
    name: String,
    alias: Option[String],
    inferredType: Option[String],
    nullableFromJoin: Boolean,
    nullableInSchema: Boolean,
    isExpression: Boolean,
    sourceTable: Option[String],
    sourceColumn: Option[String],
    sourceType: Option[String],
    sourcePrimaryKey: Boolean,
    origins: List[ColumnOrigin]
)

object SqlglotColumnInfo {
  implicit val reads: Reads[SqlglotColumnInfo] = Reads { json =>
    for {
      name <- (json \ "name").validate[String]
      alias <- (json \ "alias").validateOpt[String]
      inferredType <- (json \ "inferred_type").validateOpt[String]
      nullableFromJoin <- (json \ "nullable_from_join").validate[Boolean]
      nullableInSchema <- (json \ "nullable_in_schema").validate[Boolean]
      isExpression <- (json \ "is_expression").validate[Boolean]
      sourceTable <- (json \ "source_table").validateOpt[String]
      sourceColumn <- (json \ "source_column").validateOpt[String]
      sourceType <- (json \ "source_type").validateOpt[String]
      sourcePrimaryKey <- (json \ "source_primary_key").validate[Boolean]
      origins <- (json \ "origins").validate[List[ColumnOrigin]]
    } yield SqlglotColumnInfo(
      name = name,
      alias = alias,
      inferredType = inferredType,
      nullableFromJoin = nullableFromJoin,
      nullableInSchema = nullableInSchema,
      isExpression = isExpression,
      sourceTable = sourceTable,
      sourceColumn = sourceColumn,
      sourceType = sourceType,
      sourcePrimaryKey = sourcePrimaryKey,
      origins = origins
    )
  }

  implicit val writes: Writes[SqlglotColumnInfo] = Writes { col =>
    Json.obj(
      "name" -> col.name,
      "alias" -> col.alias,
      "inferred_type" -> col.inferredType,
      "nullable_from_join" -> col.nullableFromJoin,
      "nullable_in_schema" -> col.nullableInSchema,
      "is_expression" -> col.isExpression,
      "source_table" -> col.sourceTable,
      "source_column" -> col.sourceColumn,
      "source_type" -> col.sourceType,
      "source_primary_key" -> col.sourcePrimaryKey
    )
  }
}

/** Information about a query parameter */
case class SqlglotParameterInfo(
    name: String,
    position: Int,
    nullableHint: Boolean,
    inferredType: Option[String],
    sourceTable: Option[String],
    sourceColumn: Option[String],
    context: Option[String]
)

object SqlglotParameterInfo {
  implicit val reads: Reads[SqlglotParameterInfo] = Reads { json =>
    for {
      name <- (json \ "name").validate[String]
      position <- (json \ "position").validate[Int]
      nullableHint <- (json \ "nullable_hint").validate[Boolean]
      inferredType <- (json \ "inferred_type").validateOpt[String]
      sourceTable <- (json \ "source_table").validateOpt[String]
      sourceColumn <- (json \ "source_column").validateOpt[String]
      context <- (json \ "context").validateOpt[String]
    } yield SqlglotParameterInfo(
      name = name,
      position = position,
      nullableHint = nullableHint,
      inferredType = inferredType,
      sourceTable = sourceTable,
      sourceColumn = sourceColumn,
      context = context
    )
  }
}

/** Table reference in a query */
case class SqlglotTableRef(
    name: String,
    alias: Option[String],
    schemaName: Option[String],
    joinType: Option[String]
)

object SqlglotTableRef {
  implicit val reads: Reads[SqlglotTableRef] = Reads { json =>
    for {
      name <- (json \ "name").validate[String]
      alias <- (json \ "alias").validateOpt[String]
      schemaName <- (json \ "schema_name").validateOpt[String]
      joinType <- (json \ "join_type").validateOpt[String]
    } yield SqlglotTableRef(name, alias, schemaName, joinType)
  }
}

/** Result of analyzing a single SQL file */
case class SqlglotFileResult(
    path: String,
    success: Boolean,
    error: Option[String],
    queryType: Option[String],
    tables: List[SqlglotTableRef],
    columns: List[SqlglotColumnInfo],
    parameters: List[SqlglotParameterInfo]
)

object SqlglotFileResult {
  implicit val readsTableRef: Reads[SqlglotTableRef] = SqlglotTableRef.reads
  implicit val readsColumnInfo: Reads[SqlglotColumnInfo] = SqlglotColumnInfo.reads
  implicit val readsParameterInfo: Reads[SqlglotParameterInfo] = SqlglotParameterInfo.reads

  implicit val reads: Reads[SqlglotFileResult] = Reads { json =>
    for {
      path <- (json \ "path").validate[String]
      success <- (json \ "success").validate[Boolean]
      error <- (json \ "error").validateOpt[String]
      queryType <- (json \ "query_type").validateOpt[String]
      tables <- (json \ "tables").validateOpt[List[SqlglotTableRef]].map(_.getOrElse(Nil))
      columns <- (json \ "columns").validateOpt[List[SqlglotColumnInfo]].map(_.getOrElse(Nil))
      parameters <- (json \ "parameters").validateOpt[List[SqlglotParameterInfo]].map(_.getOrElse(Nil))
    } yield SqlglotFileResult(path, success, error, queryType, tables, columns, parameters)
  }
}

/** Output from the sqlglot analyzer */
case class SqlglotAnalysisOutput(results: List[SqlglotFileResult])

object SqlglotAnalysisOutput {
  implicit val reads: Reads[SqlglotAnalysisOutput] = Reads { json =>
    (json \ "results").validate[List[SqlglotFileResult]].map(SqlglotAnalysisOutput.apply)
  }
}

/** Input to the sqlglot analyzer */
case class SqlglotAnalysisInput(
    dialect: String,
    schema: Map[String, Map[String, SqlglotColumnSchema]],
    files: List[SqlglotFileInput]
)

object SqlglotAnalysisInput {
  implicit val writesColumnSchema: Writes[SqlglotColumnSchema] = SqlglotColumnSchema.writes
  implicit val writesFileInput: Writes[SqlglotFileInput] = SqlglotFileInput.writes

  implicit val writes: Writes[SqlglotAnalysisInput] = Writes { input =>
    Json.obj(
      "dialect" -> input.dialect,
      "schema" -> Json.toJson(input.schema),
      "files" -> Json.toJson(input.files)
    )
  }
}

/** Schema information for a column */
case class SqlglotColumnSchema(
    `type`: String,
    nullable: Boolean,
    primaryKey: Boolean
)

object SqlglotColumnSchema {
  implicit val writes: Writes[SqlglotColumnSchema] = Writes { col =>
    Json.obj(
      "type" -> col.`type`,
      "nullable" -> col.nullable,
      "primary_key" -> col.primaryKey
    )
  }
}

/** A SQL file to analyze */
case class SqlglotFileInput(path: String, content: String)

object SqlglotFileInput {
  implicit val writes: Writes[SqlglotFileInput] = Writes { file =>
    Json.obj(
      "path" -> file.path,
      "content" -> file.content
    )
  }
}
