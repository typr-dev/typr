package dev.typr.foundations.data.precise;

import java.time.OffsetDateTime;

/**
 * Abstract interface for OffsetDateTime types with fractional seconds precision constraint.
 *
 * <p>Generated precise types like OffsetDateTime3, OffsetDateTime7 implement this interface,
 * allowing users to abstract over different temporal precision constraints.
 *
 * <p>Two OffsetDateTimeN values with the same underlying timestamp are considered semantically
 * equal via {@link #semanticEquals}, regardless of their declared precision.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends OffsetDateTimeN> void processOffsetDateTime(T value) {
 *     OffsetDateTime raw = value.rawValue();
 *     int fsp = value.fractionalSecondsPrecision();
 *     // ...
 * }
 * }</pre>
 */
public interface OffsetDateTimeN {
  /** Get the underlying OffsetDateTime value. */
  OffsetDateTime rawValue();

  /** Get the fractional seconds precision (0-9) for this type. */
  int fractionalSecondsPrecision();

  /**
   * Compare this OffsetDateTimeN to another for semantic equality. Two OffsetDateTimeN values are
   * semantically equal if they have the same timestamp, regardless of their declared precision.
   */
  boolean semanticEquals(OffsetDateTimeN other);

  /** Compute a semantic hash code based only on the underlying value. */
  int semanticHashCode();
}
