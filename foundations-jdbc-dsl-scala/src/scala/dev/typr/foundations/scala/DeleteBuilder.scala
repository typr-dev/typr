package dev.typr.foundations.scala

import dev.typr.foundations.Fragment
import dev.typr.foundations.dsl

import java.sql.Connection
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*
import scala.util.Using

class DeleteBuilder[Fields, Row](private val javaBuilder: dsl.DeleteBuilder[Fields, Row]) {

  def where(predicate: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(javaBuilder.where((fields: Fields) => SqlExpr.toJavaBool(predicate(fields))))
  }

  def execute(using connection: Connection): Int = {
    javaBuilder.execute(connection)
  }

  def executeReturning(parser: ResultSetParser[List[Row]])(using connection: Connection): List[Row] = {
    sql() match {
      case Some(fragment) =>
        // SQL implementation - execute directly with Scala parser
        val query = fragment.append(Fragment.lit(" RETURNING *"))
        Using.resource(connection.prepareStatement(query.render())) { ps =>
          query.set(ps)
          Using.resource(ps.executeQuery()) { rs =>
            parser(rs)
          }
        }
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
