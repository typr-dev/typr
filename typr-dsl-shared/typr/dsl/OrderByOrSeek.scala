package typr.dsl

sealed trait OrderByOrSeek[Fields, T] {
  val f: Fields => SortOrder[T]
}

object OrderByOrSeek {
  case class OrderBy[Fields, T](f: Fields => SortOrder[T]) extends OrderByOrSeek[Fields, T]
  sealed trait SeekNoHkt[Fields, T] extends OrderByOrSeek[Fields, T]
  case class Seek[Fields, T](f: Fields => SortOrder[T], value: SqlExpr.Const[T]) extends SeekNoHkt[Fields, T]

  def expand[Fields, Row](fields: Fields, params: SelectParams[Fields, Row]): (List[SqlExpr[Boolean]], List[SortOrder[?]]) = {
    val seeks: List[SeekNoHkt[Fields, ?]] =
      params.orderBy.collect { case x: OrderByOrSeek.SeekNoHkt[Fields, nt] => x }

    val maybeSeekPredicate: Option[SqlExpr[Boolean]] =
      seeks match {
        case Nil => None
        case nonEmpty =>
          val seekOrderBys: List[SortOrder[?]] =
            nonEmpty.map { case seek: Seek[Fields, _] @unchecked /* for 2.13*/ => seek.f(fields) }

          seekOrderBys.map(_.ascending).distinct match {
            case List(uniformIsAscending) =>
              val dbTuple = SqlExpr.RowExpr(seekOrderBys.map(so => so.expr))
              val valueTuple = SqlExpr.RowExpr(nonEmpty.map { case seek: Seek[Fields, t] @unchecked /* for 2.13 */ => seek.value })

              Some(if (uniformIsAscending) dbTuple > valueTuple else dbTuple < valueTuple)
            case _ =>
              val orConditions: Seq[SqlExpr[Boolean]] =
                nonEmpty.indices.map { i =>
                  val equals: List[SqlExpr[Boolean]] =
                    nonEmpty.take(i).map { case seek: Seek[Fields, t] @unchecked /* for 2.13*/ =>
                      val so: SortOrder[t] = seek.f(fields)
                      so.expr === seek.value
                    }

                  nonEmpty(i) match {
                    case seek: Seek[Fields, t] @unchecked /* for 2.13*/ =>
                      val so: SortOrder[t] = seek.f(fields)
                      val predicate: SqlExpr[Boolean] =
                        if (so.ascending) so.expr > seek.value else so.expr < seek.value
                      (equals :+ predicate).reduce(_ and _)
                  }
                }

              Some(orConditions.reduce(_ or _))
          }
      }
    (params.where.map(_.apply(fields)) ++ maybeSeekPredicate, params.orderBy.map(_.f(fields)))
  }
}
