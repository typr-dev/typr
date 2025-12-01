package testapi.api

import testapi.model.Animal
import testapi.model.Error

sealed trait ListAnimalsResponse {
  def status: String
}

object ListAnimalsResponse {
  /** A list of animals */
  case class Status200(value: List[Animal]) extends ListAnimalsResponse {
    override lazy val status: String = "200"
  }

  /** Client error (any 4xx status) */
  case class Status4XX(
    /** HTTP status code to return */
    statusCode: Integer,
    value: Error
  ) extends ListAnimalsResponse {
    override lazy val status: String = "4XX"
  }

  /** Server error (any 5xx status) */
  case class Status5XX(
    /** HTTP status code to return */
    statusCode: Integer,
    value: Error
  ) extends ListAnimalsResponse {
    override lazy val status: String = "5XX"
  }
}