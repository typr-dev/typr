package dev.typr.dslsc

import dev.typr.{dsl}
import dev.typr.foundations.{Connection, DbType, Fragment}
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

class UpdateBuilder[Fields, Row] private[dslsc] (
    private val javaBuilder: dsl.UpdateBuilder[Fields, Row]
) {

  private def copy(newJavaBuilder: dsl.UpdateBuilder[Fields, Row]): UpdateBuilder[Fields, Row] =
    new UpdateBuilder(newJavaBuilder)

  def set[T](field: Fields => SqlExpr.FieldLike[T, Row], value: T, dbType: DbType[T]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.set((fields: Fields) => field(fields).underlying, value, dbType))
  }

  def setValue[T](field: Fields => SqlExpr.FieldLike[T, Row], value: T): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.setValue((fields: Fields) => field(fields).underlying, value))
  }

  def setExpr[T](field: Fields => SqlExpr.FieldLike[T, Row], expr: dsl.SqlExpr[T]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.setExpr((fields: Fields) => field(fields).underlying, expr))
  }

  def setComputedValue[T](
      field: Fields => SqlExpr.FieldLike[T, Row],
      compute: SqlExpr.FieldLike[T, Row] => dsl.SqlExpr[T]
  ): UpdateBuilder[Fields, Row] = {
    copy(
      javaBuilder.setComputedValue(
        (fields: Fields) => field(fields).underlying,
        (javaFieldLike: dsl.SqlExpr.FieldLike[T, Row]) => {
          val scalaFieldLike = SqlExpr.FieldLike.wrap(javaFieldLike)
          compute(scalaFieldLike)
        }
      )
    )
  }

  def where(predicate: Fields => SqlExpr[Boolean]): UpdateBuilder[Fields, Row] = {
    copy(javaBuilder.where((fields: Fields) => SqlExpr.toJavaBool(predicate(fields))))
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
  def apply[Fields, Row](javaBuilder: dsl.UpdateBuilder[Fields, Row]): UpdateBuilder[Fields, Row] =
    new UpdateBuilder(javaBuilder)

  def of[Fields, Row](
      tableName: String,
      structure: RelationStructure[Fields, Row],
      rowCodec: dev.typr.foundationssc.RowCodec[Row],
      dialect: dsl.Dialect
  ): UpdateBuilder[Fields, Row] = {
    new UpdateBuilder(dsl.UpdateBuilder.of(tableName, structure, rowCodec.underlying, dialect))
  }
}
