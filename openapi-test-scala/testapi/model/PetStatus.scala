package testapi.model



/** Pet availability status */

sealed abstract class PetStatus(val value: String)

object PetStatus {
  
  def apply(str: String): Either[String, PetStatus] =
    ByName.get(str).toRight(s"'$str' does not match any of the following legal values: $Names")
  def force(str: String): PetStatus =
    apply(str) match {
      case Left(msg) => sys.error(msg)
      case Right(value) => value
    }
  case object available extends PetStatus("available")

  case object pending extends PetStatus("pending")

  case object sold extends PetStatus("sold")
  val All: List[PetStatus] = List(available, pending, sold)
  val Names: String = All.map(_.value).mkString(", ")
  val ByName: Map[String, PetStatus] = All.map(x => (x.value, x)).toMap
}