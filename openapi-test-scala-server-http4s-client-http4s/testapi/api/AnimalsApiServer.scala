package testapi.api

import cats.effect.IO
import testapi.model.Animal

sealed trait AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: IO[List[Animal]]
}