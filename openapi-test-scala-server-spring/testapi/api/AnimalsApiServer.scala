package testapi.api

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import testapi.model.Animal

@RestController
@RequestMapping("/animals")
@SecurityScheme(name = "bearerAuth", `type` = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", `type` = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", `type` = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", `type` = SecuritySchemeType.OAUTH2)
trait AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  override def listAnimals: Response2004XX5XX[List[Animal]]

  /** Endpoint wrapper for listAnimals - handles response status codes */
  @GetMapping(value = Array("/"), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  def listAnimalsEndpoint: ResponseEntity[?] = {
    listAnimals match {
      case r: testapi.api.Ok[?] => org.springframework.http.ResponseEntity.ok(r.value.asInstanceOf[scala.List[testapi.model.Animal]])
      case r: testapi.api.ClientError4XX => org.springframework.http.ResponseEntity.status(r.statusCode).body(r.value)
      case r: testapi.api.ServerError5XX => org.springframework.http.ResponseEntity.status(r.statusCode).body(r.value)
    }
  }
}