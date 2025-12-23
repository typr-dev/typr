package typr.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Common interface for reading columns from a {@link ResultSet}. Implemented by both PgRead
 * (PostgreSQL) and MariaRead (MariaDB).
 */
public interface DbRead<A> {
  A read(ResultSet rs, int col) throws SQLException;

  <B> DbRead<B> map(SqlFunction<A, B> f);

  /** Derive a DbRead which allows nullable values */
  DbRead<Optional<A>> opt();

  /**
   * Marker interface for nullable read implementations. Used by RowParser.opt() to detect which
   * columns are already nullable.
   */
  interface Nullable {}
}
