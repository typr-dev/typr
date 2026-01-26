package com.example.events.precisetypes

import dev.typr.foundations.data.precise.DecimalN
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.math.RoundingMode

@kotlin.ConsistentCopyVisibility
data class Decimal18_4 private constructor(val value: BigDecimal) : DecimalN {
  override fun decimalValue(): BigDecimal = value

  override fun equals(other: Any?): kotlin.Boolean {
    if (this === other) return true
    if (other !is DecimalN) return false
    return decimalValue().compareTo(other.decimalValue()) == 0
  }

  override fun hashCode(): Int = decimalValue().stripTrailingZeros().hashCode()

  override fun precision(): Int = 18

  override fun scale(): Int = 4

  override fun semanticEquals(other: DecimalN): kotlin.Boolean = if (other == null) false else decimalValue().compareTo(other.decimalValue()) == 0

  override fun semanticHashCode(): Int = decimalValue().stripTrailingZeros().hashCode()

  override fun toString(): kotlin.String {
    return value.toString()
  }

  companion object {
    fun of(value: BigDecimal): Decimal18_4? {
      val scaled = value.setScale(4, RoundingMode.HALF_UP)
      return if (scaled.precision() <= 18) Decimal18_4(scaled) else null
    }

    fun of(value: Int): Decimal18_4 = Decimal18_4(BigDecimal.valueOf(value.toLong()))

    fun of(value: kotlin.Long): Decimal18_4? = Decimal18_4.of(BigDecimal.valueOf(value))

    fun of(value: kotlin.Double): Decimal18_4? = Decimal18_4.of(BigDecimal.valueOf(value))

    fun unsafeForce(value: BigDecimal): Decimal18_4 {
      val scaled = value.setScale(4, RoundingMode.HALF_UP)
      if (scaled.precision() > 18) throw IllegalArgumentException("Value exceeds precision(18, 4)")
      return Decimal18_4(scaled)
    }
  }
}