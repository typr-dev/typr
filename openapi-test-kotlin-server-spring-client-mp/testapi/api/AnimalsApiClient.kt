package testapi.api

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.GenericType
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.IllegalStateException
import kotlin.collections.List
import testapi.model.Animal
import testapi.model.Error

interface AnimalsApiClient : AnimalsApi {
  /** List all animals (polymorphic) - handles response status codes */
  override fun listAnimals(): Response2004XX5XX<List<Animal>> {
    try {
      val response: Response = listAnimalsRaw();
      if (response.getStatus() == 200) { return Ok(response.readEntity(object : GenericType<List<Animal>>() {})) }
      else if (response.getStatus() >= 400 && response.getStatus() < 500) { return ClientError4XX(response.getStatus(), response.readEntity(Error::class.java)) }
      else if (response.getStatus() >= 500 && response.getStatus() < 600) { return ServerError5XX(response.getStatus(), response.readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + response.getStatus()) }
    } catch (e: WebApplicationException) {
      if (e.getResponse().getStatus() == 200) { return Ok(e.getResponse().readEntity(object : GenericType<List<Animal>>() {})) }
      else if (e.getResponse().getStatus() >= 400 && e.getResponse().getStatus() < 500) { return ClientError4XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error::class.java)) }
      else if (e.getResponse().getStatus() >= 500 && e.getResponse().getStatus() < 600) { return ServerError5XX(e.getResponse().getStatus(), e.getResponse().readEntity(Error::class.java)) }
      else { throw IllegalStateException("Unexpected status code: " + e.getResponse().getStatus()) }
    } 
  }

  /** List all animals (polymorphic) */
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  fun listAnimalsRaw(): Response
}