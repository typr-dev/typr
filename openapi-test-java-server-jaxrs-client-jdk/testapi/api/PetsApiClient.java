package testapi.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.IllegalStateException;
import java.lang.Void;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Optional;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;
import static typo.runtime.internal.stringInterpolator.str;

/** JDK HTTP Client implementation for PetsApi */
public class PetsApiClient implements PetsApi {
  HttpClient httpClient;;

  URI baseUri;;

  ObjectMapper objectMapper;;

  public PetsApiClient(
    /** JDK HTTP client for making HTTP requests */
    HttpClient httpClient,
    /** Base URI for API requests */
    URI baseUri,
    /** Jackson ObjectMapper for JSON serialization */
    ObjectMapper objectMapper
  ) {
    this.httpClient = httpClient;
    this.baseUri = baseUri;
    this.objectMapper = objectMapper;
  };

  /** Create a pet */
  @Override
  public Response201400<Pet, Error> createPet(PetCreate body) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets")).method("POST", BodyPublishers.ofString(objectMapper.writeValueAsString(body))).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    var statusCode = response.statusCode();
    if (statusCode == 201) { return new Created(objectMapper.readValue(response.body(), Pet.class)); }
    else if (statusCode == 400) { return new BadRequest(objectMapper.readValue(response.body(), Error.class)); }
    else { throw new IllegalStateException(str("Unexpected status code: ", java.lang.String.valueOf(statusCode), "")); }
  };

  /** Delete a pet */
  @Override
  public Void deletePet(
  
    /** The pet ID */
    PetId petId
  ) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets" + "/" + petId.toString())).method("DELETE", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    return null;
  };

  /** Get a pet by ID */
  @Override
  public Response200404<Pet, Error> getPet(
  
    /** The pet ID */
    PetId petId
  ) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets" + "/" + petId.toString())).method("GET", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    var statusCode = response.statusCode();
    if (statusCode == 200) { return new Ok(objectMapper.readValue(response.body(), Pet.class)); }
    else if (statusCode == 404) { return new NotFound(objectMapper.readValue(response.body(), Error.class)); }
    else { throw new IllegalStateException(str("Unexpected status code: ", java.lang.String.valueOf(statusCode), "")); }
  };

  /** Get pet photo */
  @Override
  public Void getPetPhoto(
  
    /** The pet ID */
    PetId petId
  ) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets" + "/" + petId.toString() + "/" + "photo")).method("GET", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    return null;
  };

  /** List all pets */
  @Override
  public List<Pet> listPets(
    /** Maximum number of pets to return */
    Optional<Integer> limit,
    /** Filter by status */
    Optional<String> status
  ) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets" + (limit.isPresent() ? "?limit=" + limit.get().toString() : "") + (status.isPresent() ? "&status=" + status.get().toString() : ""))).method("GET", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    return objectMapper.readValue(response.body(), new TypeReference<List<Pet>>() {});
  };

  /** Upload a pet photo */
  @Override
  public JsonNode uploadPetPhoto(
    /** The pet ID */
    PetId petId,
    /** Optional caption for the photo */
    String caption,
    /** The photo file to upload */
    Byte[] file
  ) throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "pets" + "/" + petId.toString() + "/" + "photo")).method("POST", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    return objectMapper.readValue(response.body(), JsonNode.class);
  };
}