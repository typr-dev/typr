package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import testapi.api.CreatePetResponse.Status201;
import testapi.api.CreatePetResponse.Status400;
import testapi.api.DeletePetResponse.Status404;
import testapi.api.DeletePetResponse.StatusDefault;
import testapi.api.GetPetResponse.Status200;
import testapi.model.Pet;
import testapi.model.PetCreate;

@RestController
@RequestMapping("/pets")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public sealed interface PetsApiServer extends PetsApi {
  /** Create a pet */
  CreatePetResponse createPet(PetCreate body);

  @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  /** Endpoint wrapper for createPet - handles response status codes */
  default ResponseEntity createPetEndpoint(@RequestBody PetCreate body) {
    return switch (createPet(body)) {
      case Status201 r -> ResponseEntity.ok(r.value());
      case Status400 r -> ResponseEntity.status(400).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  /** Delete a pet */
  DeletePetResponse deletePet(
  
    /** The pet ID */
    String petId
  );

  @DeleteMapping(value = "/{petId}")
  /** Endpoint wrapper for deletePet - handles response status codes */
  default ResponseEntity deletePetEndpoint(
  
    /** The pet ID */
    @PathVariable("petId") String petId
  ) {
    return switch (deletePet(petId)) {
      case Status404 r -> ResponseEntity.status(404).body(r.value());
      case StatusDefault r -> ResponseEntity.status(r.statusCode()).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  /** Get a pet by ID */
  GetPetResponse getPet(
  
    /** The pet ID */
    String petId
  );

  @GetMapping(value = "/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
  /** Endpoint wrapper for getPet - handles response status codes */
  default ResponseEntity getPetEndpoint(
  
    /** The pet ID */
    @PathVariable("petId") String petId
  ) {
    return switch (getPet(petId)) {
      case Status200 r -> ResponseEntity.ok(r.value());
      case testapi.api.GetPetResponse.Status404 r -> ResponseEntity.status(404).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  @GetMapping(value = "/{petId}/photo", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  /** Get pet photo */
  Void getPetPhoto(
  
    /** The pet ID */
    @PathVariable("petId") String petId
  );

  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  /** List all pets */
  List<Pet> listPets(
    /** Maximum number of pets to return */
    @RequestParam(name = "limit", required = false, defaultValue = "20") Optional<Integer> limit,
    /** Filter by status */
    @RequestParam(name = "status", required = false, defaultValue = "available") Optional<String> status
  );

  @PostMapping(value = "/{petId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  /** Upload a pet photo */
  JsonNode uploadPetPhoto(
    /** The pet ID */
    @PathVariable("petId") String petId,
    /** Optional caption for the photo */
    @RequestPart(name = "caption", required = false) String caption,
    /** The photo file to upload */
    @RequestPart(name = "file") Byte[] file
  );
}