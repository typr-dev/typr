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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.glassfish.jersey.media.multipart.FormDataParam;
import testapi.api.CreatePetResponse.Status201;
import testapi.api.CreatePetResponse.Status400;
import testapi.api.DeletePetResponse.Status404;
import testapi.api.DeletePetResponse.StatusDefault;
import testapi.api.GetPetResponse.Status200;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;

@RegisterRestClient
@Path("/pets")
public sealed interface PetsApiClient extends PetsApi {
  /** Create a pet - handles response status codes */
  default CreatePetResponse createPet(PetCreate body) {
    try {
        Response response = createPetRaw(body);
        if (response.getStatus() == 201) { return new Status201(response.readEntity(Pet.class)); }
        else if (response.getStatus() == 400) { return new Status400(response.readEntity(Error.class)); }
        else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()); }
      } catch (WebApplicationException e) {
        if (e.getResponse().getStatus() == 201) { return new Status201(e.getResponse().readEntity(Pet.class)); }
        else if (e.getResponse().getStatus() == 400) { return new Status400(e.getResponse().readEntity(Error.class)); }
        else { throw new IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()); }
      } ;
    return null;
  };

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  /** Create a pet */
  Response createPetRaw(PetCreate body);

  /** Delete a pet - handles response status codes */
  default DeletePetResponse deletePet(
  
    /** The pet ID */
    String petId
  ) {
    try {
        Response response = deletePetRaw(petId);
        if (response.getStatus() == 404) { return new Status404(response.readEntity(Error.class)); }
        else { return new StatusDefault(response.getStatus(), response.readEntity(Error.class)); }
      } catch (WebApplicationException e) {
        if (e.getResponse().getStatus() == 404) { return new Status404(e.getResponse().readEntity(Error.class)); }
        else { return new StatusDefault(e.getResponse().getStatus(), e.getResponse().readEntity(Error.class)); }
      } ;
    return null;
  };

  @DELETE
  @Path("/{petId}")
  /** Delete a pet */
  Response deletePetRaw(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  /** Get a pet by ID - handles response status codes */
  default GetPetResponse getPet(
  
    /** The pet ID */
    String petId
  ) {
    try {
        Response response = getPetRaw(petId);
        if (response.getStatus() == 200) { return new Status200(response.readEntity(Pet.class)); }
        else if (response.getStatus() == 404) { return new testapi.api.GetPetResponse.Status404(response.readEntity(Error.class)); }
        else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()); }
      } catch (WebApplicationException e) {
        if (e.getResponse().getStatus() == 200) { return new Status200(e.getResponse().readEntity(Pet.class)); }
        else if (e.getResponse().getStatus() == 404) { return new testapi.api.GetPetResponse.Status404(e.getResponse().readEntity(Error.class)); }
        else { throw new IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()); }
      } ;
    return null;
  };

  @GET
  @Path("/{petId}/photo")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  /** Get pet photo */
  Void getPetPhoto(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  /** Get a pet by ID */
  Response getPetRaw(
  
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