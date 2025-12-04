package testapi.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.IllegalStateException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import testapi.model.Animal;
import testapi.model.Error;
import static typo.runtime.internal.stringInterpolator.str;

/** JDK HTTP Client implementation for AnimalsApi */
public class AnimalsApiClient implements AnimalsApi {
  HttpClient httpClient;;

  URI baseUri;;

  ObjectMapper objectMapper;;

  public AnimalsApiClient(
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

  /** List all animals (polymorphic) */
  @Override
  public Response2004XX5XX<List<Animal>> listAnimals() throws java.lang.Exception {
    var request = HttpRequest.newBuilder(URI.create(baseUri.toString() + "/" + "animals")).method("GET", BodyPublishers.noBody()).header("Content-Type", "application/json").header("Accept", "application/json").build();
    var response = httpClient.send(request, BodyHandlers.ofString());
    var statusCode = response.statusCode();
    if (statusCode == 200) { return new Ok(objectMapper.readValue(response.body(), new TypeReference<List<Animal>>() {})); }
    else if (statusCode >= 400 && statusCode < 500) { return new ClientError4XX(statusCode, objectMapper.readValue(response.body(), Error.class)); }
    else if (statusCode >= 500 && statusCode < 600) { return new ServerError5XX(statusCode, objectMapper.readValue(response.body(), Error.class)); }
    else { throw new IllegalStateException(str("Unexpected status code: ", java.lang.String.valueOf(statusCode), "")); }
  };
}