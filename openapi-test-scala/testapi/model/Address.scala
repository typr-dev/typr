package testapi.model



case class Address(
  city: Option[String],
  country: Option[String],
  street: Option[String],
  zipCode: Option[String]
)