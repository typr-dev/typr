package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Response
import testapi.model.Pet
import testapi.model.PetCreate

trait PetsApiClient extends PetsApi {
  /** Create a pet */
  def createPetRaw(body: PetCreate): IO[Response]

  /** Create a pet - handles response status codes */
  def createPet(body: PetCreate): IO[CreatePetResponse] = {
    createPetRaw(body).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 201) response.as[testapi.model.Pet].map(v => testapi.api.CreatePetResponse.Status201(v))
    else if (statusCode == 400) response.as[testapi.model.Error].map(v => testapi.api.CreatePetResponse.Status400(v))
    else cats.effect.IO.raiseError(new IllegalStateException(s"Unexpected status code: $statusCode"))
    } }
  }

  /** Delete a pet */
  def deletePetRaw(
    /** The pet ID */
    petId: String
  ): IO[Response]

  /** Delete a pet - handles response status codes */
  def deletePet(
    /** The pet ID */
    petId: String
  ): IO[DeletePetResponse] = {
    deletePetRaw(petId).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 404) response.as[testapi.model.Error].map(v => testapi.api.DeletePetResponse.Status404(v))
    else response.as[testapi.model.Error].map(v => testapi.api.DeletePetResponse.StatusDefault(statusCode, v))
    } }
  }

  /** Get a pet by ID */
  def getPetRaw(
    /** The pet ID */
    petId: String
  ): IO[Response]

  /** Get a pet by ID - handles response status codes */
  def getPet(
    /** The pet ID */
    petId: String
  ): IO[GetPetResponse] = {
    getPetRaw(petId).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 200) response.as[testapi.model.Pet].map(v => testapi.api.GetPetResponse.Status200(v))
    else if (statusCode == 404) response.as[testapi.model.Error].map(v => testapi.api.GetPetResponse.Status404(v))
    else cats.effect.IO.raiseError(new IllegalStateException(s"Unexpected status code: $statusCode"))
    } }
  }

  /** Get pet photo */
  def getPetPhoto(
    /** The pet ID */
    petId: String
  ): IO[Void]

  /** List all pets */
  def listPets(
    /** Maximum number of pets to return */
    limit: Option[Int],
    /** Filter by status */
    status: Option[String]
  ): IO[List[Pet]]

  /** Upload a pet photo */
  def uploadPetPhoto(
    /** The pet ID */
    petId: String,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array[Byte]
  ): IO[Json]
}