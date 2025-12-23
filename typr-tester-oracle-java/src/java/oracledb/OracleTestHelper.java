package oracledb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class OracleTestHelper {
  private static final String JDBC_URL = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
  private static final String USERNAME = "typr";
  private static final String PASSWORD = "typr_password";

  public static <T> T apply(Function<Connection, T> f) {
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
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
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
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
