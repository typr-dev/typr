package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Response
import testapi.model.Pet
import testapi.model.PetCreate

sealed trait PetsApiClient extends PetsApi {
  /** Create a pet */
  def createPetRaw(body: PetCreate): IO[Response]

  /** Create a pet - handles response status codes */
  def createPet(body: PetCreate): IO[CreatePetResponse] = {
    createPetRaw(body).map((response: org.http4s.Response) => if (response.status.code == 201) return new testapi.api.CreatePetResponse.Status201(response.as[testapi.model.Pet]);
    else if (response.status.code == 400) return new testapi.api.CreatePetResponse.Status400(response.as[testapi.model.Error]);
    else throw new IllegalStateException("Unexpected status code: " + response.status.code);).onFailure(org.http4s.client.UnexpectedStatus.class).recoverWithItem((e: org.http4s.client.UnexpectedStatus) => if (e.response.status.code == 201) return new testapi.api.CreatePetResponse.Status201(e.response.as[testapi.model.Pet]);
    else if (e.response.status.code == 400) return new testapi.api.CreatePetResponse.Status400(e.response.as[testapi.model.Error]);
    else throw new IllegalStateException("Unexpected status code: " + e.response.status.code);)
  }

  /** Delete a pet */
  def deletePet(
    /** The pet ID */
    petId: String
  ): IO[Void]

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
    getPetRaw(petId).map((response: org.http4s.Response) => if (response.status.code == 200) return new testapi.api.GetPetResponse.Status200(response.as[testapi.model.Pet]);
    else if (response.status.code == 404) return new testapi.api.GetPetResponse.Status404(response.as[testapi.model.Error]);
    else throw new IllegalStateException("Unexpected status code: " + response.status.code);).onFailure(org.http4s.client.UnexpectedStatus.class).recoverWithItem((e: org.http4s.client.UnexpectedStatus) => if (e.response.status.code == 200) return new testapi.api.GetPetResponse.Status200(e.response.as[testapi.model.Pet]);
    else if (e.response.status.code == 404) return new testapi.api.GetPetResponse.Status404(e.response.as[testapi.model.Error]);
    else throw new IllegalStateException("Unexpected status code: " + e.response.status.code);)
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