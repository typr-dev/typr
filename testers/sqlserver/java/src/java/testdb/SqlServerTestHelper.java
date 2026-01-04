package testdb;

import dev.typr.foundations.Transactor;
import dev.typr.foundations.connect.sqlserver.SqlServerConfig;
import dev.typr.foundations.connect.sqlserver.SqlServerEncrypt;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class SqlServerTestHelper {
  private static final SqlServerConfig CONFIG =
      SqlServerConfig.builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
          .encrypt(SqlServerEncrypt.FALSE)
          .build();

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
