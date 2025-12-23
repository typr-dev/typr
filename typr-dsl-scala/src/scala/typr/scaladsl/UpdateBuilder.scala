package typr.scaladsl

import typr.dsl.{UpdateBuilder as JavaUpdateBuilder}
import typr.runtime.{Fragment, PgType}

import java.sql.Connection
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

class UpdateBuilder[Fields, Row] private[scaladsl] (
    private val javaBuilder: JavaUpdateBuilder[Fields, Row]
) {

  private def copy(newJavaBuilder: JavaUpdateBuilder[Fields, Row]): UpdateBuilder[Fields, Row] =
    new UpdateBuilder(newJavaBuilder)

  def set[T](field: Fields => SqlExpr.FieldLike[T, Row], value: T, pgType: PgType[T]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.set((fields: Fields) => field(fields).underlying, value, pgType))
  }

  def setValue[T](field: Fields => SqlExpr.FieldLike[T, Row], value: T): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.setValue((fields: Fields) => field(fields).underlying, value))
  }

  def setExpr[T](field: Fields => SqlExpr.FieldLike[T, Row], expr: typr.dsl.SqlExpr[T]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.setExpr((fields: Fields) => field(fields).underlying, expr))
  }

  def setComputedValue[T](
      field: Fields => SqlExpr.FieldLike[T, Row],
      compute: SqlExpr.FieldLike[T, Row] => typr.dsl.SqlExpr[T]
  ): UpdateBuilder[Fields, Row] = {
    copy(
      javaBuilder.setComputedValue(
        (fields: Fields) => field(fields).underlying,
        (javaFieldLike: typr.dsl.SqlExpr.FieldLike[T, Row]) => {
          val scalaFieldLike = new GenericFieldLikeWrapper(javaFieldLike)
          compute(scalaFieldLike)
        }
      )
    )
  }

  private class GenericFieldLikeWrapper[T](override val underlying: typr.dsl.SqlExpr.FieldLike[T, Row]) extends SqlExpr.FieldLike[T, Row]

  def where(predicate: Fields => typr.dsl.SqlExpr[Boolean]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.where((fields: Fields) => predicate(fields).underlying(Bijections.scalaBooleanToJavaBoolean)))
  }

  def execute(using connection: Connection): Int = {
    javaBuilder.execute(connection)
  }

  def executeReturning(using connection: Connection): List[Row] = {
    javaBuilder.executeReturning(connection).asScala.toList
  }

  def sql(): Option[Fragment] = {
    javaBuilder.sql().toScala
  }
}

object UpdateBuilder {
  def apply[Fields, Row](javaBuilder: JavaUpdateBuilder[Fields, Row]): UpdateBuilder[Fields, Row] =
    new UpdateBuilder(javaBuilder)

  def of[Fields, Row](
      tableName: String,
      structure: RelationStructure[Fields, Row],
      rowParser: RowParser[Row],
      dialect: typr.dsl.Dialect
  ): UpdateBuilder[Fields, Row] = {
    new UpdateBuilder(JavaUpdateBuilder.of(tableName, structure, rowParser.underlying, dialect))
  }
}
