package testapi.api

import scala.Nothing
import testapi.model.Error

/** Response type for: 201, 400 */
trait Response201400[+T201, +T400] {
  def status: String
}

/** Response type for: 404, default */
trait Response404Default[+T404] {
  def status: String
}

/** Response type for: 200, 404 */
trait Response200404[+T200, +T404] {
  def status: String
}

/** Response type for: 200, 4XX, 5XX */
trait Response2004XX5XX[+T200] {
  def status: String
}

/** HTTP 400 response */
case class BadRequest[+T](value: T) extends Response201400[Nothing, T] {
  override lazy val status: String = "400"
}

/** HTTP 201 response */
case class Created[+T](value: T) extends Response201400[T, Nothing] {
  override lazy val status: String = "201"
}

/** HTTP default response */
case class Default(
  /** HTTP status code */
  statusCode: Int,
  value: Error
) extends Response404Default[Nothing] {
  override lazy val status: String = "default"
}

/** HTTP 5XX response */
case class ServerError5XX(
  /** HTTP status code */
  statusCode: Int,
  value: Error
) extends Response2004XX5XX[Nothing] {
  override lazy val status: String = "5XX"
}

/** HTTP 200 response */
case class Ok[+T](value: T) extends Response200404[T, Nothing] with Response2004XX5XX[T] {
  override lazy val status: String = "200"
}

/** HTTP 404 response */
case class NotFound[+T](value: T) extends Response404Default[T] with Response200404[Nothing, T] {
  override lazy val status: String = "404"
}

/** HTTP 4XX response */
case class ClientError4XX(
  /** HTTP status code */
  statusCode: Int,
  value: Error
) extends Response2004XX5XX[Nothing] {
  override lazy val status: String = "4XX"
}