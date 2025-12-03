package testapi.api

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Response
import testapi.model.Animal
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.dsl.io._

trait AnimalsApiServer extends AnimalsApi {

  /** List all animals (polymorphic) */
  override def listAnimals: IO[Response2004XX5XX[List[Animal]]]

  /** Endpoint wrapper for listAnimals - handles response status codes */
  def listAnimalsEndpoint: IO[Response[IO]] = {
    listAnimals.flatMap((response: testapi.api.Response2004XX5XX[?]) =>
      (response: @unchecked) match {
        case r: testapi.api.Ok[scala.List[testapi.model.Animal]] => r.toResponse
        case r: testapi.api.ClientError4XX                       => r.toResponse
        case r: testapi.api.ServerError5XX                       => r.toResponse
      }
    )
  }

  /** HTTP routes for this API - wire this to your Http4s server */
  def routes: HttpRoutes[IO] = {
    org.http4s.HttpRoutes.of[cats.effect.IO] { case GET -> Root / "animals" =>
      listAnimalsEndpoint
    }
  }
}
