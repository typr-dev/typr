package com.example.grpc



/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00001e00012fb730@5a66fbd6 */
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