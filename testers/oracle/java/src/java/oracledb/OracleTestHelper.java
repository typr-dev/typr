package oracledb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.OracleConfig;

public class OracleTestHelper {
  private static final OracleConfig CONFIG =
      OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password")
          .serviceName("FREEPDB1")
          .build();

  private static final Transactor TRANSACTOR = Transactor.create(CONFIG).rollbackOnly();

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
