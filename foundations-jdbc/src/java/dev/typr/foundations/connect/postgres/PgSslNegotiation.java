package dev.typr.foundations.connect.postgres;

/** PostgreSQL SSL negotiation mode. */
public enum PgSslNegotiation {
  /** Use PostgreSQL's SSL negotiation protocol (default). */
  POSTGRES("postgres"),
  /** Initiate SSL directly without PostgreSQL negotiation (for proxies that require it). */
  DIRECT("direct");

  private final String value;

  PgSslNegotiation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
