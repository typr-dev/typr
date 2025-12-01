package testapi.api;

import io.smallrye.mutiny.Uni;

public sealed interface AnimalsApi {
  /** List all animals (polymorphic) */
  Uni<ListAnimalsResponse> listAnimals();
}