package testapi.api;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import testapi.api.ListAnimalsResponse.Status200;
import testapi.api.ListAnimalsResponse.Status4XX;
import testapi.api.ListAnimalsResponse.Status5XX;

@RestController
@RequestMapping("/animals")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public sealed interface AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  ListAnimalsResponse listAnimals();

  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  /** Endpoint wrapper for listAnimals - handles response status codes */
  default ResponseEntity listAnimalsEndpoint() {
    return switch (listAnimals()) {
      case Status200 r -> ResponseEntity.ok(r.value());
      case Status4XX r -> ResponseEntity.status(r.statusCode()).body(r.value());
      case Status5XX r -> ResponseEntity.status(r.statusCode()).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };
}