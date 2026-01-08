package dev.typr.foundations.data.precise;

/**
 * Abstract interface for fixed-length, blank-padded string types.
 *
 * <p>Generated precise types like PaddedString10, PaddedString20 implement this interface. These
 * types represent CHAR(n) columns which are always padded to exactly n characters with trailing
 * spaces.
 *
 * <p>Two PaddedStringN values with the same trimmed content are considered semantically equal via
 * {@link #semanticEquals}, regardless of their declared length. Comparison is done on trimmed
 * values.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends PaddedStringN> void processString(T value) {
 *     String padded = value.rawValue();    // includes trailing spaces
 *     String trimmed = value.trimmed(); // without trailing spaces
 *     int len = value.length();         // declared fixed length
 *     // ...
 * }
 * }</pre>
 */
public interface PaddedStringN {
  /** Get the underlying padded string value (includes trailing spaces). */
  String rawValue();

  /** Get the value with trailing spaces removed. */
  String trimmed();

  /** Get the fixed length for this type. */
  int length();

  /**
   * Compare this PaddedStringN to another for semantic equality. Two PaddedStringN values are
   * semantically equal if they have the same trimmed content, regardless of their declared length.
   */
  boolean semanticEquals(PaddedStringN other);

  /**
   * Compute a semantic hash code based on the trimmed value. This is compatible with semanticEquals
   * for use in collections that compare PaddedStringN values by content.
   */
  int semanticHashCode();
}
