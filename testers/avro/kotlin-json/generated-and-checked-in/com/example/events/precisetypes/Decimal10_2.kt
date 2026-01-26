package com.example.events.precisetypes

import dev.typr.foundations.data.precise.DecimalN
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.math.RoundingMode

@kotlin.ConsistentCopyVisibility
data class Decimal10_2 private constructor(val value: BigDecimal) : DecimalN {
  override fun decimalValue(): BigDecimal = value

  override fun equals(other: Any?): kotlin.Boolean {
    if (this === other) return true
    if (other !is DecimalN) return false
    return decimalValue().compareTo(other.decimalValue()) == 0
  }

  override fun hashCode(): Int = decimalValue().stripTrailingZeros().hashCode()

  override fun precision(): Int = 10

  override fun scale(): Int = 2

  override fun semanticEquals(other: DecimalN): kotlin.Boolean = if (other == null) false else decimalValue().compareTo(other.decimalValue()) == 0

  override fun semanticHashCode(): Int = decimalValue().stripTrailingZeros().hashCode()

  override fun toString(): kotlin.String {
    return value.toString()
  }

  companion object {
    fun of(value: BigDecimal): Decimal10_2? {
      val scaled = value.setScale(2, RoundingMode.HALF_UP)
      return if (scaled.precision() <= 10) Decimal10_2(scaled) else null
    }

    fun of(value: Int): Decimal10_2 = Decimal10_2(BigDecimal.valueOf(value.toLong()))

    fun of(value: kotlin.Long): Decimal10_2? = Decimal10_2.of(BigDecimal.valueOf(value))

    fun of(value: kotlin.Double): Decimal10_2? = Decimal10_2.of(BigDecimal.valueOf(value))

    fun unsafeForce(value: BigDecimal): Decimal10_2 {
      val scaled = value.setScale(2, RoundingMode.HALF_UP)
      if (scaled.precision() > 10) throw IllegalArgumentException("Value exceeds precision(10, 2)")
      return Decimal10_2(scaled)
    }
  }
}