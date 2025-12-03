package testapi.api;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.IllegalStateException;
import java.util.List;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import testapi.api.ListAnimalsResponse;
import testapi.model.Animal;
import testapi.model.Error;

@RegisterRestClient
@Path("/animals")
public interface AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  Uni<Response> listAnimalsRaw();

  /** List all animals (polymorphic) - handles response status codes */
  @Override
  default Uni<ListAnimalsResponse> listAnimals() {
    return listAnimalsRaw().onFailure(WebApplicationException.class).recoverWithItem((Throwable e) -> ((WebApplicationException) e).getResponse()).map((Response response) -> if (response.getStatus() == 200) { new Ok(response.readEntity(new GenericType<List<Animal>>() {})) }
    else if (response.getStatus() >= 400 && response.getStatus() < 500) { new ClientError4XX(response.getStatus(), response.readEntity(Error.class)) }
    else if (response.getStatus() >= 500 && response.getStatus() < 600) { new ServerError5XX(response.getStatus(), response.readEntity(Error.class)) }
    else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()) });
  };
}