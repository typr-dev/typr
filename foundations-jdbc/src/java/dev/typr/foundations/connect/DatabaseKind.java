package dev.typr.foundations.connect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Enumeration of supported database systems. Used for detecting the database type from a connection
 * and for routing to database-specific code.
 */
public enum DatabaseKind {
  POSTGRESQL,
  MARIADB,
  DUCKDB,
  ORACLE,
  SQLSERVER,
  DB2;

  /**
   * Detect the database kind from an open connection by examining the database product name.
   *
   * @param conn an open database connection
   * @return the detected DatabaseKind
   * @throws SQLException if unable to get metadata
   * @throws IllegalArgumentException if the database is not recognized
   */
  public static DatabaseKind detect(Connection conn) throws SQLException {
    String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
    return fromProductName(productName);
  }

  /**
   * Detect the database kind from an open connection by examining the driver name. This is an
   * alternative to {@link #detect(Connection)} when the product name is not reliable.
   *
   * @param conn an open database connection
   * @return the detected DatabaseKind
   * @throws SQLException if unable to get metadata
   * @throws IllegalArgumentException if the database is not recognized
   */
  public static DatabaseKind detectFromDriver(Connection conn) throws SQLException {
    String driverName = conn.getMetaData().getDriverName().toLowerCase();
    return fromDriverName(driverName);
  }

  private static DatabaseKind fromProductName(String productName) {
    if (productName.contains("postgresql")) {
      return POSTGRESQL;
    } else if (productName.contains("mariadb")) {
      return MARIADB;
    } else if (productName.contains("mysql")) {
      return MARIADB;
    } else if (productName.contains("duckdb")) {
      return DUCKDB;
    } else if (productName.contains("oracle")) {
      return ORACLE;
    } else if (productName.contains("microsoft sql server") || productName.contains("sql server")) {
      return SQLSERVER;
    } else if (productName.contains("db2") || productName.contains("ibm data server")) {
      return DB2;
    } else {
      throw new IllegalArgumentException("Unsupported database: " + productName);
    }
  }

  private static DatabaseKind fromDriverName(String driverName) {
    if (driverName.contains("postgresql")) {
      return POSTGRESQL;
    } else if (driverName.contains("mariadb")) {
      return MARIADB;
    } else if (driverName.contains("mysql")) {
      return MARIADB;
    } else if (driverName.contains("duckdb")) {
      return DUCKDB;
    } else if (driverName.contains("oracle")) {
      return ORACLE;
    } else if (driverName.contains("sqlserver") || driverName.contains("sql server")) {
      return SQLSERVER;
    } else if (driverName.contains("db2") || driverName.contains("jcc")) {
      return DB2;
    } else {
      throw new IllegalArgumentException("Unsupported driver: " + driverName);
    }
  }
}
