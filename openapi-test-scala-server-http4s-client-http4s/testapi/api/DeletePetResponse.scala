package testapi.api

import testapi.model.Error

sealed trait DeletePetResponse {
  def status: String
}

object DeletePetResponse {
  /** Pet not found */
  case class Status404(value: Error) extends DeletePetResponse {
    override lazy val status: String = "404"
  }

  /** Unexpected error */
  case class StatusDefault(
    /** HTTP status code to return */
    statusCode: Integer,
    value: Error
  ) extends DeletePetResponse {
    override lazy val status: String = "default"
  }
}