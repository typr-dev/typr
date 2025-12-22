package typo.runtime;

import java.util.Objects;
import java.util.Optional;

/**
 * A non-empty string value.
 *
 * <p>Oracle converts empty strings to NULL, so this type represents strings that are guaranteed to
 * be non-null and non-empty - suitable for NOT NULL VARCHAR2/NVARCHAR2/CLOB/NCLOB columns.
 */
public final class NonEmptyString {
  private final String value;

  private NonEmptyString(String value) {
    this.value = value;
  }

  /**
   * Smart constructor: Create a NonEmptyString from a string value. Returns Optional.empty() if the
   * string is null or empty.
   */
  public static Optional<NonEmptyString> apply(String s) {
    if (s == null || s.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new NonEmptyString(s));
  }

  /**
   * Force constructor: Create a NonEmptyString from a string value. Throws IllegalArgumentException
   * if the string is null or empty.
   */
  public static NonEmptyString force(String s) {
    if (s == null || s.isEmpty()) {
      throw new IllegalArgumentException("String cannot be null or empty");
    }
    return new NonEmptyString(s);
  }

  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NonEmptyString that = (NonEmptyString) o;
    return value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
