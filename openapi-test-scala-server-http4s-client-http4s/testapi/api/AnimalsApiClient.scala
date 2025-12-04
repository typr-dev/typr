package testapi.api

import cats.effect.IO
import org.http4s.Uri
import org.http4s.client.Client
import testapi.model.Animal
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

/** Http4s client implementation for AnimalsApi */
class AnimalsApiClient(
  /** Http4s client for making HTTP requests */
  val client: Client[IO],
  /** Base URI for API requests */
  val baseUri: Uri
) extends AnimalsApi {
  /** List all animals (polymorphic) */
  override def listAnimals: IO[Response2004XX5XX[List[Animal]]] = {
    val request = org.http4s.Request[cats.effect.IO](org.http4s.Method.GET, baseUri / "animals")
    client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 200) response.as[scala.List[testapi.model.Animal]].map(v => testapi.api.Ok(v))
        else if (statusCode >= 400 && statusCode < 500) response.as[testapi.model.Error].map(v => testapi.api.ClientError4XX(statusCode, v))
        else if (statusCode >= 500 && statusCode < 600) response.as[testapi.model.Error].map(v => testapi.api.ServerError5XX(statusCode, v))
        else cats.effect.IO.raiseError(new java.lang.IllegalStateException(s"Unexpected status code: ${statusCode}"))
    }
  }
}