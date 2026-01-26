package com.example.events

import java.lang.UnsupportedOperationException

/** Union type for: string | int | boolean */
sealed interface StringOrIntOrBoolean {
  /** Wrapper for boolean value in union */
  data class BooleanValue(val value: kotlin.Boolean) : StringOrIntOrBoolean {
    override fun asBoolean(): kotlin.Boolean {
      return value
    }

    override fun asInt(): Int {
      throw UnsupportedOperationException("Not a Int value")
    }

    override fun asString(): kotlin.String {
      throw UnsupportedOperationException("Not a String value")
    }

    override fun isBoolean(): kotlin.Boolean {
      return true
    }

    override fun isInt(): kotlin.Boolean {
      return false
    }

    override fun isString(): kotlin.Boolean {
      return false
    }

    override fun toString(): kotlin.String {
      return value.toString()
    }
  }

  /** Wrapper for int value in union */
  data class IntValue(val value: Int) : StringOrIntOrBoolean {
    override fun asBoolean(): kotlin.Boolean {
      throw UnsupportedOperationException("Not a Boolean value")
    }

    override fun asInt(): Int {
      return value
    }

    override fun asString(): kotlin.String {
      throw UnsupportedOperationException("Not a String value")
    }

    override fun isBoolean(): kotlin.Boolean {
      return false
    }

    override fun isInt(): kotlin.Boolean {
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
  data class StringValue(val value: kotlin.String) : StringOrIntOrBoolean {
    override fun asBoolean(): kotlin.Boolean {
      throw UnsupportedOperationException("Not a Boolean value")
    }

    override fun asInt(): Int {
      throw UnsupportedOperationException("Not a Int value")
    }

    override fun asString(): kotlin.String {
      return value
    }

    override fun isBoolean(): kotlin.Boolean {
      return false
    }

    override fun isInt(): kotlin.Boolean {
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
    fun of(value: kotlin.String): StringOrIntOrBoolean {
      return com.example.events.StringOrIntOrBoolean.StringValue(value)
    }

    /** Create a union value from a int */
    fun of(value: Int): StringOrIntOrBoolean {
      return IntValue(value)
    }

    /** Create a union value from a boolean */
    fun of(value: kotlin.Boolean): StringOrIntOrBoolean {
      return BooleanValue(value)
    }
  }

  /** Get the boolean value. Throws if this is not a boolean. */
  abstract fun asBoolean(): kotlin.Boolean

  /** Get the int value. Throws if this is not a int. */
  abstract fun asInt(): Int

  /** Get the string value. Throws if this is not a string. */
  abstract fun asString(): kotlin.String

  /** Check if this union contains a boolean value */
  abstract fun isBoolean(): kotlin.Boolean

  /** Check if this union contains a int value */
  abstract fun isInt(): kotlin.Boolean

  /** Check if this union contains a string value */
  abstract fun isString(): kotlin.Boolean
}