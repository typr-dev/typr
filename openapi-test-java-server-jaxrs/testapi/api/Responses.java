package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.Void;
import testapi.model.Error;

/** Response type for: 201, 400 */
public sealed interface Response201400<T201, T400> permits Created, BadRequest {
  @JsonProperty("status")
  String status();
}

/** Response type for: 404, default */
public sealed interface Response404Default<T404> permits NotFound, Default {
  @JsonProperty("status")
  String status();
}

/** Response type for: 200, 404 */
public sealed interface Response200404<T200, T404> permits Ok, NotFound {
  @JsonProperty("status")
  String status();
}

/** Response type for: 200, 4XX, 5XX */
public sealed interface Response2004XX5XX<T200> permits Ok, ClientError4XX, ServerError5XX {
  @JsonProperty("status")
  String status();
}

/** HTTP 400 response */
public record BadRequest<T>(@JsonProperty("value") T value) implements Response201400<Void, T> {
  public BadRequest<T> withValue(T value) {
    return new BadRequest<>(value);
  };

  @Override
  public String status() {
    return "400";
  };
}

/** HTTP 201 response */
public record Created<T>(@JsonProperty("value") T value) implements Response201400<T, Void> {
  public Created<T> withValue(T value) {
    return new Created<>(value);
  };

  @Override
  public String status() {
    return "201";
  };
}

/** HTTP default response */
public record Default(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response404Default<Void> {
  /** HTTP status code */
  public Default withStatusCode(Integer statusCode) {
    return new Default(statusCode, value);
  };

  public Default withValue(Error value) {
    return new Default(statusCode, value);
  };

  @Override
  public String status() {
    return "default";
  };
}

/** HTTP 5XX response */
public record ServerError5XX(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response2004XX5XX<Void> {
  /** HTTP status code */
  public ServerError5XX withStatusCode(Integer statusCode) {
    return new ServerError5XX(statusCode, value);
  };

  public ServerError5XX withValue(Error value) {
    return new ServerError5XX(statusCode, value);
  };

  @Override
  public String status() {
    return "5XX";
  };
}

/** HTTP 200 response */
public record Ok<T>(@JsonProperty("value") T value) implements Response200404<T, Void>, Response2004XX5XX<T> {
  public Ok<T> withValue(T value) {
    return new Ok<>(value);
  };

  @Override
  public String status() {
    return "200";
  };
}

/** HTTP 404 response */
public record NotFound<T>(@JsonProperty("value") T value) implements Response404Default<T>, Response200404<Void, T> {
  public NotFound<T> withValue(T value) {
    return new NotFound<>(value);
  };

  @Override
  public String status() {
    return "404";
  };
}

/** HTTP 4XX response */
public record ClientError4XX(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response2004XX5XX<Void> {
  /** HTTP status code */
  public ClientError4XX withStatusCode(Integer statusCode) {
    return new ClientError4XX(statusCode, value);
  };

  public ClientError4XX withValue(Error value) {
    return new ClientError4XX(statusCode, value);
  };

  @Override
  public String status() {
    return "4XX";
  };
}