package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import testapi.api.DeletePetResponse.Status404;
import testapi.api.DeletePetResponse.StatusDefault;
import testapi.model.Error;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = Status404.class, name = "404"), @JsonSubTypes.Type(value = StatusDefault.class, name = "default") })
public sealed interface DeletePetResponse {
  /** Pet not found */
  record Status404(@JsonProperty("value") Error value) implements DeletePetResponse {
    public Status404 withValue(Error value) {
      return new Status404(value);
    };

    @Override
    public String status() {
      return "404";
    };
  };

  /** Unexpected error */
  record StatusDefault(
    /** HTTP status code to return */
    @JsonProperty("statusCode") Integer statusCode,
    @JsonProperty("value") Error value
  ) implements DeletePetResponse {
    /** HTTP status code to return */
    public StatusDefault withStatusCode(Integer statusCode) {
      return new StatusDefault(statusCode, value);
    };

    public StatusDefault withValue(Error value) {
      return new StatusDefault(statusCode, value);
    };

    @Override
    public String status() {
      return "default";
    };
  };

  @JsonProperty("status")
  String status();
}