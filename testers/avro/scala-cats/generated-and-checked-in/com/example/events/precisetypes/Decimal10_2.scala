package com.example.events.precisetypes

import dev.typr.foundations.data.precise.DecimalN
import java.lang.IllegalArgumentException

case class Decimal10_2 private(value: BigDecimal) extends DecimalN {
  override def decimalValue: java.math.BigDecimal = value.bigDecimal

  override def precision: Int = 10

  override def scale: Int = 2

  override def semanticEquals(other: DecimalN): Boolean = (if (other == null) false else decimalValue().compareTo(other.decimalValue()) == 0)

  override def semanticHashCode: Int = decimalValue().stripTrailingZeros().hashCode()

  override def equals(that: Any): Boolean = (this eq that.asInstanceOf[AnyRef]) || (that match { case other: DecimalN => decimalValue().compareTo(other.decimalValue()) == 0; case _ => false })

  override def hashCode: Int = decimalValue().stripTrailingZeros().hashCode()
}

object Decimal10_2 {
  def of(value: BigDecimal): Option[Decimal10_2] = { val scaled = value.setScale(2, BigDecimal.RoundingMode.HALF_UP); if (scaled.precision <= 10) Some(new Decimal10_2(scaled)) else None }

  def of(value: Int): Decimal10_2 = new Decimal10_2(BigDecimal(value))

  def of(value: Long): Option[Decimal10_2] = Decimal10_2.of(BigDecimal(value))

  def of(value: Double): Option[Decimal10_2] = Decimal10_2.of(BigDecimal(value))

  def unsafeForce(value: BigDecimal): Decimal10_2 = { val scaled = value.setScale(2, BigDecimal.RoundingMode.HALF_UP); if (scaled.precision > 10) throw new IllegalArgumentException("Value exceeds precision(10, 2)"); new Decimal10_2(scaled) }
}