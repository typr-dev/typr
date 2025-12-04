package testapi.api

import testapi.model.Animal

trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: Response2004XX5XX[List[Animal]]
}