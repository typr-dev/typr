package testapi.api

import cats.effect.IO
import org.http4s.Response
import testapi.model.Animal
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

trait AnimalsApiClient extends AnimalsApi {
  /** List all animals (polymorphic) */
  def listAnimalsRaw: IO[Response[IO]]

  /** List all animals (polymorphic) - handles response status codes */
  override def listAnimals: IO[Response2004XX5XX[List[Animal]]] = {
    listAnimalsRaw.flatMap { response => {
      val statusCode = response.status.code
      if (statusCode == 200) response.as[scala.List[testapi.model.Animal]].map(v => testapi.api.Ok(v))
    else if (statusCode >= 400 && statusCode < 500) response.as[testapi.model.Error].map(v => testapi.api.ClientError4XX(statusCode, v))
    else if (statusCode >= 500 && statusCode < 600) response.as[testapi.model.Error].map(v => testapi.api.ServerError5XX(statusCode, v))
    else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: statusCode"))
    } }
  }
}