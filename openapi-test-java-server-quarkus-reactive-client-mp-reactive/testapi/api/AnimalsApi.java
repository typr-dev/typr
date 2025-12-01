package testapi.api;

import io.smallrye.mutiny.Uni;
import java.util.List;
import testapi.model.Animal;

public sealed interface AnimalsApi {
  /** List all animals (polymorphic) */
  Uni<List<Animal>> listAnimals();
}