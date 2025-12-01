package testapi.api

import java.util.UUID
import testapi.model.Error
import testapi.model.Pet

sealed trait GetPetResponse {
  def status: String
}

object GetPetResponse {
  /** Pet found */
  case class Status200(
    value: Pet,
    /** Whether the response was served from cache */
    xCacheStatus: Option[String],
    /** Unique request identifier for tracing */
    xRequestId: UUID
  ) extends GetPetResponse {
    override lazy val status: String = "200"
  }

  /** Pet not found */
  case class Status404(
    value: Error,
    /** Unique request identifier for tracing */
    xRequestId: UUID
  ) extends GetPetResponse {
    override lazy val status: String = "404"
  }
}