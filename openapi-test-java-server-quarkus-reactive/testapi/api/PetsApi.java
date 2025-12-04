package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;

public interface PetsApi {
  /** Create a pet */
  Uni<Response201400<Pet, Error>> createPet(PetCreate body);

  /** Delete a pet */
  Uni<Void> deletePet(
  
    /** The pet ID */
    PetId petId
  );

  /** Get a pet by ID */
  Uni<Response200404<Pet, Error>> getPet(
  
    /** The pet ID */
    PetId petId
  );

  /** Get pet photo */
  Uni<Void> getPetPhoto(
  
    /** The pet ID */
    PetId petId
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
    PetId petId,
    /** Optional caption for the photo */
    String caption,
    /** The photo file to upload */
    Byte[] file
  );
}