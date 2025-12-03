package testapi.api;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.lang.IllegalStateException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import testapi.model.Animal;

@RestController
@RequestMapping("/animals")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public interface AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  @Override
  Response2004XX5XX<List<Animal>> listAnimals();

  /** Endpoint wrapper for listAnimals - handles response status codes */
  @GetMapping(value = { "/" }, produces = { MediaType.APPLICATION_JSON_VALUE })
  default ResponseEntity<?> listAnimalsEndpoint() {
    return switch (listAnimals) {
      case Ok r -> ResponseEntity.ok(r.value());
      case ClientError4XX r -> ResponseEntity.status(r.statusCode()).body(r.value());
      case ServerError5XX r -> ResponseEntity.status(r.statusCode()).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };
}