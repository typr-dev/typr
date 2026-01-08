package dev.typr.foundations.data.precise;

/**
 * Abstract interface for string types with a maximum length constraint.
 *
 * <p>Generated precise types like String10, String50, String255 implement this interface, allowing
 * users to abstract over different string length constraints.
 *
 * <p>Two StringN values with the same underlying string are considered semantically equal via
 * {@link #semanticEquals}, regardless of their declared max length. For example, a
 * String10("hello") and String50("hello") will compare equal using semanticEquals.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends StringN> void processString(T value) {
 *     String raw = value.rawValue();
 *     int maxLen = value.maxLength();
 *     // ...
 * }
 * }</pre>
 */
public interface StringN {
  /** Get the underlying string value. */
  String rawValue();

  /** Get the maximum allowed length for this type. */
  int maxLength();

  /**
   * Compare this StringN to another for semantic equality. Two StringN values are semantically
   * equal if they have the same underlying string value, regardless of their declared max length.
   */
  boolean semanticEquals(StringN other);

  /**
   * Compute a semantic hash code based only on the underlying value. This is compatible with
   * semanticEquals for use in collections that compare StringN values by content.
   */
  int semanticHashCode();
}
