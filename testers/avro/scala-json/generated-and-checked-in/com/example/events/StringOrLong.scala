package com.example.events

import java.lang.UnsupportedOperationException

/** Union type for: string | long */
sealed trait StringOrLong {
  /** Check if this union contains a string value */
  def isString: Boolean

  /** Get the string value. Throws if this is not a string. */
  def asString: String

  /** Check if this union contains a long value */
  def isLong: Boolean

  /** Get the long value. Throws if this is not a long. */
  def asLong: Long
}

object StringOrLong {
  /** Create a union value from a string */
  def of(value: String): StringOrLong = {
    return new StringValue(value)
  }

  /** Create a union value from a long */
  def of(value: Long): StringOrLong = {
    return new LongValue(value)
  }

  /** Wrapper for long value in union */
  case class LongValue(value: Long) extends StringOrLong {
    override def isString: Boolean = {
      return false
    }

    override def asString: String = {
      throw new UnsupportedOperationException("Not a String value")
    }

    override def isLong: Boolean = {
      return true
    }

    override def asLong: Long = {
      return value
    }
  }

  /** Wrapper for string value in union */
  case class StringValue(value: String) extends StringOrLong {
    override def isString: Boolean = {
      return true
    }

    override def asString: String = {
      return value
    }

    override def isLong: Boolean = {
      return false
    }

    override def asLong: Long = {
      throw new UnsupportedOperationException("Not a Long value")
    }
  }
}