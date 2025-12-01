package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import testapi.model.Error;
import testapi.model.Pet;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = CreatePetResponse.Status201.class, name = "201"), @JsonSubTypes.Type(value = CreatePetResponse.Status400.class, name = "400") })
public sealed interface CreatePetResponse {
  /** Pet created */
  record Status201(@JsonProperty("value") Pet value) implements CreatePetResponse {
    public Status201 withValue(Pet value) {
      return new Status201(value);
    };

    @Override
    public String status() {
      return "201";
    };
  };

  /** Invalid input */
  record Status400(@JsonProperty("value") Error value) implements CreatePetResponse {
    public Status400 withValue(Error value) {
      return new Status400(value);
    };

    @Override
    public String status() {
      return "400";
    };
  };

  @JsonProperty("status")
  String status();
}