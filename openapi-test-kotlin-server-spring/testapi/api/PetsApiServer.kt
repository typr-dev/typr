package testapi.api

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.lang.IllegalStateException
import java.lang.Void
import java.util.Optional
import kotlin.collections.List
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import testapi.model.PetId

interface PetsApiServer : PetsApi {
  /** Create a pet */
  override fun createPet(body: PetCreate): Response201400<Pet, Error>

  /** Endpoint wrapper for createPet - handles response status codes */
  @PostMapping(value = ["/"], consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
  @SecurityRequirement(name = "oauth2", scopes = ["write:pets"])
  @SecurityRequirement(name = "apiKeyHeader")
  fun createPetEndpoint(body: PetCreate): ResponseEntity<*> = when (val __r = createPet(body)) {
    is Created<*> -> { val r = __r as Created<*>; ResponseEntity.ok(r.value) }
    is BadRequest<*> -> { val r = __r as BadRequest<*>; ResponseEntity.status(400).body(r.value) }
    else -> throw IllegalStateException("Unexpected response type")
  }

  /** Delete a pet */
  @DeleteMapping(value = ["/{petId}"])
  override fun deletePet(
    /** The pet ID */
    petId: PetId
  ): Void

  /** Get a pet by ID */
  override fun getPet(
    /** The pet ID */
    petId: PetId
  ): Response200404<Pet, Error>

  /** Endpoint wrapper for getPet - handles response status codes */
  @GetMapping(value = ["/{petId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getPetEndpoint(
    /** The pet ID */
    petId: PetId
  ): ResponseEntity<*> = when (val __r = getPet(petId)) {
    is Ok<*> -> { val r = __r as Ok<*>; ResponseEntity.ok(r.value) }
    is NotFound<*> -> { val r = __r as NotFound<*>; ResponseEntity.status(404).body(r.value) }
    else -> throw IllegalStateException("Unexpected response type")
  }

  /** Get pet photo */
  @GetMapping(value = ["/{petId}/photo"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
  override fun getPetPhoto(
    /** The pet ID */
    petId: PetId
  ): Void

  /** List all pets */
  @GetMapping(value = ["/"], produces = [MediaType.APPLICATION_JSON_VALUE])
  override fun listPets(
    /** Maximum number of pets to return */
    limit: Optional<Integer>,
    /** Filter by status */
    status: Optional<String>
  ): List<Pet>

  /** Upload a pet photo */
  @PostMapping(value = ["/{petId}/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
  override fun uploadPetPhoto(
    /** The pet ID */
    petId: PetId,
    /** Optional caption for the photo */
    caption: String,
    /** The photo file to upload */
    file: Array<Byte>
  ): JsonNode
}