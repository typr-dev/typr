package typo
package internal
package pg

import anorm.*

import scala.concurrent.{ExecutionContext, Future}

sealed trait OpenEnum {
  val values: NonEmptyList[String]
}

object OpenEnum {
  case class Text(values: NonEmptyList[String]) extends OpenEnum
  case class TextDomain(domainRef: db.PgType.DomainRef, values: NonEmptyList[String]) extends OpenEnum

  def find(
      ds: TypoDataSource,
      logger: TypoLogger,
      // optimization to not necessarily evaluate all relations
      viewSelector: Selector,
      openEnumSelector: Selector,
      metaDb: MetaDb
  )(implicit ec: ExecutionContext): Future[Map[db.RelationName, OpenEnum]] =
    Future
      .sequence {
        def fetch(dbTable: db.Table, unaryPkCol: db.Col): Future[Option[(db.RelationName, NonEmptyList[String])]] =
          ds.run { implicit c =>
            logger.info(s"Fetching enum values for ${dbTable.name.value}")
            val values = SQL"""select "#${unaryPkCol.name.value}"::text from #${dbTable.name.quotedValue}""".as(SqlParser.str(1).*)
            NonEmptyList.fromList(values.sorted) match {
              case Some(nonEmptyValues) =>
                Some((dbTable.name, nonEmptyValues))
              case None =>
                logger.warn(s"Table ${dbTable.name.value} has no values for enum column ${unaryPkCol.name.value}")
                None
            }
          }

        for {
          tuple <- metaDb.relations
          (name, lazyRelation) = tuple
          if viewSelector.include(name) && openEnumSelector.include(name)
          dbTable <- lazyRelation.get.collect { case dbTable: db.Table => dbTable }
          unaryPkCol <- dbTable.primaryKey.collect { case db.PrimaryKey(NonEmptyList(head, Nil), _) => dbTable.cols.find(_.name == head) }.flatten
        } yield {
          unaryPkCol.tpe match {
            case db.PgType.Text | db.PgType.VarChar(_) =>
              fetch(dbTable, unaryPkCol).map(_.map { case (name, values) => (name, OpenEnum.Text(values)) })
            case domainRef @ db.PgType.DomainRef(_, _, db.PgType.Text | db.PgType.VarChar(_)) =>
              fetch(dbTable, unaryPkCol).map(_.map { case (name, values) => (name, OpenEnum.TextDomain(domainRef, values)) })
            case _ => Future.successful(None)
          }
        }
      }
      .map(_.flatten.toMap)

}
