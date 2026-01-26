package com.example.events

import java.lang.UnsupportedOperationException

/** Union type for: string | int | boolean */
sealed trait StringOrIntOrBoolean {
  /** Check if this union contains a string value */
  def isString: Boolean

  /** Get the string value. Throws if this is not a string. */
  def asString: String

  /** Check if this union contains a int value */
  def isInt: Boolean

  /** Get the int value. Throws if this is not a int. */
  def asInt: Int

  /** Check if this union contains a boolean value */
  def isBoolean: Boolean

  /** Get the boolean value. Throws if this is not a boolean. */
  def asBoolean: Boolean
}

object StringOrIntOrBoolean {
  /** Create a union value from a string */
  def of(value: String): StringOrIntOrBoolean = {
    return new com.example.events.StringOrIntOrBoolean.StringValue(value)
  }

  /** Create a union value from a int */
  def of(value: Int): StringOrIntOrBoolean = {
    return new IntValue(value)
  }

  /** Create a union value from a boolean */
  def of(value: Boolean): StringOrIntOrBoolean = {
    return new BooleanValue(value)
  }

  /** Wrapper for boolean value in union */
  case class BooleanValue(value: Boolean) extends StringOrIntOrBoolean {
    override def isString: Boolean = {
      return false
    }

    override def asString: String = {
      throw new UnsupportedOperationException("Not a String value")
    }

    override def isInt: Boolean = {
      return false
    }

    override def asInt: Int = {
      throw new UnsupportedOperationException("Not a Int value")
    }

    override def isBoolean: Boolean = {
      return true
    }

    override def asBoolean: Boolean = {
      return value
    }
  }

  /** Wrapper for int value in union */
  case class IntValue(value: Int) extends StringOrIntOrBoolean {
    override def isString: Boolean = {
      return false
    }

    override def asString: String = {
      throw new UnsupportedOperationException("Not a String value")
    }

    override def isInt: Boolean = {
      return true
    }

    override def asInt: Int = {
      return value
    }

    override def isBoolean: Boolean = {
      return false
    }

    override def asBoolean: Boolean = {
      throw new UnsupportedOperationException("Not a Boolean value")
    }
  }

  /** Wrapper for string value in union */
  case class StringValue(value: String) extends StringOrIntOrBoolean {
    override def isString: Boolean = {
      return true
    }

    override def asString: String = {
      return value
    }

    override def isInt: Boolean = {
      return false
    }

    override def asInt: Int = {
      throw new UnsupportedOperationException("Not a Int value")
    }

    override def isBoolean: Boolean = {
      return false
    }

    override def asBoolean: Boolean = {
      throw new UnsupportedOperationException("Not a Boolean value")
    }
  }
}