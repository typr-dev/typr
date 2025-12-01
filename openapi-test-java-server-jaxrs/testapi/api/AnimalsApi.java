package testapi.api;

import java.util.List;
import testapi.model.Animal;

public sealed interface AnimalsApi {
  /** List all animals (polymorphic) */
  List<Animal> listAnimals();
}