package testapi.model



case class Owner(
  address: Option[Address],
  email: Option[String],
  id: String,
  name: String
)