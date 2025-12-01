package testapi.api;



public sealed interface AnimalsApi {
  /** List all animals (polymorphic) */
  ListAnimalsResponse listAnimals();
}