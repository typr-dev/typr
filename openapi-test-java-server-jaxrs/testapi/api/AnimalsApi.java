package testapi.api;

import java.util.List;
import testapi.model.Animal;

public interface AnimalsApi {
  /** List all animals (polymorphic) */
  Response2004XX5XX<List<Animal>> listAnimals();
}