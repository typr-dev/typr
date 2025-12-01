package testapi.model

import io.circe.Json

case class Error(
  code: String,
  details: Option[Map[String, Json]],
  message: String
)