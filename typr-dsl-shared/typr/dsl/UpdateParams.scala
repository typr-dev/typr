package typr.dsl

case class UpdateParams[Fields, Row](
    where: List[Fields => SqlExpr[Boolean]],
    setters: List[UpdateParams.Setter[Fields, ?, Row]]
) {
  def where(v: Fields => SqlExpr[Boolean]): UpdateParams[Fields, Row] =
    copy(where = where :+ v)

  def set[T](col: Fields => SqlExpr.FieldLikeNotId[T, Row], value: Fields => SqlExpr[T]): UpdateParams[Fields, Row] =
    copy(setters = setters :+ UpdateParams.Setter(col, value))
}

object UpdateParams {
  case class Setter[Fields, T, Row](col: Fields => SqlExpr.FieldLikeNotId[T, Row], value: Fields => SqlExpr[T])

  def empty[Fields, Row]: UpdateParams[Fields, Row] =
    UpdateParams[Fields, Row](List.empty, List.empty)
}
