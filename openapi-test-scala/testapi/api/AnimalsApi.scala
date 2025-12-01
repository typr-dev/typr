package testapi.api

import testapi.model.Animal

sealed trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: List[Animal]
}