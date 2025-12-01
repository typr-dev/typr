package testapi.api;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.util.List;
import org.springframework.http.MediaType;
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
public sealed interface AnimalsApiServer extends AnimalsApi {
  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  /** List all animals (polymorphic) */
  List<Animal> listAnimals();
}