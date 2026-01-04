package adventureworks;

import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.postgres.PostgresConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class WithConnection {
  private static final PostgresConfig CONFIG =
      PostgresConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build();

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
