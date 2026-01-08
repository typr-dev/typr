package dev.typr.foundations.data.precise;

import java.time.LocalDateTime;

/**
 * Abstract interface for LocalDateTime types with fractional seconds precision constraint.
 *
 * <p>Generated precise types like LocalDateTime3, LocalDateTime6 implement this interface, allowing
 * users to abstract over different temporal precision constraints.
 *
 * <p>Two LocalDateTimeN values with the same underlying timestamp are considered semantically equal
 * via {@link #semanticEquals}, regardless of their declared precision.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends LocalDateTimeN> void processDateTime(T value) {
 *     LocalDateTime raw = value.rawValue();
 *     int fsp = value.fractionalSecondsPrecision();
 *     // ...
 * }
 * }</pre>
 */
public interface LocalDateTimeN {
  /** Get the underlying LocalDateTime value. */
  LocalDateTime rawValue();

  /** Get the fractional seconds precision (0-9) for this type. */
  int fractionalSecondsPrecision();

  /**
   * Compare this LocalDateTimeN to another for semantic equality. Two LocalDateTimeN values are
   * semantically equal if they have the same timestamp, regardless of their declared precision.
   */
  boolean semanticEquals(LocalDateTimeN other);

  /** Compute a semantic hash code based only on the underlying value. */
  int semanticHashCode();
}
