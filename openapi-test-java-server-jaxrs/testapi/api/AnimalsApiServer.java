package testapi.api;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.IllegalStateException;
import testapi.api.ListAnimalsResponse.Status200;
import testapi.api.ListAnimalsResponse.Status4XX;
import testapi.api.ListAnimalsResponse.Status5XX;

@Path("/animals")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public sealed interface AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  @Override
  ListAnimalsResponse listAnimals();

  /** Endpoint wrapper for listAnimals - handles response status codes */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  default Response listAnimalsEndpoint() {
    return switch (listAnimals()) {
      case Status200 r -> Response.ok(r.value()).build();
      case Status4XX r -> Response.status(r.statusCode()).entity(r.value()).build();
      case Status5XX r -> Response.status(r.statusCode()).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    };
  };
}