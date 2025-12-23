package typr

import anorm.{ResultSetResource, Row, SimpleSql, StatementResource}
import resource.managed

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.reflect.{ClassManifestFactory, ClassTag}

package object anormruntime {
  private val CTPS: ClassTag[PreparedStatement] = ClassManifestFactory.fromClass(classOf[PreparedStatement])
  private val CTRS: ClassTag[ResultSet] = ClassManifestFactory.fromClass(classOf[ResultSet])

  def updateReturning[A](simpleSql: SimpleSql[Row], generatedKeysParser: ResultSetParser[A])(implicit c: Connection): A =
    managed(simpleSql.unsafeStatement(c, getGeneratedKeys = true))(using StatementResource, CTPS)
      .flatMap { stmt =>
        stmt.executeUpdate()
        managed(stmt.getGeneratedKeys)(ResultSetResource, CTRS)
      }
      .acquireAndGet(generatedKeysParser.apply)

  def query[T](simpleSql: SimpleSql[Row], parser: ResultSetParser[T])(implicit c: Connection): T =
    managed(simpleSql.unsafeStatement(c, getGeneratedKeys = false))(using StatementResource, CTPS)
      .flatMap(stmt => managed(stmt.executeQuery())(using ResultSetResource, CTRS))
      .acquireAndGet(parser.apply)
}
