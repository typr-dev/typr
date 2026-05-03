package testdb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.ConnectionSettings;
import dev.typr.foundations.connect.SqlServerConfig;
import dev.typr.foundations.connect.SqlServerEncrypt;
import dev.typr.foundations.connect.TransactionIsolation;

public class SqlServerTestHelper {
  private static final SqlServerConfig CONFIG =
      SqlServerConfig.builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
          .encrypt(SqlServerEncrypt.FALSE)
          .build();

  // SQL Server uses pessimistic locking by default, which causes deadlocks when
  // multiple tests run in parallel and access the same tables. READ_UNCOMMITTED
  // prevents lock contention. Since tests rollback anyway, dirty reads are fine.
  private static final ConnectionSettings SETTINGS =
      ConnectionSettings.builder()
          .transactionIsolation(TransactionIsolation.READ_UNCOMMITTED)
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
