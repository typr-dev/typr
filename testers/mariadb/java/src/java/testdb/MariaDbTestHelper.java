package testdb;

import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.mariadb.MariaDbConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class MariaDbTestHelper {
  private static final MariaDbConfig CONFIG =
      MariaDbConfig.builder("localhost", 3307, "typr", "typr", "password").build();

  private static final Transactor TRANSACTOR = new Transactor(CONFIG, Transactor.testStrategy());

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
