package testapi.api

import cats.effect.IO
import org.http4s.Response

trait AnimalsApiServer extends AnimalsApi {
  /** List all animals (polymorphic) */
  override def listAnimals: IO[ListAnimalsResponse]

  /** Endpoint wrapper for listAnimals - handles response status codes */
  def listAnimalsEndpoint: IO[Response] = {
    listAnimals().map((response: testapi.api.ListAnimalsResponse) => response match {
      case r: testapi.api.ListAnimalsResponse.Status200 => org.http4s.Response.apply(org.http4s.Status.Ok).withEntity(r.value)
      case r: testapi.api.ListAnimalsResponse.Status4XX => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
      case r: testapi.api.ListAnimalsResponse.Status5XX => org.http4s.Response.apply(org.http4s.Status.fromInt(r.statusCode).getOrElse(org.http4s.Status.InternalServerError)).withEntity(r.value)
    })
  }
}