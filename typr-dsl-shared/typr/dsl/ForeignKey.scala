package typr.dsl

case class ForeignKey[Fields2, Row2](
    name: String,
    columnPairs: List[ForeignKey.ColumnPair[?, Fields2]]
) {

  def withColumnPair[T](
      field: SqlExpr.FieldLike[T, ?],
      thatField: Fields2 => SqlExpr.FieldLike[T, Row2]
  ): ForeignKey[Fields2, Row2] =
    new ForeignKey[Fields2, Row2](
      name,
      columnPairs :+ ForeignKey.ColumnPair[T, Fields2](field, f => thatField(f))
    )
}

object ForeignKey {
  def of[Fields2, Row2](name: String): ForeignKey[Fields2, Row2] =
    ForeignKey[Fields2, Row2](name, Nil)

  case class ColumnPair[T, Fields2](
      thisField: SqlExpr[T],
      thatField: Fields2 => SqlExpr[T]
  )
}
