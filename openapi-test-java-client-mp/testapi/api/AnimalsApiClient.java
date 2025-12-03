package testapi.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.IllegalStateException;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import testapi.model.Animal;
import testapi.model.Error;

@RegisterRestClient
@Path("/animals")
public interface AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  Response listAnimalsRaw();

  /** List all animals (polymorphic) - handles response status codes */
  @Override
  default Response2004XX5XX<List<Animal>> listAnimals() {
    try {
      Response response = listAnimalsRaw();
      if (response.getStatus() == 200) { return new Ok(response.readEntity(new GenericType<List<Animal>>() {})); }
      else if (response.getStatus() >= 400 && response.getStatus() < 500) { return new ClientError4XX(response.getStatus(), response.readEntity(Error.class)); }
      else if (response.getStatus() >= 500 && response.getStatus() < 600) { return new ServerError5XX(response.getStatus(), response.readEntity(Error.class)); }
      else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()); }
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 200) { return new Ok(e.getResponse().readEntity(new GenericType<List<Animal>>() {})); }
      else if (e.getResponse().getStatus() >= 400 && e.getResponse().getStatus() < 500) { return new ClientError4XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error.class)); }
      else if (e.getResponse().getStatus() >= 500 && e.getResponse().getStatus() < 600) { return new ServerError5XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error.class)); }
      else { throw new IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()); }
    } 
  };
}