package com.example.events

import java.lang.UnsupportedOperationException

/** Union type for: string | long */
sealed interface StringOrLong {
  /** Wrapper for long value in union */
  data class LongValue(val value: kotlin.Long) : StringOrLong {
    override fun asLong(): kotlin.Long {
      return value
    }

    override fun asString(): kotlin.String {
      throw UnsupportedOperationException("Not a String value")
    }

    override fun isLong(): kotlin.Boolean {
      return true
    }

    override fun isString(): kotlin.Boolean {
      return false
    }

    override fun toString(): kotlin.String {
      return value.toString()
    }
  }

  /** Wrapper for string value in union */
  data class StringValue(val value: kotlin.String) : StringOrLong {
    override fun asLong(): kotlin.Long {
      throw UnsupportedOperationException("Not a Long value")
    }

    override fun asString(): kotlin.String {
      return value
    }

    override fun isLong(): kotlin.Boolean {
      return false
    }

    override fun isString(): kotlin.Boolean {
      return true
    }

    override fun toString(): kotlin.String {
      return value
    }
  }

  companion object {
    /** Create a union value from a string */
    fun of(value: kotlin.String): StringOrLong {
      return StringValue(value)
    }

    /** Create a union value from a long */
    fun of(value: kotlin.Long): StringOrLong {
      return LongValue(value)
    }
  }

  /** Get the long value. Throws if this is not a long. */
  abstract fun asLong(): kotlin.Long

  /** Get the string value. Throws if this is not a string. */
  abstract fun asString(): kotlin.String

  /** Check if this union contains a long value */
  abstract fun isLong(): kotlin.Boolean

  /** Check if this union contains a string value */
  abstract fun isString(): kotlin.Boolean
}