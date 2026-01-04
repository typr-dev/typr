package testdb;

import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.duckdb.DuckDbConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class DuckDbTestHelper {
  private static final DuckDbConfig CONFIG = DuckDbConfig.inMemory().build();
  private static String schemaSQL = null;

  private static synchronized String getSchemaSQL() {
    if (schemaSQL == null) {
      try {
        Path schemaPath = Path.of("sql-init/duckdb/00-schema.sql");
        schemaSQL = Files.readString(schemaPath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to read DuckDB schema", e);
      }
    }
    return schemaSQL;
  }

  private static Connection createConnection() throws SQLException {
    Connection conn = CONFIG.connect();
    conn.createStatement().execute(getSchemaSQL());
    return conn;
  }

  private static final Transactor TRANSACTOR =
      new Transactor(DuckDbTestHelper::createConnection, Transactor.testStrategy());

  public static <T> T apply(Function<Connection, T> f) {
    try {
      return TRANSACTOR.execute(conn -> f.apply(conn));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void run(Consumer<Connection> f) {
    try {
      TRANSACTOR.executeVoid(conn -> f.accept(conn));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
