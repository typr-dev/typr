package typr
package internal
package sqlfiles

import typr.internal.external.ExternalTools
import typr.internal.mariadb.MariaSqlFileMetadata
import typr.internal.duckdb.DuckDbSqlFileMetadata
import typr.internal.oracle.OracleSqlFileMetadata
import typr.internal.sqlserver.SqlServerSqlFileMetadata
import typr.internal.db2.Db2SqlFileMetadata

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

/** Dispatcher for database-specific SQL file reading */
object SqlFileReader {
  def apply(logger: TypoLogger, scriptsPath: Path, ds: TypoDataSource, externalTools: ExternalTools)(implicit ec: ExecutionContext): Future[List[SqlFile]] = {
    ds.dbType match {
      case DbType.PostgreSQL =>
        readSqlFileDirectories(logger, scriptsPath, ds)
      case DbType.MariaDB =>
        MariaSqlFileMetadata(logger, scriptsPath, ds, externalTools)
      case DbType.DuckDB =>
        DuckDbSqlFileMetadata(logger, scriptsPath, ds, externalTools)
      case DbType.Oracle =>
        OracleSqlFileMetadata(logger, scriptsPath, ds, externalTools)
      case DbType.SqlServer =>
        SqlServerSqlFileMetadata(logger, scriptsPath, ds, externalTools)
      case DbType.DB2 =>
        Db2SqlFileMetadata(logger, scriptsPath, ds, externalTools)
    }
  }
}
