package testapi.api

import kotlin.collections.List
import testapi.model.Animal

interface AnimalsApi {
  /** List all animals (polymorphic) */
  fun listAnimals(): Response2004XX5XX<List<Animal>>
}