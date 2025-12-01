package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import testapi.api.ListAnimalsResponse.Status200;
import testapi.api.ListAnimalsResponse.Status4XX;
import testapi.api.ListAnimalsResponse.Status5XX;
import testapi.model.Animal;
import testapi.model.Error;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({ @JsonSubTypes.Type(value = Status200.class, name = "200"), @JsonSubTypes.Type(value = Status4XX.class, name = "4XX"), @JsonSubTypes.Type(value = Status5XX.class, name = "5XX") })
public sealed interface ListAnimalsResponse {
  /** A list of animals */
  record Status200(@JsonProperty("value") List<Animal> value) implements ListAnimalsResponse {
    public Status200 withValue(List<Animal> value) {
      return new Status200(value);
    };

    @Override
    public String status() {
      return "200";
    };
  };

  /** Client error (any 4xx status) */
  record Status4XX(
    /** HTTP status code to return */
    @JsonProperty("statusCode") Integer statusCode,
    @JsonProperty("value") Error value
  ) implements ListAnimalsResponse {
    /** HTTP status code to return */
    public Status4XX withStatusCode(Integer statusCode) {
      return new Status4XX(statusCode, value);
    };

    public Status4XX withValue(Error value) {
      return new Status4XX(statusCode, value);
    };

    @Override
    public String status() {
      return "4XX";
    };
  };

  /** Server error (any 5xx status) */
  record Status5XX(
    /** HTTP status code to return */
    @JsonProperty("statusCode") Integer statusCode,
    @JsonProperty("value") Error value
  ) implements ListAnimalsResponse {
    /** HTTP status code to return */
    public Status5XX withStatusCode(Integer statusCode) {
      return new Status5XX(statusCode, value);
    };

    public Status5XX withValue(Error value) {
      return new Status5XX(statusCode, value);
    };

    @Override
    public String status() {
      return "5XX";
    };
  };

  @JsonProperty("status")
  String status();
}