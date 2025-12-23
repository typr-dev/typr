package adventureworks

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Utility object for creating database-compatible timestamps.
 *
 * PostgreSQL timestamp types store microsecond precision (6 decimal places), while Java's
 * LocalDateTime and OffsetDateTime have nanosecond precision. This causes test failures on Linux
 * where timestamps don't roundtrip correctly through the database.
 *
 * Use these methods instead of .now() in tests to ensure timestamps are compatible with database
 * precision.
 */
object DbNow {

  /**
   * Returns the current LocalDateTime truncated to microseconds (6 decimal places) to match
   * PostgreSQL timestamp precision.
   */
  fun localDateTime(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

  /**
   * Returns the current OffsetDateTime truncated to microseconds (6 decimal places) to match
   * PostgreSQL timestamptz precision.
   */
  fun offsetDateTime(): OffsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)

  /**
   * Returns the current Instant truncated to microseconds (6 decimal places) to match PostgreSQL
   * timestamp precision.
   */
  fun instant(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

  /**
   * Converts an Instant to LocalDateTime in the system default timezone, truncated to
   * microseconds.
   */
  fun toLocalDateTime(instant: Instant): LocalDateTime =
      LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MICROS)

  /**
   * Converts an Instant to OffsetDateTime in the system default timezone, truncated to
   * microseconds.
   */
  fun toOffsetDateTime(instant: Instant): OffsetDateTime =
      OffsetDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MICROS)
}
