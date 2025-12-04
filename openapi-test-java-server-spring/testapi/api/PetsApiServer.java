package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.lang.IllegalStateException;
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
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;

@RestController
@RequestMapping("/pets")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public interface PetsApiServer extends PetsApi {
  /** Create a pet */
  @Override
  Response201400<Pet, Error> createPet(PetCreate body);

  /** Endpoint wrapper for createPet - handles response status codes */
  @PostMapping(value = { "/" }, consumes = { MediaType.APPLICATION_JSON_VALUE }, produces = { MediaType.APPLICATION_JSON_VALUE })
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  default ResponseEntity<?> createPetEndpoint(@RequestBody PetCreate body) {
    return switch (createPet(body)) {
      case Created r -> ResponseEntity.ok(r.value());
      case BadRequest r -> ResponseEntity.status(400).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  /** Delete a pet */
  @Override
  @DeleteMapping(value = { "/{petId}" })
  Void deletePet(
  
    /** The pet ID */
    @PathVariable("petId") PetId petId
  );

  /** Get a pet by ID */
  @Override
  Response200404<Pet, Error> getPet(
  
    /** The pet ID */
    PetId petId
  );

  /** Endpoint wrapper for getPet - handles response status codes */
  @GetMapping(value = { "/{petId}" }, produces = { MediaType.APPLICATION_JSON_VALUE })
  default ResponseEntity<?> getPetEndpoint(
  
    /** The pet ID */
    @PathVariable("petId") PetId petId
  ) {
    return switch (getPet(petId)) {
      case Ok r -> ResponseEntity.ok(r.value());
      case NotFound r -> ResponseEntity.status(404).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };

  /** Get pet photo */
  @Override
  @GetMapping(value = { "/{petId}/photo" }, produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
  Void getPetPhoto(
  
    /** The pet ID */
    @PathVariable("petId") PetId petId
  );

  /** List all pets */
  @Override
  @GetMapping(value = { "/" }, produces = { MediaType.APPLICATION_JSON_VALUE })
  List<Pet> listPets(
    /** Maximum number of pets to return */
    @RequestParam(name = "limit", required = false, defaultValue = "20") Optional<Integer> limit,
    /** Filter by status */
    @RequestParam(name = "status", required = false, defaultValue = "available") Optional<String> status
  );

  /** Upload a pet photo */
  @Override
  @PostMapping(value = { "/{petId}/photo" }, consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }, produces = { MediaType.APPLICATION_JSON_VALUE })
  JsonNode uploadPetPhoto(
    /** The pet ID */
    @PathVariable("petId") PetId petId,
    /** Optional caption for the photo */
    @RequestPart(name = "caption", required = false) String caption,
    /** The photo file to upload */
    @RequestPart(name = "file") Byte[] file
  );
}