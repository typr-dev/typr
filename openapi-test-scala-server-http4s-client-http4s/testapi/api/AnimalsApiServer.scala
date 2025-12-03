package testapi.api

import cats.effect.IO
import org.http4s.Response
import testapi.model.Animal

trait AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  override def listAnimals: IO[Response2004XX5XX[List[Animal]]]

  /** Endpoint wrapper for listAnimals - handles response status codes */
  def listAnimalsEndpoint: IO[Response] = {
    listAnimals().map((response: testapi.api.Response2004XX5XX) => response match {
      case r: testapi.api.Ok => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.ClientError4XX => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case r: testapi.api.ServerError5XX => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
    })
  }
}