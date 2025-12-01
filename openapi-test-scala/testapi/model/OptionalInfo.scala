package testapi.model



case class OptionalInfo(
  optional_field: Option[String],
  optional_with_default: Option[String],
  required_field: Option[String]
)