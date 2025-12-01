package testapi.api

import cats.effect.IO
import testapi.model.Animal

sealed trait AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: IO[List[Animal]]
}