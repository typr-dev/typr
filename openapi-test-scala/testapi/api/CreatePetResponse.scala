package testapi.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import testapi.model.Error
import testapi.model.Pet

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = CreatePetResponse.Status201.class, name = "201"), @JsonSubTypes.Type(value = CreatePetResponse.Status400.class, name = "400") })
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