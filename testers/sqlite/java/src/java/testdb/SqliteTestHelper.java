package testdb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.ConnectionSettings;
import dev.typr.foundations.connect.SqliteConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives a single in-memory SQLite DB across all tests. SQLite's xerial driver can't run a
 * multi-statement string in one {@code Statement.execute()} call (unlike DuckDB), so we cannot rely
 * on {@link ConnectionSettings#connectionInitSql}. Instead, on first use we open the connection
 * source ourselves, split the schema on {@code ;} boundaries, and execute each statement.
 *
 * <p>SQLite's in-memory database is bound to a single connection, so {@link Transactor} reuses one
 * connection underneath. Each {@code apply}/{@code run} is wrapped in {@link
 * Transactor#rollbackOnly()}, which BEGINs and ROLLBACKs around the test body — keeping per-test
 * isolation while the schema (which we COMMIT during init) survives.
 */
public class SqliteTestHelper {
  private static final SqliteConfig CONFIG = SqliteConfig.inMemory().foreignKeys(true).build();
  private static final Transactor BASE = Transactor.create(CONFIG, ConnectionSettings.EMPTY);
  private static final Transactor TRANSACTOR = BASE.rollbackOnly();
  private static final AtomicBoolean INITIALISED = new AtomicBoolean(false);

  private static void ensureInitialised() {
    if (INITIALISED.compareAndSet(false, true)) {
      String schema;
      try {
        schema = Files.readString(Path.of("sql-init/sqlite/00-schema.sql"));
      } catch (IOException e) {
        throw new RuntimeException("Failed to read SQLite schema", e);
      }

      // Use the non-rollback base transactor so the schema commits and survives subsequent
      // rollback-only test transactions.
      BASE.transact(
          conn -> {
            try (Statement stmt = conn.unwrap().createStatement()) {
              for (String chunk : splitStatements(schema)) {
                String s = chunk.trim();
                if (!s.isEmpty()) stmt.execute(s);
              }
            } catch (SQLException e) {
              throw new RuntimeException("Failed to load SQLite schema", e);
            }
            return null;
          });
    }
  }

  /** Split a SQL string on `;` outside of `'...'` literals, stripping `-- ...` line comments. */
  private static java.util.List<String> splitStatements(String sql) {
    java.util.List<String> out = new java.util.ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inString = false;
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      if (!inString && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        while (i < sql.length() && sql.charAt(i) != '\n') i++;
      } else if (c == '\'') {
        cur.append(c);
        inString = !inString;
        i++;
      } else if (c == ';' && !inString) {
        out.add(cur.toString());
        cur.setLength(0);
        i++;
      } else {
        cur.append(c);
        i++;
      }
    }
    out.add(cur.toString());
    return out;
  }

  public static <T> T apply(SqlFunction<Connection, T> f) {
    ensureInitialised();
    return TRANSACTOR.transact(f);
  }

  public static void run(SqlConsumer<Connection> f) {
    ensureInitialised();
    TRANSACTOR.transactVoid(f);
  }

  public static <T> T applyRead(SqlFunction<ConnectionRead, T> f) {
    ensureInitialised();
    return TRANSACTOR.transactRead(f);
  }
}
