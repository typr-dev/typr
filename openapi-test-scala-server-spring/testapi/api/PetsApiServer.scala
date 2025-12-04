package testapi.api

import io.circe.Json
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import java.lang.Void
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import testapi.model.Error
import testapi.model.Pet
import testapi.model.PetCreate
import testapi.model.PetId

@RestController
@RequestMapping("/pets")
@SecurityScheme(name = "bearerAuth", `type` = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", `type` = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", `type` = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", `type` = SecuritySchemeType.OAUTH2)
trait PetsApiServer extends PetsApi {
  /** Create a pet */
  override def createPet(body: PetCreate): Response201400[Pet, Error]

  /** Endpoint wrapper for createPet - handles response status codes */
  @PostMapping(value = Array("/"), consumes = Array(MediaType.APPLICATION_JSON_VALUE), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  @SecurityRequirement(name = "oauth2", scopes = Array("write:pets"))
  @SecurityRequirement(name = "apiKeyHeader")
  def createPetEndpoint(@RequestBody body: PetCreate): ResponseEntity[?] = {
    createPet(body) match {
      case r: testapi.api.Created[?] => org.springframework.http.ResponseEntity.ok(r.value.asInstanceOf[testapi.model.Pet])
      case r: testapi.api.BadRequest[?] => org.springframework.http.ResponseEntity.status(400).body(r.value.asInstanceOf[testapi.model.Error])
    }
  }

  /** Delete a pet */
  @DeleteMapping(value = Array("/{petId}"))
  override def deletePet(
    /** The pet ID */
    @PathVariable("petId") petId: PetId
  ): Void

  /** Get a pet by ID */
  override def getPet(
    /** The pet ID */
    petId: PetId
  ): Response200404[Pet, Error]

  /** Endpoint wrapper for getPet - handles response status codes */
  @GetMapping(value = Array("/{petId}"), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  def getPetEndpoint(
    /** The pet ID */
    @PathVariable("petId") petId: PetId
  ): ResponseEntity[?] = {
    getPet(petId) match {
      case r: testapi.api.Ok[?] => org.springframework.http.ResponseEntity.ok(r.value.asInstanceOf[testapi.model.Pet])
      case r: testapi.api.NotFound[?] => org.springframework.http.ResponseEntity.status(404).body(r.value.asInstanceOf[testapi.model.Error])
    }
  }

  /** Get pet photo */
  @GetMapping(value = Array("/{petId}/photo"), produces = Array(MediaType.APPLICATION_OCTET_STREAM_VALUE))
  override def getPetPhoto(
    /** The pet ID */
    @PathVariable("petId") petId: PetId
  ): Void

  /** List all pets */
  @GetMapping(value = Array("/"), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  override def listPets(
    /** Maximum number of pets to return */
    @RequestParam(name = "limit", required = false, defaultValue = "20") limit: Option[Int],
    /** Filter by status */
    @RequestParam(name = "status", required = false, defaultValue = "available") status: Option[String]
  ): List[Pet]

  /** Upload a pet photo */
  @PostMapping(value = Array("/{petId}/photo"), consumes = Array(MediaType.MULTIPART_FORM_DATA_VALUE), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  override def uploadPetPhoto(
    /** The pet ID */
    @PathVariable("petId") petId: PetId,
    /** Optional caption for the photo */
    @RequestPart(name = "caption", required = false) caption: String,
    /** The photo file to upload */
    @RequestPart(name = "file") file: Array[Byte]
  ): Json
}