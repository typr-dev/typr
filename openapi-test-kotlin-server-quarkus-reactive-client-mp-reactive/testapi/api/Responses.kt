package testapi.api

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.Nothing
import testapi.model.Error

/** Response type for: 201, 400 */
sealed interface Response201400<T201, T400> {
  @JsonProperty("status")
  fun status(): String
}

/** Response type for: 404, default */
sealed interface Response404Default<T404> {
  @JsonProperty("status")
  fun status(): String
}

/** Response type for: 200, 404 */
sealed interface Response200404<T200, T404> {
  @JsonProperty("status")
  fun status(): String
}

/** Response type for: 200, 4XX, 5XX */
sealed interface Response2004XX5XX<T200> {
  @JsonProperty("status")
  fun status(): String
}

/** HTTP 400 response */
data class BadRequest<T>(@field:JsonProperty("value") val value: T) : Response201400<Nothing, T> {
  override fun status(): String = "400"
}

/** HTTP 201 response */
data class Created<T>(@field:JsonProperty("value") val value: T) : Response201400<T, Nothing> {
  override fun status(): String = "201"
}

/** HTTP default response */
data class Default(
  /** HTTP status code */
  @field:JsonProperty("statusCode") val statusCode: Int,
  @field:JsonProperty("value") val value: Error
) : Response404Default<Nothing> {
  override fun status(): String = "default"
}

/** HTTP 5XX response */
data class ServerError5XX(
  /** HTTP status code */
  @field:JsonProperty("statusCode") val statusCode: Int,
  @field:JsonProperty("value") val value: Error
) : Response2004XX5XX<Nothing> {
  override fun status(): String = "5XX"
}

/** HTTP 200 response */
data class Ok<T>(@field:JsonProperty("value") val value: T) : Response200404<T, Nothing>, Response2004XX5XX<T> {
  override fun status(): String = "200"
}

/** HTTP 404 response */
data class NotFound<T>(@field:JsonProperty("value") val value: T) : Response404Default<T>, Response200404<Nothing, T> {
  override fun status(): String = "404"
}

/** HTTP 4XX response */
data class ClientError4XX(
  /** HTTP status code */
  @field:JsonProperty("statusCode") val statusCode: Int,
  @field:JsonProperty("value") val value: Error
) : Response2004XX5XX<Nothing> {
  override fun status(): String = "4XX"
}