package dev.typr.foundations.data.precise;

/**
 * Abstract interface for non-empty string types with a maximum length constraint.
 *
 * <p>Generated precise types like NonEmptyString10, NonEmptyString50 implement this interface.
 * These types guarantee the string is non-null and non-empty, suitable for databases like Oracle
 * where empty strings are converted to NULL.
 *
 * <p>Two NonEmptyStringN values with the same underlying string are considered semantically equal
 * via {@link #semanticEquals}, regardless of their declared max length.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends NonEmptyStringN> void processString(T value) {
 *     String raw = value.rawValue();  // guaranteed non-empty
 *     int maxLen = value.maxLength();
 *     // ...
 * }
 * }</pre>
 */
public interface NonEmptyStringN {
  /** Get the underlying string value. Guaranteed to be non-null and non-empty. */
  String rawValue();

  /** Get the maximum allowed length for this type. */
  int maxLength();

  /**
   * Compare this NonEmptyStringN to another for semantic equality. Two NonEmptyStringN values are
   * semantically equal if they have the same underlying string value, regardless of their declared
   * max length.
   */
  boolean semanticEquals(NonEmptyStringN other);

  /**
   * Compute a semantic hash code based only on the underlying value. This is compatible with
   * semanticEquals for use in collections that compare NonEmptyStringN values by content.
   */
  int semanticHashCode();
}
