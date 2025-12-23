package adventureworks;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for creating database-compatible timestamps.
 *
 * <p>PostgreSQL timestamp types store microsecond precision (6 decimal places), while Java's
 * LocalDateTime and OffsetDateTime have nanosecond precision. This causes test failures on Linux
 * where timestamps don't roundtrip correctly through the database.
 *
 * <p>Use these methods instead of .now() in tests to ensure timestamps are compatible with database
 * precision.
 */
public class DbNow {

  /**
   * Returns the current LocalDateTime truncated to microseconds (6 decimal places) to match
   * PostgreSQL timestamp precision.
   */
  public static LocalDateTime localDateTime() {
    return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Returns the current OffsetDateTime truncated to microseconds (6 decimal places) to match
   * PostgreSQL timestamptz precision.
   */
  public static OffsetDateTime offsetDateTime() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Returns the current Instant truncated to microseconds (6 decimal places) to match PostgreSQL
   * timestamp precision.
   */
  public static Instant instant() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Converts an Instant to LocalDateTime in the system default timezone, truncated to microseconds.
   */
  public static LocalDateTime toLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Converts an Instant to OffsetDateTime in the system default timezone, truncated to
   * microseconds.
   */
  public static OffsetDateTime toOffsetDateTime(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MICROS);
  }
}
