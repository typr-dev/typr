package testapi.api

import cats.effect.IO

trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: IO[ListAnimalsResponse]
}