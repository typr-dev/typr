package typo.dsl

case class DeleteParams[Fields](where: List[Fields => SqlExpr[Boolean, Required]], limit: Option[Int]) {
  def where(v: Fields => SqlExpr[Boolean, Required]): DeleteParams[Fields] =
    copy(where = where :+ v)
  def limit(v: Int): DeleteParams[Fields] = copy(limit = Some(v))
}

object DeleteParams {
  def empty[Fields]: DeleteParams[Fields] =
    DeleteParams[Fields](List.empty, None)
}
