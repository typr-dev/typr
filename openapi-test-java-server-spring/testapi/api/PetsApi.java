package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;

public interface PetsApi {
  /** Create a pet */
  Response201400<Pet, Error> createPet(PetCreate body);

  /** Delete a pet */
  Void deletePet(
  
    /** The pet ID */
    PetId petId
  );

  /** Get a pet by ID */
  Response200404<Pet, Error> getPet(
  
    /** The pet ID */
    PetId petId
  );

  /** Get pet photo */
  Void getPetPhoto(
  
    /** The pet ID */
    PetId petId
  );

  /** List all pets */
  List<Pet> listPets(
    /** Maximum number of pets to return */
    Optional<Integer> limit,
    /** Filter by status */
    Optional<String> status
  );

  /** Upload a pet photo */
  JsonNode uploadPetPhoto(
    /** The pet ID */
    PetId petId,
    /** Optional caption for the photo */
    String caption,
    /** The photo file to upload */
    Byte[] file
  );
}