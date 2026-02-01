package com.example.grpc



/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00001e00012fb730@52336435 */
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