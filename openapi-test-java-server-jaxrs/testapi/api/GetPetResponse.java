package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Optional;
import java.util.UUID;
import testapi.api.GetPetResponse.Status200;
import testapi.api.GetPetResponse.Status404;
import testapi.model.Error;
import testapi.model.Pet;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes(value = { @Type(value = Status200.class, name = "200"), @Type(value = Status404.class, name = "404") })
public sealed interface GetPetResponse {
  /** Pet found */
  record Status200(
    @JsonProperty("value") Pet value,
    /** Whether the response was served from cache */
    @JsonProperty("X-Cache-Status") Optional<String> xCacheStatus,
    /** Unique request identifier for tracing */
    @JsonProperty("X-Request-Id") UUID xRequestId
  ) implements GetPetResponse {
    public Status200 withValue(Pet value) {
      return new Status200(value, xCacheStatus, xRequestId);
    };

    /** Whether the response was served from cache */
    public Status200 withXCacheStatus(Optional<String> xCacheStatus) {
      return new Status200(value, xCacheStatus, xRequestId);
    };

    /** Unique request identifier for tracing */
    public Status200 withXRequestId(UUID xRequestId) {
      return new Status200(value, xCacheStatus, xRequestId);
    };

    @Override
    public String status() {
      return "200";
    };
  };

  /** Pet not found */
  record Status404(
    @JsonProperty("value") Error value,
    /** Unique request identifier for tracing */
    @JsonProperty("X-Request-Id") UUID xRequestId
  ) implements GetPetResponse {
    public Status404 withValue(Error value) {
      return new Status404(value, xRequestId);
    };

    /** Unique request identifier for tracing */
    public Status404 withXRequestId(UUID xRequestId) {
      return new Status404(value, xRequestId);
    };

    @Override
    public String status() {
      return "404";
    };
  };

  @JsonProperty("status")
  String status();
}