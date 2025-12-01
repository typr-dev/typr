package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Response
import testapi.model.Pet
import testapi.model.PetCreate

trait PetsApiServer extends PetsApi {
  /** Create a pet */
  def createPet(body: PetCreate): IO[CreatePetResponse]

  /** Endpoint wrapper for createPet - handles response status codes */
  def createPetEndpoint(body: PetCreate): IO[Response] = {
    createPet(body).map((response: testapi.api.CreatePetResponse) => response match {
      case r: testapi.api.CreatePetResponse.Status201 => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.CreatePetResponse.Status400 => org.http4s.Response.apply(org.http4s.Status.fromInt(400).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case _ => throw new IllegalStateException("Unexpected response type: " + response.getClass())
    })
  }

  /** Delete a pet */
  def deletePet(
    /** The pet ID */
    petId: String
  ): IO[DeletePetResponse]

  /** Endpoint wrapper for deletePet - handles response status codes */
  def deletePetEndpoint(
    /** The pet ID */
    petId: String
  ): IO[Response] = {
    deletePet(petId).map((response: testapi.api.DeletePetResponse) => response match {
      case r: testapi.api.DeletePetResponse.Status404 => org.http4s.Response.apply(org.http4s.Status.fromInt(404).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case r: testapi.api.DeletePetResponse.StatusDefault => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case _ => throw new IllegalStateException("Unexpected response type: " + response.getClass())
    })
  }

  /** Get a pet by ID */
  def getPet(
    /** The pet ID */
    petId: String
  ): IO[GetPetResponse]

  /** Endpoint wrapper for getPet - handles response status codes */
  def getPetEndpoint(
    /** The pet ID */
    petId: String
  ): IO[Response] = {
    getPet(petId).map((response: testapi.api.GetPetResponse) => response match {
      case r: testapi.api.GetPetResponse.Status200 => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.GetPetResponse.Status404 => org.http4s.Response.apply(org.http4s.Status.fromInt(404).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case _ => throw new IllegalStateException("Unexpected response type: " + response.getClass())
    })
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