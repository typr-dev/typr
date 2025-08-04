package typo.dsl

import doobie.ConnectionIO
import doobie.free.connection.delay
import doobie.implicits.toSqlInterpolator
import doobie.util.fragment.Fragment

import scala.annotation.nowarn

trait DeleteBuilder[Fields, Row] {
  protected def params: DeleteParams[Fields]
  protected def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row]

  final def where[N[_]: Nullability](v: Fields => SqlExpr[Boolean, N]): DeleteBuilder[Fields, Row] =
    whereStrict(f => v(f).?.coalesce(false))

  final def whereStrict(v: Fields => SqlExpr[Boolean, Required]): DeleteBuilder[Fields, Row] =
    withParams(params.where(v))

  def sql: Option[Fragment]
  def execute: ConnectionIO[Int]

  final def limit(v: Int): DeleteBuilder[Fields, Row] =
    withParams(params.limit(v))
}

object DeleteBuilder {
  def apply[Fields, Row](name: String, structure: Structure.Relation[Fields, Row]): DeleteBuilderSql[Fields, Row] =
    DeleteBuilderSql(name, structure, DeleteParams.empty)

  final case class DeleteBuilderSql[Fields, Row](
      name: String,
      structure: Structure.Relation[Fields, Row],
      params: DeleteParams[Fields]
  ) extends DeleteBuilder[Fields, Row] {
    override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
      copy(params = sqlParams)

    def mkSql(ctx: RenderCtx): Fragment = {
      List[Iterable[Fragment]](
        Some(fr"delete from ${Fragment.const0(name)}"),
        params.where
          .map(w => w(structure.fields))
          .reduceLeftOption(_.and(_))
          .map { where => fr" where " ++ where.render(ctx: RenderCtx) }
      ).flatten.reduce(_ ++ _)
    }

    override def sql: Option[Fragment] =
      Some(mkSql(RenderCtx.Empty))

    override def execute: ConnectionIO[Int] =
      mkSql(RenderCtx.Empty).update.run
  }

  final case class DeleteBuilderMock[Id, Fields, Row](
      params: DeleteParams[Fields],
      structure: Structure[Fields, Row],
      map: scala.collection.mutable.Map[Id, Row]
  ) extends DeleteBuilder[Fields, Row] {
    override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
      copy(params = sqlParams)

    override def sql: Option[Fragment] =
      None

    override def execute: ConnectionIO[Int] = delay {
      var changed = 0
      map.foreach { case (id, row) =>
        if (params.where.forall(w => structure.untypedEval(w(structure.fields), row))) {
          map.remove(id): @nowarn
          changed += 1
        }
      }
      changed
    }
  }
}
