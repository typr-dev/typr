package typr.scaladsl

import typr.dsl.{DeleteBuilder => JavaDeleteBuilder}
import typr.runtime.Fragment
import java.sql.Connection
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Using

class DeleteBuilder[Fields, Row](private val javaBuilder: JavaDeleteBuilder[Fields, Row]) {

  def where(predicate: Fields => typr.dsl.SqlExpr[Boolean]): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(javaBuilder.where((fields: Fields) => predicate(fields).underlying(Bijections.scalaBooleanToJavaBoolean)))
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
      dialect: typr.dsl.Dialect
  ): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(JavaDeleteBuilder.of(tableName, structure, dialect))
  }
}
