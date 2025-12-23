package adventureworks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class WithConnection {
  private static final String JDBC_URL =
      "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password";

  public static <T> T apply(Function<Connection, T> f) {
    try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
      conn.setAutoCommit(false);
      try {
        return f.apply(conn);
      } finally {
        conn.rollback();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void run(Consumer<Connection> f) {
    try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
      conn.setAutoCommit(false);
      try {
        f.accept(conn);
      } finally {
        conn.rollback();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
