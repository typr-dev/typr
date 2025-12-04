package testapi.api;

import io.smallrye.mutiny.Uni;
import java.util.List;
import testapi.model.Animal;

public interface AnimalsApi {
  /** List all animals (polymorphic) */
  Uni<Response2004XX5XX<List<Animal>>> listAnimals();
}