package typo

import typo.internal.{Lazy, TypeMapperDb}
import typo.internal.mariadb.{MariaMetaDb, MariaTypeMapperDb}
import typo.internal.pg.{PgMetaDb, PgTypeMapperDb}

import scala.concurrent.{ExecutionContext, Future}

case class MetaDb(
    dbType: DbType,
    relations: Map[db.RelationName, Lazy[db.Relation]],
    enums: List[db.StringEnum],
    domains: List[db.Domain]
) {
  val typeMapperDb: TypeMapperDb = dbType match {
    case DbType.PostgreSQL             => PgTypeMapperDb(enums, domains)
    case DbType.MariaDB | DbType.MySQL => MariaTypeMapperDb()
  }
}

object MetaDb {
  // Re-export PostgreSQL-specific types for backwards compatibility
  type Input = PgMetaDb.Input
  val Input = PgMetaDb.Input

  type AnalyzedView = PgMetaDb.AnalyzedView
  val AnalyzedView = PgMetaDb.AnalyzedView

  /** Load metadata from database, dispatching to the appropriate implementation based on database type */
  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] =
    ds.dbType match {
      case DbType.PostgreSQL => PgMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.MariaDB    => MariaMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.MySQL      => MariaMetaDb.fromDb(logger, ds, viewSelector, schemaMode) // Use MariaDB implementation for MySQL
    }

  /** Load metadata from PostgreSQL-specific input (for backwards compatibility) */
  def fromInput(logger: TypoLogger, input: Input): MetaDb =
    PgMetaDb.fromInput(logger, input)
}
