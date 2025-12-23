package typr.anormruntime

import java.sql.ResultSet
import scala.collection.IterableFactory

@FunctionalInterface
trait ResultSetParser[R] {
  def apply(resultSet: ResultSet): R
}

object ResultSetParser {
  def row[Row](rowParser: RowParser[Row], rs: ResultSet): Row =
    rowParser.decode(rowParser.colsWithIndex.map { case (tpe, i) => tpe.read.read(rs, i + 1) })

  class Many[Row, I[r] <: Iterable[r]](rowParser: RowParser[Row], factory: IterableFactory[I]) extends ResultSetParser[I[Row]] {
    override def apply(resultSet: ResultSet): I[Row] = {
      val b = factory.newBuilder[Row]
      while (resultSet.next()) b += row(rowParser, resultSet)
      b.result()
    }
  }

  class MaybeOne[Row](rowParser: RowParser[Row]) extends ResultSetParser[Option[Row]] {
    override def apply(resultSet: ResultSet): Option[Row] =
      if (resultSet.next()) Some(row(rowParser, resultSet)) else None
  }

  class MaybeOneStrict[Row](rowParser: RowParser[Row]) extends ResultSetParser[Option[Either[String, Row]]] {
    override def apply(resultSet: ResultSet): Option[Either[String, Row]] =
      if (resultSet.next()) {
        val ret = row(rowParser, resultSet)
        if (resultSet.next()) Some(Left("Expected single row, but found more"))
        else Some(Right(ret))
      } else None
  }

  class ExactlyOne[Row, R](rowParser: RowParser[Row]) extends ResultSetParser[Either[String, Row]] {
    override def apply(resultSet: ResultSet): Either[String, Row] =
      if (resultSet.next()) {
        val ret = row(rowParser, resultSet)
        if (resultSet.next()) Left("Expected single row, but found more") else Right(ret)
      } else Left("No rows when expecting a single one")
  }
}
