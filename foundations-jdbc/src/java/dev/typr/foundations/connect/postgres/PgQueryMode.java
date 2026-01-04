package dev.typr.foundations.connect.postgres;

/** PostgreSQL query execution mode. */
public enum PgQueryMode {
  /** Use simple protocol for all queries (no parameter binding, no prepared statements). */
  SIMPLE("simple"),
  /** Use extended protocol with parameter binding (default). */
  EXTENDED("extended"),
  /** Use extended protocol only for prepared statements. */
  EXTENDED_FOR_PREPARED("extendedForPrepared"),
  /** Cache all statements (use with care - can lead to high memory usage). */
  EXTENDED_CACHE_EVERYTHING("extendedCacheEverything");

  private final String value;

  PgQueryMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
