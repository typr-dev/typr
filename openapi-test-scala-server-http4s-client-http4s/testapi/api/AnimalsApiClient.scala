package testapi.api

import cats.effect.IO
import org.http4s.Response

trait AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimalsRaw: IO[Response]

  /** List all animals (polymorphic) - handles response status codes */
  override def listAnimals: IO[ListAnimalsResponse] = {
    listAnimalsRaw().flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 200) response.as[scala.List[testapi.model.Animal]].map(v => testapi.api.ListAnimalsResponse.Status200(v))
    else if (statusCode >= 400 && statusCode < 500) response.as[testapi.model.Error].map(v => testapi.api.ListAnimalsResponse.Status4XX(statusCode, v))
    else if (statusCode >= 500 && statusCode < 600) response.as[testapi.model.Error].map(v => testapi.api.ListAnimalsResponse.Status5XX(statusCode, v))
    else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: statusCode"))
    } }
  }
}