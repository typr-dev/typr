package typr.runtime;

import java.util.Arrays;
import java.util.Optional;

/**
 * A non-empty byte array value.
 *
 * <p>Oracle converts empty byte arrays to NULL, so this type represents byte arrays that are
 * guaranteed to be non-null and non-empty - suitable for NOT NULL BLOB/RAW columns.
 */
public final class NonEmptyBlob {
  private final byte[] value;

  private NonEmptyBlob(byte[] value) {
    this.value = value;
  }

  /**
   * Smart constructor: Create a NonEmptyBlob from a byte array. Returns Optional.empty() if the
   * array is null or empty.
   */
  public static Optional<NonEmptyBlob> apply(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return Optional.empty();
    }
    return Optional.of(new NonEmptyBlob(bytes));
  }

  /**
   * Force constructor: Create a NonEmptyBlob from a byte array. Throws IllegalArgumentException if
   * the array is null or empty.
   */
  public static NonEmptyBlob force(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Byte array cannot be null or empty");
    }
    return new NonEmptyBlob(bytes);
  }

  public byte[] value() {
    return value;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < Math.min(value.length, 8); i++) {
      if (i > 0) sb.append(", ");
      sb.append(String.format("0x%02X", value[i] & 0xff));
    }
    if (value.length > 8) sb.append(", ...");
    sb.append("]");
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NonEmptyBlob that = (NonEmptyBlob) o;
    return Arrays.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }
}
