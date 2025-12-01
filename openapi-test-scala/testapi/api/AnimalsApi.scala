package testapi.api



trait AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimals: ListAnimalsResponse
}