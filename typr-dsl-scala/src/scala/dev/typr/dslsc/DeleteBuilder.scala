package dev.typr.dslsc

import dev.typr.foundations.Fragment
import dev.typr.foundations.Connection
import dev.typr.{dsl}
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

class DeleteBuilder[Fields, Row](private val javaBuilder: dsl.DeleteBuilder[Fields, Row]) {

  def where(predicate: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(javaBuilder.where((fields: Fields) => SqlExpr.toJavaBool(predicate(fields))))
  }

  def execute(using connection: Connection): Int = {
    javaBuilder.execute(connection)
  }

  def executeReturning(parser: dev.typr.foundationssc.ResultSetParser[List[Row]])(using connection: Connection): List[Row] = {
    sql() match {
      case Some(fragment) =>
        // SQL implementation - run via foundations Operation API. The wrapper parser already
        // produces a Scala List[Row]; the DSL's Java executeReturning expects a parser yielding
        // java.util.List<Row>, so go through Fragment.updateReturning directly.
        fragment.append(Fragment.of(" RETURNING *")).updateReturning(parser.underlying).run(connection)
      case None =>
        // Mock implementation - parser is ignored, just convert result
        javaBuilder.executeReturning(connection, null).asScala.toList
    }
  }

  def sql(): Option[Fragment] = {
    javaBuilder.sql().toScala
  }
}

object DeleteBuilder {
  def of[Fields, Row](
      tableName: String,
      structure: RelationStructure[Fields, Row],
      dialect: dsl.Dialect
  ): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(dsl.DeleteBuilder.of(tableName, structure, dialect))
  }
}
