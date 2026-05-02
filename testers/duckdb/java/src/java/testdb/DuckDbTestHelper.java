package testdb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.ConnectionSettings;
import dev.typr.foundations.connect.DuckDbConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DuckDbTestHelper {
  private static final DuckDbConfig CONFIG = DuckDbConfig.inMemory().build();

  private static String readSchema() {
    try {
      return Files.readString(Path.of("sql-init/duckdb/00-schema.sql"));
    } catch (IOException e) {
      throw new RuntimeException("Failed to read DuckDB schema", e);
    }
  }

  private static final ConnectionSettings SETTINGS =
      ConnectionSettings.builder().connectionInitSql(readSchema()).build();

  private static final Transactor TRANSACTOR = Transactor.create(CONFIG, SETTINGS).rollbackOnly();

  public static <T> T apply(SqlFunction<Connection, T> f) {
    return TRANSACTOR.transact(f);
  }

  public static void run(SqlConsumer<Connection> f) {
    TRANSACTOR.transactVoid(f);
  }

  public static <T> T applyRead(SqlFunction<ConnectionRead, T> f) {
    return TRANSACTOR.transactRead(f);
  }
}
