package testapi.api

import java.lang.IllegalStateException
import kotlin.collections.List
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import testapi.model.Animal

interface AnimalsApiServer : AnimalsApi {
  /** List all animals (polymorphic) */
  override fun listAnimals(): Response2004XX5XX<List<Animal>>

  /** Endpoint wrapper for listAnimals - handles response status codes */
  @GetMapping(value = ["/"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun listAnimalsEndpoint(): ResponseEntity<*> = when (val __r = listAnimals()) {
    is Ok<*> -> { val r = __r as Ok<*>; ResponseEntity.ok(r.value) }
    is ClientError4XX -> { val r = __r as ClientError4XX; ResponseEntity.status(r.statusCode).body(r.value) }
    is ServerError5XX -> { val r = __r as ServerError5XX; ResponseEntity.status(r.statusCode).body(r.value) }
    else -> throw IllegalStateException("Unexpected response type")
  }
}