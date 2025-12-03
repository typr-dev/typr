package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate

trait PetsApi {
  /** Create a pet */
  def createPet(body: PetCreate): IO[Response201400[Pet, Error]]

  /** Delete a pet */
  def deletePet(
    /** The pet ID */
    petId: String
  ): IO[Response404Default[Error]]

  /** Get a pet by ID */
  def getPet(
    /** The pet ID */
    petId: String
  ): IO[Response200404[Pet, Error]]

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