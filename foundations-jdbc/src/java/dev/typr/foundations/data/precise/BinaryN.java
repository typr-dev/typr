package dev.typr.foundations.data.precise;

/**
 * Abstract interface for binary types with a maximum length constraint.
 *
 * <p>Generated precise types like Binary16, Binary32, Binary64 implement this interface, allowing
 * users to abstract over different binary length constraints.
 *
 * <p>Two BinaryN values with the same underlying byte array are considered semantically equal via
 * {@link #semanticEquals}, regardless of their declared max length.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends BinaryN> void processBinary(T value) {
 *     byte[] raw = value.rawValue();
 *     int maxLen = value.maxLength();
 *     // ...
 * }
 * }</pre>
 */
public interface BinaryN {
  /** Get the underlying byte array value. */
  byte[] rawValue();

  /** Get the maximum allowed length in bytes for this type. */
  int maxLength();

  /**
   * Compare this BinaryN to another for semantic equality. Two BinaryN values are semantically
   * equal if they have the same underlying byte array content, regardless of their declared max
   * length.
   */
  boolean semanticEquals(BinaryN other);

  /**
   * Compute a semantic hash code based only on the underlying value. This is compatible with
   * semanticEquals for use in collections that compare BinaryN values by content.
   */
  int semanticHashCode();
}
