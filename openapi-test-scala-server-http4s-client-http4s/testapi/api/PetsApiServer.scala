package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.HttpRoutes
import org.http4s.Response
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.dsl.io._

trait PetsApiServer extends PetsApi {
  /** Create a pet */
  override def createPet(body: PetCreate): IO[Response201400[Pet, Error]]

  /** Endpoint wrapper for createPet - handles response status codes */
  def createPetEndpoint(body: PetCreate): IO[Response[IO]] = {
    createPet(body).flatMap((response: testapi.api.Response201400[?, ?]) => (response: @unchecked) match {
      case r: testapi.api.Created[testapi.model.Pet] => r.toResponse
      case r: testapi.api.BadRequest[testapi.model.Error] => r.toResponse
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
  ): IO[Response[IO]] = {
    deletePet(petId).flatMap((response: testapi.api.Response404Default[?]) => (response: @unchecked) match {
      case r: testapi.api.NotFound[testapi.model.Error] => r.toResponse
      case r: testapi.api.Default => r.toResponse
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
  ): IO[Response[IO]] = {
    getPet(petId).flatMap((response: testapi.api.Response200404[?, ?]) => (response: @unchecked) match {
      case r: testapi.api.Ok[testapi.model.Pet] => r.toResponse
      case r: testapi.api.NotFound[testapi.model.Error] => r.toResponse
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

  /** HTTP routes for this API - wire this to your Http4s server */
  def routes: HttpRoutes[IO] = {
    org.http4s.HttpRoutes.of[cats.effect.IO] {
      case req @ POST -> Root / "pets" => req.as[testapi.model.PetCreate].flatMap(body => createPetEndpoint(body))
      case DELETE -> Root / "pets" / petId => deletePetEndpoint(petId)
      case GET -> Root / "pets" / petId => getPetEndpoint(petId)
      case req @ GET -> Root / "pets" => listPets(req.params.get("limit").flatMap(_.toIntOption), req.params.get("status")).flatMap(result => Ok(result))
    }
  }
}