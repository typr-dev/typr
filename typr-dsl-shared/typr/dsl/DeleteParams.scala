package typr.dsl

case class DeleteParams[Fields](where: List[Fields => SqlExpr[Boolean]]) {
  def where(v: Fields => SqlExpr[Boolean]): DeleteParams[Fields] =
    copy(where = where :+ v)
}

object DeleteParams {
  def empty[Fields]: DeleteParams[Fields] =
    DeleteParams[Fields](List.empty)
}
