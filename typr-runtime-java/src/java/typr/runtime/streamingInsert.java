package typr.runtime;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import org.postgresql.PGConnection;
import org.postgresql.util.PSQLException;

public class streamingInsert {
  public static <T> long insertUnchecked(
      String copyCommand, int batchSize, Iterator<T> rows, Connection c, PgText<T> T) {
    try {
      return insert(copyCommand, batchSize, rows, c, T);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> long insert(
      String copyCommand, int batchSize, Iterator<T> rows, Connection c, PgText<T> T)
      throws SQLException {
    var copyManager = c.unwrap(PGConnection.class).getCopyAPI();

    var in = copyManager.copyIn(copyCommand);
    try {
      while (rows.hasNext()) {
        var sb = new StringBuilder();
        for (int i = 0; i < batchSize && rows.hasNext(); i++) {
          T.unsafeEncode(rows.next(), sb);
          sb.append("\n");
        }
        var bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        in.writeToCopy(bytes, 0, bytes.length);
      }
      return in.endCopy();
    } catch (Throwable th) {
      try {
        in.cancelCopy();
      } catch (PSQLException ignored) {
      }
      throw th;
    }
  }
}
