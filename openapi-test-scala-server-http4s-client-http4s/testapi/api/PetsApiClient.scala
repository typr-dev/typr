package testapi.api

import cats.effect.IO
import io.circe.Json
import java.lang.Void
import org.http4s.Uri
import org.http4s.client.Client
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import testapi.model.PetId
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

/** Http4s client implementation for PetsApi */
class PetsApiClient(
  /** Http4s client for making HTTP requests */
  val client: Client[IO],
  /** Base URI for API requests */
  val baseUri: Uri
) extends PetsApi {
  /** Create a pet */
  override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.POST, baseUri / "pets").withEntity(body)
    client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 201) response.as[testapi.model.Pet].map(v => testapi.api.Created(v))
        else if (statusCode == 400) response.as[testapi.model.Error].map(v => testapi.api.BadRequest(v))
        else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: ${statusCode}"))
    }
  }

  /** Delete a pet */
  override def deletePet(
    /** The pet ID */
    petId: PetId
  ): IO[Void] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.DELETE, baseUri / "pets" / petId)
    client.status(request).as(null.asInstanceOf[java.lang.Void])
  }

  /** Get a pet by ID */
  override def getPet(
    /** The pet ID */
    petId: PetId
  ): IO[Response200404[Pet, Error]] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.GET, baseUri / "pets" / petId)
    client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 200) response.as[testapi.model.Pet].map(v => testapi.api.Ok(v))
        else if (statusCode == 404) response.as[testapi.model.Error].map(v => testapi.api.NotFound(v))
        else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: ${statusCode}"))
    }
  }

  /** Get pet photo */
  override def getPetPhoto(
    /** The pet ID */
    petId: PetId
  ): IO[Void] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.GET, baseUri / "pets" / petId / "photo")
    client.status(request).as(null.asInstanceOf[java.lang.Void])
  }

  /** List all pets */
  override def listPets(
    /** Maximum number of pets to return */
    limit: Option[Int],
    /** Filter by status */
    status: Option[String]
  ): IO[List[Pet]] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.GET, (baseUri / "pets").withOptionQueryParam("limit", limit).withOptionQueryParam("status", status))
    client.expect[scala.List[testapi.model.Pet]](request)
  }

  /** Upload a pet photo */
  override def uploadPetPhoto(
    /** The pet ID */
    petId: PetId,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array[Byte]
  ): IO[Json] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.POST, baseUri / "pets" / petId / "photo")
    client.expect[io.circe.Json](request)
  }
}