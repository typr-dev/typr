package typo
package internal
package sqlfiles

import typo.internal.external.ExternalTools
import typo.internal.mariadb.MariaSqlFileMetadata
import typo.internal.duckdb.DuckDbSqlFileMetadata

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
        readSqlFileDirectories(logger, scriptsPath, ds)
    }
  }
}
