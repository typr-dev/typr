package testapi

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response
import org.http4s.Uri
import org.http4s.Method
import org.http4s.client.Client
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import testapi.api._
import testapi.model._

import java.time.OffsetDateTime
import scala.collection.mutable

class PetsApiTest extends AnyFunSuite with Matchers {

  // In-memory store for pets
  class TestPetsApiServer extends PetsApiServer {
    private val pets = mutable.Map[String, Pet]()
    private var idCounter = 0

    override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = IO {
      idCounter += 1
      val pet = Pet(
        tags = body.tags,
        id = idCounter.toString,
        status = body.status.getOrElse(PetStatus.available),
        createdAt = OffsetDateTime.now(),
        metadata = None,
        name = body.name,
        updatedAt = None
      )
      pets(pet.id) = pet
      Created(pet)
    }

    override def deletePet(petId: String): IO[Response404Default[Error]] = IO {
      if (pets.contains(petId)) {
        pets.remove(petId)
        Default(204, Error("0", None, "Deleted"))
      } else {
        NotFound(Error("404", None, "Pet not found"))
      }
    }

    override def getPet(petId: String): IO[Response200404[Pet, Error]] = IO {
      pets.get(petId) match {
        case Some(pet) => Ok(pet)
        case None      => NotFound(Error("404", None, "Pet not found"))
      }
    }

    override def getPetPhoto(petId: String): IO[java.lang.Void] = {
      // Binary response - not implemented in routes
      IO.raiseError(new NotImplementedError("Binary response not supported"))
    }

    override def listPets(limit: Option[Int], status: Option[String]): IO[List[Pet]] = IO {
      val filtered = status match {
        case Some(s) => pets.values.filter(_.status.value == s)
        case None    => pets.values
      }
      val limited = limit match {
        case Some(l) => filtered.take(l)
        case None    => filtered
      }
      limited.toList
    }

    override def uploadPetPhoto(petId: String, caption: String, file: Array[Byte]): IO[Json] = {
      // Multipart - not implemented in routes
      IO.raiseError(new NotImplementedError("Multipart uploads not supported"))
    }
  }

  // Client implementation that talks to routes directly
  // The PetsApiClient trait expects `Raw` methods to return IO[Response[IO]],
  // but Http4s resources need to be consumed inside the `use` block.
  // We work around this by directly implementing createPet/getPet/deletePet
  // using `expect` which handles the response lifecycle properly.
  class TestPetsApiClient(routes: HttpRoutes[IO]) extends PetsApiClient {
    private val client = Client.fromHttpApp(routes.orNotFound)

