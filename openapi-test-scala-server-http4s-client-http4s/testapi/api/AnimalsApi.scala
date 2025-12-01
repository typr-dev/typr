package testapi.api

import cats.effect.IO
import testapi.model.Animal

sealed trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: IO[List[Animal]]
}