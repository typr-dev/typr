package com.example.events

import com.fasterxml.jackson.annotation.JsonValue

/** Unique order identifier */
data class OrderId(@field:JsonValue val value: kotlin.String) {
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