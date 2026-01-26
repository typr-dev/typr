package com.example.events



/** Customer identifier */
case class CustomerId(value: Long) extends scala.AnyVal {
  /** Get the underlying value */
  def unwrap: Long = {
    return this.value
  }
}

object CustomerId {
  /** Create a CustomerId from a raw value */
  def valueOf(v: Long): CustomerId = {
    return new CustomerId(v)
  }
}