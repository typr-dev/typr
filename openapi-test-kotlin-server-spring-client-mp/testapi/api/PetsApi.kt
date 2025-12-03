package testapi.api

import com.fasterxml.jackson.databind.JsonNode
import java.lang.Void
import java.util.Optional
import kotlin.collections.List
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate

interface PetsApi {
  /** Create a pet */
  fun createPet(body: PetCreate): Response201400<Pet, Error>

  /** Delete a pet */
  fun deletePet(
    /** The pet ID */
    petId: String
  ): Response404Default<Error>

  /** Get a pet by ID */
  fun getPet(
    /** The pet ID */
    petId: String
  ): Response200404<Pet, Error>

  /** Get pet photo */
  fun getPetPhoto(
    /** The pet ID */
    petId: String
  ): Void

  /** List all pets */
  fun listPets(
    /** Maximum number of pets to return */
    limit: Optional<Integer>,
    /** Filter by status */
    status: Optional<String>
  ): List<Pet>

  /** Upload a pet photo */
  fun uploadPetPhoto(
    /** The pet ID */
    petId: String,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array<Byte>
  ): JsonNode
}