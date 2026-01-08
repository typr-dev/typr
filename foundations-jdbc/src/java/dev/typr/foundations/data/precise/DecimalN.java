package dev.typr.foundations.data.precise;

import java.math.BigDecimal;

/**
 * Abstract interface for decimal types with precision and scale constraints.
 *
 * <p>Generated precise types like Decimal5_2, Decimal10_2, Decimal18_4 implement this interface,
 * allowing users to abstract over different decimal precision/scale constraints.
 *
 * <p>For types with scale=0 (integer decimals like Int5, Int10, Int18), this interface returns a
 * BigDecimal representation of the underlying BigInteger value.
 *
 * <p>Two DecimalN values with the same numeric value are considered semantically equal via {@link
 * #semanticEquals}, regardless of their declared precision/scale. Comparison uses {@link
 * BigDecimal#compareTo} so that values like 1.0 and 1.00 are equal.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public <T extends DecimalN> void processDecimal(T value) {
 *     BigDecimal raw = value.decimalValue();
 *     int precision = value.precision();
 *     int scale = value.scale();
 *     // ...
 * }
 * }</pre>
 */
public interface DecimalN {
  /** Get the value as BigDecimal. For scale=0 types, this converts from BigInteger. */
  BigDecimal decimalValue();

  /** Get the maximum precision (total number of digits) for this type. */
  int precision();

  /** Get the scale (digits after decimal point) for this type. */
  int scale();

  /**
   * Compare this DecimalN to another for semantic equality. Two DecimalN values are semantically
   * equal if they have the same numeric value (using compareTo), regardless of their declared
   * precision/scale.
   */
  boolean semanticEquals(DecimalN other);

  /**
   * Compute a semantic hash code based only on the underlying value. Uses stripTrailingZeros to
   * ensure that 1.0 and 1.00 have the same hash code.
   */
  int semanticHashCode();
}
