package dev.typr.foundations.data.precise;

import java.time.LocalTime;

/**
 * Abstract interface for LocalTime types with fractional seconds precision constraint.
 *
 * <p>Generated precise types like LocalTime3, LocalTime6 implement this interface, allowing users
 * to abstract over different temporal precision constraints.
 *
 * <p>Two LocalTimeN values with the same underlying time are considered semantically equal via
 * {@link #semanticEquals}, regardless of their declared precision.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends LocalTimeN> void processTime(T value) {
 *     LocalTime raw = value.rawValue();
 *     int fsp = value.fractionalSecondsPrecision();
 *     // ...
 * }
 * }</pre>
 */
public interface LocalTimeN {
  /** Get the underlying LocalTime value. */
  LocalTime rawValue();

  /** Get the fractional seconds precision (0-9) for this type. */
  int fractionalSecondsPrecision();

  /**
   * Compare this LocalTimeN to another for semantic equality. Two LocalTimeN values are
   * semantically equal if they have the same time, regardless of their declared precision.
   */
  boolean semanticEquals(LocalTimeN other);

  /** Compute a semantic hash code based only on the underlying value. */
  int semanticHashCode();
}
