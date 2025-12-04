package testapi.api

import io.smallrye.mutiny.Uni
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.lang.IllegalStateException
import kotlin.collections.List
import testapi.model.Animal

interface AnimalsApiServer : AnimalsApi {
  /** List all animals (polymorphic) */
  override fun listAnimals(): Uni<Response2004XX5XX<List<Animal>>>

  /** Endpoint wrapper for listAnimals - handles response status codes */
  @GET
  @Path("/")
  @Produces(value = [MediaType.APPLICATION_JSON])
  fun listAnimalsEndpoint(): Uni<Response> = listAnimals().map({ response: Response2004XX5XX<*> -> when (val __r = response) {
    is Ok<*> -> { val r = __r as Ok<*>; Response.ok(r.value).build() }
    is ClientError4XX -> { val r = __r as ClientError4XX; Response.status(r.statusCode).entity(r.value).build() }
    is ServerError5XX -> { val r = __r as ServerError5XX; Response.status(r.statusCode).entity(r.value).build() }
    else -> throw IllegalStateException("Unexpected response type")
  } })
}