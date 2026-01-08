package dev.typr.foundations.data.precise;

import java.time.Instant;

/**
 * Abstract interface for Instant types with fractional seconds precision constraint.
 *
 * <p>Generated precise types like Instant3, Instant6 implement this interface, allowing users to
 * abstract over different temporal precision constraints.
 *
 * <p>Two InstantN values with the same underlying instant are considered semantically equal via
 * {@link #semanticEquals}, regardless of their declared precision.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends InstantN> void processInstant(T value) {
 *     Instant raw = value.rawValue();
 *     int fsp = value.fractionalSecondsPrecision();
 *     // ...
 * }
 * }</pre>
 */
public interface InstantN {
  /** Get the underlying Instant value. */
  Instant rawValue();

  /** Get the fractional seconds precision (0-9) for this type. */
  int fractionalSecondsPrecision();

  /**
   * Compare this InstantN to another for semantic equality. Two InstantN values are semantically
   * equal if they have the same instant, regardless of their declared precision.
   */
  boolean semanticEquals(InstantN other);

  /** Compute a semantic hash code based only on the underlying value. */
  int semanticHashCode();
}
