package testapi.api

import cats.effect.IO
import testapi.model.Animal

trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: IO[Response2004XX5XX[List[Animal]]]
}