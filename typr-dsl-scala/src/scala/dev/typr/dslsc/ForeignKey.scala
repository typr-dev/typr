package dev.typr.dslsc

import dev.typr.{dsl}

/** Scala wrapper for ForeignKey */
class ForeignKey[Fields, Row](val underlying: dsl.ForeignKey[Fields, Row]) {
  def withColumnPair[T](
      thisField: SqlExpr.FieldLike[T, ?],
      otherGetter: Fields => SqlExpr.FieldLike[T, Row]
  ): ForeignKey[Fields, Row] = {
    new ForeignKey(
      underlying.withColumnPair(
        thisField.underlying,
        (fields: Fields) => otherGetter(fields).underlying
      )
    )
  }
}

object ForeignKey {
  def of[Fields, Row](constraintName: String): ForeignKey[Fields, Row] =
    new ForeignKey(dsl.ForeignKey.of(constraintName))
}
