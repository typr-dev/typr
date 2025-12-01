package testapi.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import testapi.model.Error
import testapi.model.Pet

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = GetPetResponse.Status200.class, name = "200"), @JsonSubTypes.Type(value = GetPetResponse.Status404.class, name = "404") })
sealed trait GetPetResponse {
  def status: String
}

object GetPetResponse {
  /** Pet found */
  case class Status200(value: Pet) extends GetPetResponse {
    override lazy val status: String = "200"
  }

  /** Pet not found */
  case class Status404(value: Error) extends GetPetResponse {
    override lazy val status: String = "404"
  }
}