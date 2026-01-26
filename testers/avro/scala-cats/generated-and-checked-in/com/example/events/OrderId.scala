package com.example.events



/** Unique order identifier */
case class OrderId(value: String) extends scala.AnyVal {
  /** Get the underlying value */
  def unwrap: String = {
    return this.value
  }
}

object OrderId {
  /** Create a OrderId from a raw value */
  def valueOf(v: String): OrderId = {
    return new OrderId(v)
  }
}