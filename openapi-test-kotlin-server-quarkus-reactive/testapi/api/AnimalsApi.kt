package testapi.api

import io.smallrye.mutiny.Uni
import kotlin.collections.List
import testapi.model.Animal

interface AnimalsApi {
  /** List all animals (polymorphic) */
  fun listAnimals(): Uni<Response2004XX5XX<List<Animal>>>
}