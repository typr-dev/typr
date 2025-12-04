package testapi.api

import com.fasterxml.jackson.databind.JsonNode
import io.smallrye.mutiny.Uni
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.IllegalStateException
import java.lang.Void
import java.util.Optional
import kotlin.collections.List
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import testapi.model.PetId

interface PetsApiServer : PetsApi {
  /** Create a pet */
  override fun createPet(body: PetCreate): Uni<Response201400<Pet, Error>>

  /** Endpoint wrapper for createPet - handles response status codes */
  @POST
  @Path("/")
  @Consumes(value = [MediaType.APPLICATION_JSON])
  @Produces(value = [MediaType.APPLICATION_JSON])
  @SecurityRequirement(name = "oauth2", scopes = ["write:pets"])
  @SecurityRequirement(name = "apiKeyHeader")
  fun createPetEndpoint(body: PetCreate): Uni<Response> = createPet(body).map({ response: Response201400<*, *> -> when (val __r = response) {
    is Created<*> -> { val r = __r as Created<*>; Response.ok(r.value).build() }
    is BadRequest<*> -> { val r = __r as BadRequest<*>; Response.status(400).entity(r.value).build() }
    else -> throw IllegalStateException("Unexpected response type")
  } })

  /** Delete a pet */
  @DELETE
  @Path("/{petId}")
  override fun deletePet(
    /** The pet ID */
    petId: PetId
  ): Uni<Void>

  /** Get a pet by ID */
  override fun getPet(
    /** The pet ID */
    petId: PetId
  ): Uni<Response200404<Pet, Error>>

  /** Endpoint wrapper for getPet - handles response status codes */
  @GET
  @Path("/{petId}")
  @Produces(value = [MediaType.APPLICATION_JSON])
  fun getPetEndpoint(
    /** The pet ID */
    petId: PetId
  ): Uni<Response> = getPet(petId).map({ response: Response200404<*, *> -> when (val __r = response) {
    is Ok<*> -> { val r = __r as Ok<*>; Response.ok(r.value).build() }
    is NotFound<*> -> { val r = __r as NotFound<*>; Response.status(404).entity(r.value).build() }
    else -> throw IllegalStateException("Unexpected response type")
  } })

  /** Get pet photo */
  @GET
  @Path("/{petId}/photo")
  @Produces(value = [MediaType.APPLICATION_OCTET_STREAM])
  override fun getPetPhoto(
    /** The pet ID */
    petId: PetId
  ): Uni<Void>

  /** List all pets */
  @GET
  @Path("/")
  @Produces(value = [MediaType.APPLICATION_JSON])
  override fun listPets(
    /** Maximum number of pets to return */
    limit: Optional<Integer>,
    /** Filter by status */
    status: Optional<String>
  ): Uni<List<Pet>>

  /** Upload a pet photo */
  @POST
  @Path("/{petId}/photo")
  @Consumes(value = [MediaType.MULTIPART_FORM_DATA])
  @Produces(value = [MediaType.APPLICATION_JSON])
  override fun uploadPetPhoto(
    /** The pet ID */
    petId: PetId,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array<Byte>
  ): Uni<JsonNode>
}