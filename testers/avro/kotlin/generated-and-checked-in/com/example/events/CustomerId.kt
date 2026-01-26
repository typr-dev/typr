package com.example.events



/** Customer identifier */
data class CustomerId(val value: kotlin.Long) {
  /** Get the underlying value */
  fun unwrap(): kotlin.Long {
    return this.value
  }

  override fun toString(): kotlin.String {
    return value.toString()
  }

  companion object {
    /** Create a CustomerId from a raw value */
    fun valueOf(v: kotlin.Long): CustomerId {
      return CustomerId(v)
    }
  }
}