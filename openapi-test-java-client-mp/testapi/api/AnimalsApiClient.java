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
import testapi.api.ListAnimalsResponse.Status200;
import testapi.api.ListAnimalsResponse.Status4XX;
import testapi.api.ListAnimalsResponse.Status5XX;
import testapi.model.Animal;
import testapi.model.Error;

@RegisterRestClient
@Path("/animals")
public sealed interface AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) - handles response status codes */
  @Override
  default ListAnimalsResponse listAnimals() {
    try {
      Response response = listAnimalsRaw();
      if (response.getStatus() == 200) { new Status200(response.readEntity(new GenericType<List<Animal>>() {})) }
      else if (response.getStatus() >= 400 && response.getStatus() < 500) { new Status4XX(response.getStatus(), response.readEntity(Error.class)) }
      else if (response.getStatus() >= 500 && response.getStatus() < 600) { new Status5XX(response.getStatus(), response.readEntity(Error.class)) }
      else { throw new IllegalStateException("Unexpected status code: " + response.getStatus()) }
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 200) { new Status200(e.getResponse().readEntity(new GenericType<List<Animal>>() {})) }
      else if (e.getResponse().getStatus() >= 400 && e.getResponse().getStatus() < 500) { new Status4XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error.class)) }
      else if (e.getResponse().getStatus() >= 500 && e.getResponse().getStatus() < 600) { new Status5XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error.class)) }
      else { throw new IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()) }
    } ;
    null;
  };

  /** List all animals (polymorphic) */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  Response listAnimalsRaw();
}