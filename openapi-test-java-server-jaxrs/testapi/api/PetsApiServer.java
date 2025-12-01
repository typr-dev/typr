package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
import jakarta.ws.rs.core.Response;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.media.multipart.FormDataParam;
import testapi.api.CreatePetResponse.Status201;
import testapi.api.CreatePetResponse.Status400;
import testapi.api.GetPetResponse.Status200;
import testapi.api.GetPetResponse.Status404;
import testapi.model.Pet;
import testapi.model.PetCreate;

@Path("/pets")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public sealed interface PetsApiServer extends PetsApi {
  /** Create a pet */
  CreatePetResponse createPet(PetCreate body);

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  /** Endpoint wrapper for createPet - handles response status codes */
  default Response createPetEndpoint(PetCreate body) {
    return switch (createPet(body)) {
      case Status201 r -> Response.ok(r.value()).build();
      case Status400 r -> Response.status(400).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  @DELETE
  @Path("/{petId}")
  /** Delete a pet */
  Void deletePet(
  
    /** The pet ID */
    @PathParam("petId") String petId
  );

  /** Get a pet by ID */
  GetPetResponse getPet(
  
    /** The pet ID */
    String petId
  );

  @GET
  @Path("/{petId}")
  @Produces(MediaType.APPLICATION_JSON)
  /** Endpoint wrapper for getPet - handles response status codes */
  default Response getPetEndpoint(
  
    /** The pet ID */
    @PathParam("petId") String petId
  ) {
    return switch (getPet(petId)) {
      case Status200 r -> Response.ok(r.value()).build();
      case Status404 r -> Response.status(404).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    };
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