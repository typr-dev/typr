package testdb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.ConnectionSettings;
import dev.typr.foundations.connect.MariaConfig;
import dev.typr.foundations.connect.TransactionIsolation;

public class MariaDbTestHelper {
  private static final MariaConfig CONFIG =
      MariaConfig.builder("localhost", 3307, "typr", "typr", "password").build();

  private static final ConnectionSettings SETTINGS =
      ConnectionSettings.builder()
          .transactionIsolation(TransactionIsolation.READ_COMMITTED)
          .build();

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
