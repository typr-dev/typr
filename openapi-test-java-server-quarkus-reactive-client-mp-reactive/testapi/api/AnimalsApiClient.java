package testapi.api;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import testapi.model.Animal;

@RegisterRestClient
@Path("/animals")
public sealed interface AnimalsApiClient extends AnimalsApi {
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  /** List all animals (polymorphic) */
  Uni<List<Animal>> listAnimals();
}