package com.example.grpc



/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00007fc001118400@5884a914 */
data class OrderId(val value: kotlin.String) {
  /** Get the underlying value */
  fun unwrap(): kotlin.String {
    return this.value
  }

  override fun toString(): kotlin.String {
    return value
  }

  companion object {
    /** Create a OrderId from a raw value */
    fun valueOf(v: kotlin.String): OrderId {
      return OrderId(v)
    }
  }
}