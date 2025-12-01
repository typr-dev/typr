package testapi.model



case class PetCreate(
  age: Option[Long],
  email: Option[String],
  name: String,
  status: Option[PetStatus],
  tags: Option[List[String]],
  website: Option[String]
)