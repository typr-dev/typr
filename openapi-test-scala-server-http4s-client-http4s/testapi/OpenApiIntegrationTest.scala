package testapi

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import io.circe.Json
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import testapi.api._
import testapi.model._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder

import java.lang.Void
import java.time.OffsetDateTime

/** Integration tests for the OpenAPI generated Scala HTTP4s server and client code.
  *
  * These tests start a real Http4s server, create a real client, and verify that requests/responses round-trip correctly through the generated code.
  */
class OpenApiIntegrationTest extends AnyFunSuite with Matchers {

  private val TestTime = OffsetDateTime.parse("2024-01-01T12:00:00Z")

  /** Test server implementation with resettable state */
  class TestPetsApiServer extends PetsApiServer {
    private val initialPet = Pet(
      tags = Some(List("friendly", "cute")),
      id = PetId("pet-123"),
      status = PetStatus.available,
      createdAt = TestTime,
      metadata = Some(Map("color" -> "brown")),
      name = "Fluffy",
      updatedAt = None
    )

    private val pets = scala.collection.mutable.Map(initialPet.id -> initialPet)

    def reset(): Unit = {
      pets.clear()
      pets(initialPet.id) = initialPet
    }

    override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = {
      val newPet = Pet(
        tags = body.tags,
        id = PetId(s"pet-${System.nanoTime()}"),
        status = body.status.getOrElse(PetStatus.available),
        createdAt = TestTime,
        metadata = None,
        name = body.name,
        updatedAt = None
      )
      pets(newPet.id) = newPet
      IO.pure(Created(newPet))
    }

    override def deletePet(petId: PetId): IO[Void] = {
      pets.remove(petId)
      IO.pure(null)
    }

    override def getPet(petId: PetId): IO[Response200404[Pet, Error]] = {
      pets.get(petId) match {
        case Some(foundPet) => IO.pure(Ok(foundPet))
        case None           => IO.pure(NotFound(Error("NOT_FOUND", None, "Pet not found")))
      }
    }

    override def getPetPhoto(petId: PetId): IO[Void] = {
      throw new UnsupportedOperationException("Not implemented")
    }

    override def listPets(limit: Option[Int], status: Option[String]): IO[List[Pet]] = {
      val filtered = status match {
        case Some(s) => pets.values.filter(_.status.value == s).toList
        case None    => pets.values.toList
      }
      val limited = limit match {
        case Some(l) => filtered.take(l)
        case None    => filtered
      }
      IO.pure(limited)
    }

    override def uploadPetPhoto(petId: PetId, caption: String, file: Array[Byte]): IO[Json] = {
      throw new UnsupportedOperationException("Not implemented")
    }
  }

  // Shared server and client - started once for all tests
  private val serverImpl = new TestPetsApiServer
  private lazy val (httpClient, cleanup) = {
    val resources = for {
      client <- EmberClientBuilder.default[IO].build
      srv <- EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(serverImpl.routes.orNotFound)
        .build
    } yield (client, srv)

    val ((client, srv), release) = resources.allocated.unsafeRunSync()
    val baseUri = Uri.unsafeFromString(s"http://127.0.0.1:${srv.address.getPort}")
    (new PetsApiClient(client, baseUri), release)
  }

  override def withFixture(test: NoArgTest) = {
    serverImpl.reset()
    super.withFixture(test)
  }

  test("getPet returns existing pet via HTTP round-trip") {
    httpClient
      .getPet(PetId("pet-123"))
      .map { response =>
        val ok = response.asInstanceOf[Ok[Pet]]
        ok.value.name shouldBe "Fluffy"
        ok.value.id shouldBe PetId("pet-123")
        ok.value.status shouldBe PetStatus.available
      }
      .unsafeRunSync()
  }

  test("getPet returns NotFound for non-existent pet via HTTP round-trip") {
    httpClient
      .getPet(PetId("nonexistent"))
      .map { response =>
        val notFound = response.asInstanceOf[NotFound[Error]]
        notFound.value.code shouldBe "NOT_FOUND"
      }
      .unsafeRunSync()
  }

  test("createPet creates and returns pet via HTTP round-trip") {
    val newPet = PetCreate(
      age = Some(2L),
      email = None,
      name = "Buddy",
      status = Some(PetStatus.pending),
      tags = Some(List("playful")),
      website = None
    )

    httpClient
      .createPet(newPet)
      .map { response =>
        val created = response.asInstanceOf[Created[Pet]]
        created.value.name shouldBe "Buddy"
        created.value.status shouldBe PetStatus.pending
        created.value.tags shouldBe Some(List("playful"))
      }
      .unsafeRunSync()
  }

  test("deletePet deletes existing pet via HTTP round-trip") {
    httpClient
      .deletePet(PetId("pet-123"))
      .map { _ =>
        succeed
      }
      .unsafeRunSync()
  }

  test("listPets returns all pets via HTTP round-trip") {
    httpClient
      .listPets(None, None)
      .map { pets =>
        pets should not be empty
        pets.exists(_.name == "Fluffy") shouldBe true
      }
      .unsafeRunSync()
  }

  test("listPets with limit returns limited pets via HTTP round-trip") {
    httpClient
      .listPets(Some(1), None)
      .map { pets =>
        pets.size shouldBe 1
      }
      .unsafeRunSync()
  }
}
