package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Response
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate

trait PetsApiServer extends PetsApi {
  /** Create a pet */
  override def createPet(body: PetCreate): IO[Response201400[Pet, Error]]

  /** Endpoint wrapper for createPet - handles response status codes */
  def createPetEndpoint(body: PetCreate): IO[Response] = {
    createPet(body).map((response: testapi.api.Response201400) => response match {
      case r: testapi.api.Created => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.BadRequest => org.http4s.Response.apply(org.http4s.Status.fromInt(400).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
    })
  }

  /** Delete a pet */
  override def deletePet(
    /** The pet ID */
    petId: String
  ): IO[Response404Default[Error]]

  /** Endpoint wrapper for deletePet - handles response status codes */
  def deletePetEndpoint(
    /** The pet ID */
    petId: String
  ): IO[Response] = {
    deletePet(petId).map((response: testapi.api.Response404Default) => response match {
      case r: testapi.api.NotFound => org.http4s.Response.apply(org.http4s.Status.fromInt(404).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case r: testapi.api.Default => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
    })
  }

  /** Get a pet by ID */
  override def getPet(
    /** The pet ID */
    petId: String
  ): IO[Response200404[Pet, Error]]

  /** Endpoint wrapper for getPet - handles response status codes */
  def getPetEndpoint(
    /** The pet ID */
    petId: String
  ): IO[Response] = {
    getPet(petId).map((response: testapi.api.Response200404) => response match {
      case r: testapi.api.Ok => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.NotFound => org.http4s.Response.apply(org.http4s.Status.fromInt(404).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
    })
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