package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import testapi.model.Error;
import testapi.model.Pet;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = GetPetResponse.Status200.class, name = "200"), @JsonSubTypes.Type(value = GetPetResponse.Status404.class, name = "404") })
public sealed interface GetPetResponse {
  /** Pet found */
  record Status200(@JsonProperty("value") Pet value) implements GetPetResponse {
    public Status200 withValue(Pet value) {
      return new Status200(value);
    };

    @Override
    public String status() {
      return "200";
    };
  };

  /** Pet not found */
  record Status404(@JsonProperty("value") Error value) implements GetPetResponse {
    public Status404 withValue(Error value) {
      return new Status404(value);
    };

    @Override
    public String status() {
      return "404";
    };
  };

  @JsonProperty("status")
  String status();
}