    // Override the high-level methods directly to avoid the Raw lifecycle issue
    override def createPet(body: PetCreate): IO[Response201400[Pet, Error]] = {
      val request = Request[IO](Method.POST, Uri.unsafeFromString("/pets"))
        .withEntity(body)
      client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 201) response.as[Pet].map(v => Created(v))
        else if (statusCode == 400) response.as[Error].map(v => BadRequest(v))
        else IO.raiseError(new IllegalStateException(s"Unexpected status code: $statusCode"))
      }
    }

    override def createPetRaw(body: PetCreate): IO[Response[IO]] = {
      IO.raiseError(new NotImplementedError("Use createPet instead"))
    }

    override def deletePet(petId: String): IO[Response404Default[Error]] = {
      val request = Request[IO](Method.DELETE, Uri.unsafeFromString(s"/pets/$petId"))
      client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 404) response.as[Error].map(v => NotFound(v))
        else response.as[Error].map(v => Default(statusCode, v))
      }
    }

    override def deletePetRaw(petId: String): IO[Response[IO]] = {
      IO.raiseError(new NotImplementedError("Use deletePet instead"))
    }

    override def getPet(petId: String): IO[Response200404[Pet, Error]] = {
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/pets/$petId"))
      client.run(request).use { response =>
        val statusCode = response.status.code
        if (statusCode == 200) response.as[Pet].map(v => Ok(v))
        else if (statusCode == 404) response.as[Error].map(v => NotFound(v))
        else IO.raiseError(new IllegalStateException(s"Unexpected status code: $statusCode"))
      }
    }

    override def getPetRaw(petId: String): IO[Response[IO]] = {
      IO.raiseError(new NotImplementedError("Use getPet instead"))
    }

    override def getPetPhoto(petId: String): IO[java.lang.Void] = {
      IO.raiseError(new NotImplementedError("Binary response not supported"))
    }

    override def listPets(limit: Option[Int], status: Option[String]): IO[List[Pet]] = {
      val params = List(
        limit.map(l => s"limit=$l"),
        status.map(s => s"status=$s")
      ).flatten.mkString("&")
      val uri = if (params.isEmpty) "/pets" else s"/pets?$params"
      val request = Request[IO](Method.GET, Uri.unsafeFromString(uri))
      client.run(request).use { response =>
        response.as[List[Pet]]
      }
    }

    override def uploadPetPhoto(petId: String, caption: String, file: Array[Byte]): IO[Json] = {
      IO.raiseError(new NotImplementedError("Multipart uploads not supported"))
    }
  }

  test("create and get pet roundtrip") {
    val server = new TestPetsApiServer
    val client = new TestPetsApiClient(server.routes)

    val result = (for {
      // Create a pet
      createResponse <- client.createPet(
        PetCreate(
          age = None,
          email = None,
          name = "Fluffy",
          status = Some(PetStatus.available),
          tags = Some(List("cat")),
          website = None
        )
      )

      // Verify creation
      _ = createResponse shouldBe a[Created[_]]
      createdPet = createResponse.asInstanceOf[Created[Pet]].value
      _ = createdPet.name shouldBe "Fluffy"
      _ = createdPet.tags shouldBe Some(List("cat"))

      // Get the pet back
      getResponse <- client.getPet(createdPet.id)
      _ = getResponse shouldBe a[Ok[_]]
      retrievedPet = getResponse.asInstanceOf[Ok[Pet]].value
      _ = retrievedPet shouldBe createdPet

    } yield ()).unsafeRunSync()
  }

  test("get non-existent pet returns 404") {
    val server = new TestPetsApiServer
    val client = new TestPetsApiClient(server.routes)

    val result = client.getPet("non-existent").unsafeRunSync()
    result shouldBe a[NotFound[_]]
    result.asInstanceOf[NotFound[Error]].value.code shouldBe "404"
  }

  test("list pets with pagination") {
    val server = new TestPetsApiServer
    val client = new TestPetsApiClient(server.routes)

    val result = (for {
      // Create multiple pets
      _ <- client.createPet(PetCreate(None, None, "Pet1", None, None, None))
      _ <- client.createPet(PetCreate(None, None, "Pet2", None, None, None))
      _ <- client.createPet(PetCreate(None, None, "Pet3", None, None, None))

      // List all pets
      allPets <- client.listPets(None, None)
      _ = allPets should have size 3

      // List with limit
      limitedPets <- client.listPets(Some(2), None)
      _ = limitedPets should have size 2

    } yield ()).unsafeRunSync()
  }

  test("delete pet") {
    val server = new TestPetsApiServer
    val client = new TestPetsApiClient(server.routes)

    val result = (for {
      // Create a pet
      createResponse <- client.createPet(PetCreate(None, None, "ToDelete", None, None, None))
      createdPet = createResponse.asInstanceOf[Created[Pet]].value

      // Delete the pet
      deleteResponse <- client.deletePet(createdPet.id)
      _ = deleteResponse shouldBe a[Default]

      // Verify it's gone
      getResponse <- client.getPet(createdPet.id)
      _ = getResponse shouldBe a[NotFound[_]]

    } yield ()).unsafeRunSync()
  }
}
