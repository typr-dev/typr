package dev.typr.foundations.data.precise;

/**
 * Abstract interface for fixed-length, blank-padded, non-empty string types.
 *
 * <p>Generated precise types like NonEmptyPaddedString10, NonEmptyPaddedString20 implement this
 * interface. These types represent CHAR(n) columns that are always padded to exactly n characters
 * with trailing spaces and must contain at least one non-whitespace character.
 *
 * <p>This is particularly useful for Oracle CHAR columns which cannot store empty strings (Oracle
 * treats empty string as NULL).
 *
 * <p>Two NonEmptyPaddedStringN values with the same trimmed content are considered semantically
 * equal via {@link #semanticEquals}, regardless of their declared length. Comparison is done on
 * trimmed values.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends NonEmptyPaddedStringN> void processString(T value) {
 *     String padded = value.rawValue();    // includes trailing spaces
 *     String trimmed = value.trimmed(); // without trailing spaces
 *     int len = value.length();         // declared fixed length
 *     // ...
 * }
 * }</pre>
 */
public interface NonEmptyPaddedStringN {
  /** Get the underlying padded string value (includes trailing spaces). */
  String rawValue();

  /** Get the value with trailing spaces removed. */
  String trimmed();

  /** Get the fixed length for this type. */
  int length();

  /**
   * Compare this NonEmptyPaddedStringN to another for semantic equality. Two NonEmptyPaddedStringN
   * values are semantically equal if they have the same trimmed content, regardless of their
   * declared length.
   */
  boolean semanticEquals(NonEmptyPaddedStringN other);

  /**
   * Compute a semantic hash code based on the trimmed value. This is compatible with semanticEquals
   * for use in collections that compare NonEmptyPaddedStringN values by content.
   */
  int semanticHashCode();
}
