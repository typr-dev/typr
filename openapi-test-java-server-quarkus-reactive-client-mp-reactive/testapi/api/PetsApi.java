package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import testapi.model.Pet;
import testapi.model.PetCreate;

public sealed interface PetsApi {
  /** Create a pet */
  Uni<CreatePetResponse> createPet(PetCreate body);

  /** Delete a pet */
  Uni<Void> deletePet(
  
    /** The pet ID */
    String petId
  );

  /** Get a pet by ID */
  Uni<GetPetResponse> getPet(
  
    /** The pet ID */
    String petId
  );

  /** Get pet photo */
  Uni<Void> getPetPhoto(
  
    /** The pet ID */
    String petId
  );

  /** List all pets */
  Uni<List<Pet>> listPets(
    /** Maximum number of pets to return */
    Optional<Integer> limit,
    /** Filter by status */
    Optional<String> status
  );

  /** Upload a pet photo */
  Uni<JsonNode> uploadPetPhoto(
    /** The pet ID */
    String petId,
    /** Optional caption for the photo */
    String caption,
    /** The photo file to upload */
    Byte[] file
  );
}