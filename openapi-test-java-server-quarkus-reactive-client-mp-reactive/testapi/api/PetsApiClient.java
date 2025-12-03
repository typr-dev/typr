package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.IllegalStateException;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.glassfish.jersey.media.multipart.FormDataParam;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;

@RegisterRestClient
@Path("/pets")
public interface PetsApiClient extends PetsApi {
  /** Create a pet */
  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  Uni<Response> createPetRaw(PetCreate body);

  /** Create a pet - handles response status codes */
  @Override
  default Uni<Response201400<Pet, Error>> createPet(PetCreate body) {
    return createPetRaw(body).onFailure(WebApplicationException.class).recoverWithItem((Throwable e) -> ((WebApplicationException) e).getResponse()).map((Response response) -> {
      if (response.getStatus() == 201) { return new Created(response.readEntity(Pet.class)); }
    else if (response.getStatus() == 400) { return new BadRequest(response.readEntity(Error.class)); }
    else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()); }
    });
  };

  /** Delete a pet */
  @DELETE
  @Path("/{petId}")
  Uni<Response> deletePetRaw(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  /** Delete a pet - handles response status codes */
  @Override
  default Uni<Response404Default<Error>> deletePet(
  
    /** The pet ID */
    String petId
  ) {
    return deletePetRaw(petId).onFailure(WebApplicationException.class).recoverWithItem((Throwable e) -> ((WebApplicationException) e).getResponse()).map((Response response) -> {
      if (response.getStatus() == 404) { return new NotFound(response.readEntity(Error.class)); }
    else { return new Default(response.getStatus(), response.readEntity(Error.class)); }
    });
  };

  /** Get a pet by ID */
  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  Uni<Response> getPetRaw(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  /** Get a pet by ID - handles response status codes */
  @Override
  default Uni<Response200404<Pet, Error>> getPet(
  
    /** The pet ID */
    String petId
  ) {
    return getPetRaw(petId).onFailure(WebApplicationException.class).recoverWithItem((Throwable e) -> ((WebApplicationException) e).getResponse()).map((Response response) -> {
      if (response.getStatus() == 200) { return new Ok(response.readEntity(Pet.class)); }
    else if (response.getStatus() == 404) { return new NotFound(response.readEntity(Error.class)); }
    else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()); }
    });
  };

  /** Get pet photo */
  @Override
  @GET
  @Path("/{petId}/photo")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  Uni<Void> getPetPhoto(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  /** List all pets */
  @Override
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  Uni<List<Pet>> listPets(
    /** Maximum number of pets to return */
    @QueryParam("limit") @DefaultValue("20") Optional<Integer> limit,
    /** Filter by status */
    @QueryParam("status") @DefaultValue("available") Optional<String> status
  );

  /** Upload a pet photo */
  @Override
  @POST
  @Path("/{petId}/photo")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  Uni<JsonNode> uploadPetPhoto(
    /** The pet ID */
    @PathParam("petId") String petId,
    /** Optional caption for the photo */
    @FormDataParam("caption") String caption,
    /** The photo file to upload */
    @FormDataParam("file") Byte[] file
  );
}