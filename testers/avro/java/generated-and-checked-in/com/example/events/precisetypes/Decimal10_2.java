package com.example.events.precisetypes;

import dev.typr.foundations.data.precise.DecimalN;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public record Decimal10_2(BigDecimal value) implements DecimalN {
  @java.lang.Deprecated
  public Decimal10_2 {}

  public Decimal10_2 withValue(BigDecimal value) {
    return new Decimal10_2(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  public static Optional<Decimal10_2> of(BigDecimal value) {
    BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
    return scaled.precision() <= 10 ? Optional.of(new Decimal10_2(scaled)) : Optional.empty();
  }

  public static Decimal10_2 of(Integer value) {
    return new Decimal10_2(BigDecimal.valueOf((long) (value)));
  }

  public static Optional<Decimal10_2> of(Long value) {
    return Decimal10_2.of(BigDecimal.valueOf(value));
  }

  public static Optional<Decimal10_2> of(Double value) {
    return Decimal10_2.of(BigDecimal.valueOf(value));
  }

  public static Decimal10_2 unsafeForce(BigDecimal value) {
    BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
    if (scaled.precision() > 10) {
      throw new IllegalArgumentException("Value exceeds precision(10, 2)");
    }
    ;
    return new Decimal10_2(scaled);
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
    return 10;
  }

  @Override
  public int scale() {
    return 2;
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
