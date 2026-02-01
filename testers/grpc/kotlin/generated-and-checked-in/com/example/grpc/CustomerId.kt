package com.example.grpc



/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00007fc001118400@7b4c50bc */
data class CustomerId(val value: kotlin.String) {
  /** Get the underlying value */
  fun unwrap(): kotlin.String {
    return this.value
  }

  override fun toString(): kotlin.String {
    return value
  }

  companion object {
    /** Create a CustomerId from a raw value */
    fun valueOf(v: kotlin.String): CustomerId {
      return CustomerId(v)
    }
  }
}