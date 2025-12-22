package typo.runtime;

import java.util.Objects;
import java.util.Optional;

/**
 * A fixed-length, blank-padded string value.
 *
 * <p>Represents Oracle CHAR(n) and NCHAR(n) types, which are always padded to exactly n characters
 * with trailing spaces. Oracle converts empty strings to NULL, so this type represents strings that
 * are guaranteed to be non-null and non-empty.
 */
public final class PaddedString {
  private final String value;
  private final int length;

  private PaddedString(String value, int length) {
    this.value = value;
    this.length = length;
  }

  /**
   * Smart constructor: Create a PaddedString from a string value and target length. The string will
   * be padded to the specified length with trailing spaces. Returns Optional.empty() if the string
   * is null, empty, or longer than the target length.
   */
  public static Optional<PaddedString> apply(String s, int length) {
    if (s == null || s.isEmpty()) {
      return Optional.empty();
    }
    if (s.length() > length) {
      return Optional.empty();
    }
    // Pad to target length
    String padded = String.format("%-" + length + "s", s);
    return Optional.of(new PaddedString(padded, length));
  }

  /**
   * Force constructor: Create a PaddedString from a string value and target length. Throws
   * IllegalArgumentException if the string is null, empty, or longer than the target length.
   */
  public static PaddedString force(String s, int length) {
    if (s == null || s.isEmpty()) {
      throw new IllegalArgumentException("String cannot be null or empty");
    }
    if (s.length() > length) {
      throw new IllegalArgumentException(
          "String length " + s.length() + " exceeds maximum " + length);
    }
    String padded = String.format("%-" + length + "s", s);
    return new PaddedString(padded, length);
  }

  /** Get the padded value (includes trailing spaces). */
  public String value() {
    return value;
  }

  /** Get the declared length of this padded string. */
  public int length() {
    return length;
  }

  /** Get the value with trailing spaces removed. */
  public String trimmed() {
    return value.stripTrailing();
  }

  @Override
  public String toString() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PaddedString that = (PaddedString) o;
    return length == that.length && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, length);
  }
}
