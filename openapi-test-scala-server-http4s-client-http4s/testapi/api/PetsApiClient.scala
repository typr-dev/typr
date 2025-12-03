package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Response
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

trait PetsApiClient extends PetsApi {
  /** Create a pet */
  def createPetRaw(body: PetCreate): IO[Response[IO]]

  /** Create a pet - handles response status codes */
  override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = {
    createPetRaw(body).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 201) response.as[testapi.model.Pet].map(v => testapi.api.Created(v))
    else if (statusCode == 400) response.as[testapi.model.Error].map(v => testapi.api.BadRequest(v))
    else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: statusCode"))
    } }
  }

  /** Delete a pet */
  def deletePetRaw(
    /** The pet ID */
    petId: String
  ): IO[Response[IO]]

  /** Delete a pet - handles response status codes */
  override def deletePet(
    /** The pet ID */
    petId: String
  ): IO[Response404Default[Error]] = {
    deletePetRaw(petId).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 404) response.as[testapi.model.Error].map(v => testapi.api.NotFound(v))
    else response.as[testapi.model.Error].map(v => testapi.api.Default(statusCode, v))
    } }
  }

  /** Get a pet by ID */
  def getPetRaw(
    /** The pet ID */
    petId: String
  ): IO[Response[IO]]

  /** Get a pet by ID - handles response status codes */
  override def getPet(
    /** The pet ID */
    petId: String
  ): IO[Response200404[Pet, Error]] = {
    getPetRaw(petId).flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 200) response.as[testapi.model.Pet].map(v => testapi.api.Ok(v))
    else if (statusCode == 404) response.as[testapi.model.Error].map(v => testapi.api.NotFound(v))
    else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: statusCode"))
    } }
  }

  /** Get pet photo */
  override def getPetPhoto(
    /** The pet ID */
    petId: String
  ): IO[Void]

  /** List all pets */
  override def listPets(
    /** Maximum number of pets to return */
    limit: Option[Int],
    /** Filter by status */
    status: Option[String]
  ): IO[List[Pet]]

  /** Upload a pet photo */
  override def uploadPetPhoto(
    /** The pet ID */
    petId: String,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array[Byte]
  ): IO[Json]
}