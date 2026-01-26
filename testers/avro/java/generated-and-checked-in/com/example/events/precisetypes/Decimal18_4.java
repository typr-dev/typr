package com.example.events.precisetypes;

import dev.typr.foundations.data.precise.DecimalN;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public record Decimal18_4(BigDecimal value) implements DecimalN {
  @java.lang.Deprecated
  public Decimal18_4 {}

  public Decimal18_4 withValue(BigDecimal value) {
    return new Decimal18_4(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  public static Optional<Decimal18_4> of(BigDecimal value) {
    BigDecimal scaled = value.setScale(4, RoundingMode.HALF_UP);
    return scaled.precision() <= 18 ? Optional.of(new Decimal18_4(scaled)) : Optional.empty();
  }

  public static Decimal18_4 of(Integer value) {
    return new Decimal18_4(BigDecimal.valueOf((long) (value)));
  }

  public static Optional<Decimal18_4> of(Long value) {
    return Decimal18_4.of(BigDecimal.valueOf(value));
  }

  public static Optional<Decimal18_4> of(Double value) {
    return Decimal18_4.of(BigDecimal.valueOf(value));
  }

  public static Decimal18_4 unsafeForce(BigDecimal value) {
    BigDecimal scaled = value.setScale(4, RoundingMode.HALF_UP);
    if (scaled.precision() > 18) {
      throw new IllegalArgumentException("Value exceeds precision(18, 4)");
    }
    ;
    return new Decimal18_4(scaled);
  }

  @Override
  public BigDecimal decimalValue() {
    return value;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DecimalN other)) return false;
    return decimalValue().compareTo(other.decimalValue()) == 0;
  }

  @Override
  public int hashCode() {
    return decimalValue().stripTrailingZeros().hashCode();
  }

  @Override
  public int precision() {
    return 18;
  }

  @Override
  public int scale() {
    return 4;
  }

  @Override
  public boolean semanticEquals(DecimalN other) {
    return (other == null ? false : decimalValue().compareTo(other.decimalValue()) == 0);
  }

  @Override
  public int semanticHashCode() {
    return decimalValue().stripTrailingZeros().hashCode();
  }
}
