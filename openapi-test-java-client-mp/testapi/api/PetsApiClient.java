package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.ws.rs.core.MediaType;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.glassfish.jersey.media.multipart.FormDataParam;
import testapi.model.Pet;
import testapi.model.PetCreate;

@RegisterRestClient
@Path("/pets")
public sealed interface PetsApiClient extends PetsApi {
  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  /** Create a pet */
  CreatePetResponse createPet(PetCreate body);

  @DELETE
  @Path("/{petId}")
  /** Delete a pet */
  Void deletePet(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  /** Get a pet by ID */
  GetPetResponse getPet(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  @GET
  @Path("/{petId}/photo")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  /** Get pet photo */
  Void getPetPhoto(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  /** List all pets */
  List<Pet> listPets(
    /** Maximum number of pets to return */
    @QueryParam("limit") @DefaultValue("20") Optional<Integer> limit,
    /** Filter by status */
    @QueryParam("status") @DefaultValue("available") Optional<String> status
  );

  @POST
  @Path("/{petId}/photo")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  /** Upload a pet photo */
  JsonNode uploadPetPhoto(
    /** The pet ID */
    @PathParam("petId") String petId,
    /** Optional caption for the photo */
    @FormDataParam("caption") String caption,
    /** The photo file to upload */
    @FormDataParam("file") Byte[] file
  );
}