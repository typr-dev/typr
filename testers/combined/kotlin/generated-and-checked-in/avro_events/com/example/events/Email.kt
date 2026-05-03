package com.example.events



/** Customer email address */
data class Email(val value: kotlin.String) {
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