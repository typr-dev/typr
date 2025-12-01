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
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.IllegalStateException
import java.lang.Void
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import kotlin.collections.List
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import testapi.api.CreatePetResponse.Status201
import testapi.api.CreatePetResponse.Status400
import testapi.api.DeletePetResponse.Status404
import testapi.api.DeletePetResponse.StatusDefault
import testapi.api.GetPetResponse.Status200
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate

@RegisterRestClient
@Path("/pets")
sealed interface PetsApiClient : PetsApi {
  /** Create a pet - handles response status codes */
  override fun createPet(body: PetCreate): Uni<CreatePetResponse> = createPetRaw(body).onFailure(WebApplicationException::class.java).recoverWithItem(object : Function<Throwable, Response> { override fun apply(e: Throwable): Response = (e as WebApplicationException).getResponse() }).map({ response: Response -> if (response.getStatus() == 201) { Status201(response.readEntity(Pet::class.java)) }
  else if (response.getStatus() == 400) { Status400(response.readEntity(Error::class.java)) }
  else { throw IllegalStateException("Unexpected status code: " + response.getStatus()) } })

  /** Create a pet */
  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = ["write:pets"])
  @SecurityRequirement(name = "apiKeyHeader")
  fun createPetRaw(body: PetCreate): Uni<Response>

  /** Delete a pet - handles response status codes */
  override fun deletePet(
    /** The pet ID */
    petId: String
  ): Uni<DeletePetResponse> = deletePetRaw(petId).onFailure(WebApplicationException::class.java).recoverWithItem(object : Function<Throwable, Response> { override fun apply(e: Throwable): Response = (e as WebApplicationException).getResponse() }).map({ response: Response -> if (response.getStatus() == 404) { Status404(response.readEntity(Error::class.java)) }
  else { StatusDefault(response.getStatus(), response.readEntity(Error::class.java)) } })

  /** Delete a pet */
  @DELETE
  @Path("/{petId}")
  fun deletePetRaw(
    /** The pet ID */
    petId: String
  ): Uni<Response>

  /** Get a pet by ID - handles response status codes */
  override fun getPet(
    /** The pet ID */
    petId: String
  ): Uni<GetPetResponse> = getPetRaw(petId).onFailure(WebApplicationException::class.java).recoverWithItem(object : Function<Throwable, Response> { override fun apply(e: Throwable): Response = (e as WebApplicationException).getResponse() }).map({ response: Response -> if (response.getStatus() == 200) { Status200(response.readEntity(Pet::class.java), Optional.ofNullable(response.getHeaderString("X-Cache-Status")), UUID.fromString(response.getHeaderString("X-Request-Id"))) }
  else if (response.getStatus() == 404) { testapi.api.GetPetResponse.Status404(response.readEntity(Error::class.java), UUID.fromString(response.getHeaderString("X-Request-Id"))) }
  else { throw IllegalStateException("Unexpected status code: " + response.getStatus()) } })

  /** Get pet photo */
  @GET
  @Path("/{petId}/photo")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  override fun getPetPhoto(
    /** The pet ID */
    petId: String
  ): Uni<Void>

  /** Get a pet by ID */
  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getPetRaw(
    /** The pet ID */
    petId: String
  ): Uni<Response>

  /** List all pets */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  override fun listPets(
    /** Maximum number of pets to return */
    limit: Optional<Integer>,
    /** Filter by status */
    status: Optional<String>
  ): Uni<List<Pet>>

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
  ): Uni<JsonNode>
}