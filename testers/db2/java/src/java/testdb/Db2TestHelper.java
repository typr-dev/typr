package testdb;

import dev.typr.foundations.Connection;
import dev.typr.foundations.ConnectionRead;
import dev.typr.foundations.SqlConsumer;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.Db2Config;

public class Db2TestHelper {
  private static final Db2Config CONFIG =
      Db2Config.builder("localhost", 50000, "typr", "db2inst1", "password").build();

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
