package testapi.api

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.IllegalStateException
import java.lang.Void
import java.util.Optional
import kotlin.collections.List
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate

interface PetsApiClient : PetsApi {
  /** Create a pet - handles response status codes */
  override fun createPet(body: PetCreate): Response201400<Pet, Error> {
    try {
      val response: Response = createPetRaw(body);
      if (response.getStatus() == 201) { return Created(response.readEntity(Pet::class.java)) }
      else if (response.getStatus() == 400) { return BadRequest(response.readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + response.getStatus()) }
    } catch (e: WebApplicationException) {
      if (e.getResponse().getStatus() == 201) { return Created(e.getResponse().readEntity(Pet::class.java)) }
      else if (e.getResponse().getStatus() == 400) { return BadRequest(e.getResponse().readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()) }
    } 
  }

  /** Create a pet */
  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = ["write:pets"])
  @SecurityRequirement(name = "apiKeyHeader")
  fun createPetRaw(body: PetCreate): Response

  /** Delete a pet - handles response status codes */
  override fun deletePet(
    /** The pet ID */
    petId: String
  ): Response404Default<Error> {
    try {
      val response: Response = deletePetRaw(petId);
      if (response.getStatus() == 404) { return NotFound(response.readEntity(Error::class.java)) }
      else { return Default(response.getStatus(), response.readEntity(Error::class.java)) }
    } catch (e: WebApplicationException) {
      if (e.getResponse().getStatus() == 404) { return NotFound(e.getResponse().readEntity(Error::class.java)) }
      else { return Default(e.getResponse().getStatus(), e.getResponse().readEntity(Error::class.java)) }
    } 
  }

  /** Delete a pet */
  @DELETE
  @Path("/{petId}")
  fun deletePetRaw(
    /** The pet ID */
    petId: String
  ): Response

  /** Get a pet by ID - handles response status codes */
  override fun getPet(
    /** The pet ID */
    petId: String
  ): Response200404<Pet, Error> {
    try {
      val response: Response = getPetRaw(petId);
      if (response.getStatus() == 200) { return Ok(response.readEntity(Pet::class.java)) }
      else if (response.getStatus() == 404) { return NotFound(response.readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + response.getStatus()) }
    } catch (e: WebApplicationException) {
      if (e.getResponse().getStatus() == 200) { return Ok(e.getResponse().readEntity(Pet::class.java)) }
      else if (e.getResponse().getStatus() == 404) { return NotFound(e.getResponse().readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()) }
    } 
  }

  /** Get pet photo */
  @GET
  @Path("/{petId}/photo")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  override fun getPetPhoto(
    /** The pet ID */
    petId: String
  ): Void

  /** Get a pet by ID */
  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getPetRaw(
    /** The pet ID */
    petId: String
  ): Response

  /** List all pets */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  override fun listPets(
    /** Maximum number of pets to return */
    limit: Optional<Integer>,
    /** Filter by status */
    status: Optional<String>
  ): List<Pet>

  /** Upload a pet photo */
  @POST
  @Path("/{petId}/photo")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  override fun uploadPetPhoto(
    /** The pet ID */
    petId: String,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array<Byte>
  ): JsonNode
}