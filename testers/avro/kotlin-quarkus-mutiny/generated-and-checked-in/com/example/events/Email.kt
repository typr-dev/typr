package com.example.events

import com.fasterxml.jackson.annotation.JsonValue

/** Customer email address */
data class Email(@field:JsonValue val value: kotlin.String) {
  /** Get the underlying value */
  fun unwrap(): kotlin.String {
    return this.value
  }

  override fun toString(): kotlin.String {
    return value
  }

  companion object {
    /** Create a Email from a raw value */
    fun valueOf(v: kotlin.String): Email {
      return Email(v)
    }
  }
}