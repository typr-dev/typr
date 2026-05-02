package com.example.grpc



/** Wrapper type for kotlin.String */
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