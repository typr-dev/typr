package com.example.grpc



/** Wrapper type for java.lang.String */
case class CustomerId(value: String) extends scala.AnyVal {
  /** Get the underlying value */
  def unwrap: String = {
    return this.value
  }
}

object CustomerId {
  /** Create a CustomerId from a raw value */
  def valueOf(v: String): CustomerId = {
    return new CustomerId(v)
  }
}