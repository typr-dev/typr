package com.example.events



/** Customer email address */
case class Email(value: String) extends scala.AnyVal {
  /** Get the underlying value */
  def unwrap: String = {
    return this.value
  }
}

object Email {
  /** Create a Email from a raw value */
  def valueOf(v: String): Email = {
    return new Email(v)
  }
}