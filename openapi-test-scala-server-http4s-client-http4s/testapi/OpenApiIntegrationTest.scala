package testapi

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.Method._
import org.http4s.Request
import org.http4s.Response
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import testapi.api._
import testapi.model._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder

import java.time.OffsetDateTime

/** Integration tests for the OpenAPI generated Scala HTTP4s server code.
  *
  * These tests verify that:
  *   1. The generated server trait can be implemented 2. The generated response types work correctly 3. Routes are correctly wired to handler methods
  */
class OpenApiIntegrationTest extends AnyFunSuite with Matchers {

  private val TestTime = OffsetDateTime.parse("2024-01-01T12:00:00Z")

  class TestPetsApiServer extends PetsApiServer {
    private val pets = scala.collection.mutable.Map(
      "pet-123" -> Pet(
        tags = Some(List("friendly", "cute")),
        id = "pet-123",
        status = PetStatus.available,
        createdAt = TestTime,
        metadata = Some(Map("color" -> "brown")),
        name = "Fluffy",
        updatedAt = None
      )
    )

    override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = {
      val newPet = Pet(
        tags = body.tags,
        id = s"pet-${System.nanoTime()}",
        status = body.status.getOrElse(PetStatus.available),
        createdAt = TestTime,
        metadata = None,
        name = body.name,
        updatedAt = None
      )
      pets(newPet.id) = newPet
      IO.pure(Created(newPet))
    }

    override def deletePet(petId: String): IO[Response404Default[Error]] = {
      if (pets.contains(petId)) {
        pets.remove(petId)
        IO.pure(Default(204, Error("OK", None, "Deleted")))
      } else {
        IO.pure(NotFound(Error("NOT_FOUND", None, "Pet not found")))
      }
    }

    override def getPet(petId: String): IO[Response200404[Pet, Error]] = {
      pets.get(petId) match {
        case Some(foundPet) => IO.pure(Ok(foundPet))
        case None           => IO.pure(NotFound(Error("NOT_FOUND", None, "Pet not found")))
      }
    }

    override def getPetPhoto(petId: String): IO[Void] = {
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

    override def uploadPetPhoto(petId: String, caption: String, file: Array[Byte]): IO[Json] = {
      throw new UnsupportedOperationException("Not implemented")
    }
  }

  test("getPet returns Ok for existing pet") {
    val server = new TestPetsApiServer
    val result = server.getPet("pet-123").unsafeRunSync()

    result shouldBe a[Ok[_]]
    result.status shouldBe "200"
    result.asInstanceOf[Ok[Pet]].value.name shouldBe "Fluffy"
  }

  test("getPet returns NotFound for non-existent pet") {
    val server = new TestPetsApiServer
    val result = server.getPet("nonexistent").unsafeRunSync()

    result shouldBe a[NotFound[_]]
    result.status shouldBe "404"
    result.asInstanceOf[NotFound[Error]].value.code shouldBe "NOT_FOUND"
  }

  test("createPet returns Created") {
    val server = new TestPetsApiServer
    val newPet = PetCreate(
      age = Some(2L),
      email = None,
      name = "Buddy",
      status = Some(PetStatus.pending),
      tags = Some(List("playful")),
      website = None
    )

    val result = server.createPet(newPet).unsafeRunSync()

    result shouldBe a[Created[_]]
    result.status shouldBe "201"
    val created = result.asInstanceOf[Created[Pet]]
    created.value.name shouldBe "Buddy"
    created.value.status shouldBe PetStatus.pending
  }

  test("deletePet returns Default for existing pet") {
    val server = new TestPetsApiServer
    val result = server.deletePet("pet-123").unsafeRunSync()

    result shouldBe a[Default]
    result.status shouldBe "default"
    result.asInstanceOf[Default].statusCode shouldBe 204
  }

  test("deletePet returns NotFound for non-existent pet") {
    val server = new TestPetsApiServer
    val result = server.deletePet("nonexistent").unsafeRunSync()

    result shouldBe a[NotFound[_]]
    result.status shouldBe "404"
  }

  test("listPets returns all pets") {
    val server = new TestPetsApiServer
    val result = server.listPets(None, None).unsafeRunSync()

    result should not be empty
    result.exists(_.name == "Fluffy") shouldBe true
  }

  test("HTTP routes work correctly") {
    val server = new TestPetsApiServer
    val httpApp = server.routes.orNotFound

    // Test GET /pets/{petId}
    val getRequest = Request[IO](GET, uri"/pets/pet-123")
    val getResponse = httpApp.run(getRequest).unsafeRunSync()

    getResponse.status.code shouldBe 200
    val pet = getResponse.as[Pet].unsafeRunSync()
    pet.name shouldBe "Fluffy"

    // Test GET /pets/{petId} not found
    val get404Request = Request[IO](GET, uri"/pets/nonexistent")
    val get404Response = httpApp.run(get404Request).unsafeRunSync()

    get404Response.status.code shouldBe 404
  }
}
