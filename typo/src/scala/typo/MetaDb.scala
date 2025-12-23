package typo

import typo.internal.duckdb.{DuckDbMetaDb, DuckDbTypeMapperDb}
import typo.internal.external.ExternalTools
import typo.internal.mariadb.{MariaMetaDb, MariaTypeMapperDb}
import typo.internal.oracle.{OracleMetaDb, OracleTypeMapperDb}
import typo.internal.pg.{PgMetaDb, PgTypeMapperDb}
import typo.internal.sqlserver.{SqlServerMetaDb, SqlServerTypeMapperDb}
import typo.internal.{Lazy, TypeMapperDb}

import scala.concurrent.{ExecutionContext, Future}

case class MetaDb(
    dbType: DbType,
    relations: Map[db.RelationName, Lazy[db.Relation]],
    enums: List[db.StringEnum],
    domains: List[db.Domain],
    oracleObjectTypes: Map[String, db.OracleType.ObjectType] = Map.empty,
    oracleCollectionTypes: Map[String, db.OracleType] = Map.empty
) {
  val typeMapperDb: TypeMapperDb = dbType match {
    case DbType.PostgreSQL => PgTypeMapperDb(enums, domains)
    case DbType.MariaDB    => MariaTypeMapperDb()
    case DbType.DuckDB     => DuckDbTypeMapperDb()
    case DbType.Oracle     => OracleTypeMapperDb(oracleObjectTypes, oracleCollectionTypes)
    case DbType.SqlServer  => SqlServerTypeMapperDb(domains)
  }
}

object MetaDb {
  // Re-export PostgreSQL-specific types for backwards compatibility
  type Input = PgMetaDb.Input
  val Input = PgMetaDb.Input

  type AnalyzedView = PgMetaDb.AnalyzedView
  val AnalyzedView = PgMetaDb.AnalyzedView

  /** Load metadata from database, dispatching to the appropriate implementation based on database type */
  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode, externalTools: ExternalTools)(implicit ec: ExecutionContext): Future[MetaDb] =
    ds.dbType match {
      case DbType.PostgreSQL => PgMetaDb.fromDb(logger, ds, viewSelector, schemaMode, externalTools)
      case DbType.MariaDB    => MariaMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.DuckDB     => DuckDbMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.Oracle     => OracleMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.SqlServer  => SqlServerMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
    }

  /** Load metadata from PostgreSQL-specific input (for backwards compatibility) */
  def fromInput(logger: TypoLogger, input: Input): MetaDb =
    PgMetaDb.fromInput(logger, input)
}
