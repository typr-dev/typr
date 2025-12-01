package testapi.api

import testapi.model.Error
import testapi.model.Pet

sealed trait CreatePetResponse {
  def status: String
}

object CreatePetResponse {
  /** Pet created */
  case class Status201(value: Pet) extends CreatePetResponse {
    override lazy val status: String = "201"
  }

  /** Invalid input */
  case class Status400(value: Error) extends CreatePetResponse {
    override lazy val status: String = "400"
  }
}