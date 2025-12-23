package testapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.circe.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import testapi.api._
import testapi.model._

import java.lang.Void
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.util.Optional
import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters._

/** Integration tests for the OpenAPI generated Scala Spring server and JDK HTTP client code. These tests start a real Spring Boot server and use the generated client.
  */
class OpenApiIntegrationTest {

  private val TestTime = OffsetDateTime.parse("2024-01-01T12:00:00Z")

  private var context: ConfigurableApplicationContext = uninitialized
  private var client: PetsApiClient = uninitialized
  private var serverImpl: TestPetsApiServer = uninitialized
  private var baseUri: URI = uninitialized
  private var httpClient: HttpClient = uninitialized

  /** Test server implementation */
  class TestPetsApiServer extends PetsApiServer {
    private val initialPet = Pet(
      tags = Optional.of(List("friendly", "cute").asJava),
      id = PetId("pet-123"),
      status = PetStatus.available,
      createdAt = TestTime,
      metadata = Optional.of(Map("color" -> "brown").asJava),
      name = "Fluffy",
      updatedAt = Optional.empty()
    )

    private val pets = mutable.Map[PetId, Pet](initialPet.id -> initialPet)

    def reset(): Unit = {
      pets.clear()
      pets(initialPet.id) = initialPet
    }

    override def createPet(body: PetCreate): Response201400[Pet, Error] = {
      val newPet = Pet(
        tags = body.tags,
        id = PetId(s"pet-${System.nanoTime()}"),
        status = if (body.status.isPresent) body.status.get() else PetStatus.available,
        createdAt = TestTime,
        metadata = Optional.empty(),
        name = body.name,
        updatedAt = Optional.empty()
      )
      pets(newPet.id) = newPet
      Created(newPet)
    }

    override def deletePet(petId: PetId): Unit = {
      pets.remove(petId)
    }

    override def getPet(petId: PetId): Response200404[Pet, Error] = {
      pets.get(petId) match {
        case Some(foundPet) => Ok(foundPet)
        case None           => NotFound(Error("NOT_FOUND", Optional.empty(), "Pet not found"))
      }
    }

    override def getPetPhoto(petId: PetId): Unit = {
      throw new UnsupportedOperationException("Not implemented")
    }

    override def listPets(limit: Optional[Integer], status: Optional[String]): java.util.List[Pet] = {
      val filtered = if (status.isPresent) {
        pets.values.filter(_.status.value == status.get()).toList
      } else {
        pets.values.toList
      }
      val result = if (limit.isPresent) {
        filtered.take(limit.get().intValue())
      } else {
        filtered
      }
      result.asJava
    }

    override def uploadPetPhoto(petId: PetId, caption: String, file: Array[Byte]): Json = {
      throw new UnsupportedOperationException("Not implemented")
    }
  }

  @Before
  def setUp(): Unit = {
    serverImpl = new TestPetsApiServer
    OpenApiIntegrationTest.setTestServer(serverImpl)

    // Start Spring Boot with random port
    context = SpringApplication.run(classOf[OpenApiIntegrationTest.TestApplication], "--server.port=0")

    val port = context.getEnvironment.getProperty("local.server.port", classOf[Integer])
    baseUri = URI.create(s"http://127.0.0.1:$port")

    val objectMapper = new ObjectMapper()
    objectMapper.registerModule(new Jdk8Module())
    objectMapper.registerModule(new JavaTimeModule())
    objectMapper.registerModule(DefaultScalaModule)

    httpClient = HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

    client = new PetsApiClient(httpClient, baseUri, objectMapper)
  }

  @After
  def tearDown(): Unit = {
    if (context != null) {
      context.close()
    }
  }

  @Test
  def debugRawHttpRequest(): Unit = {
    serverImpl.reset()
    // Make a raw HTTP request to see what Spring returns
    val request = HttpRequest
      .newBuilder(URI.create(baseUri.toString + "/pets/pet-123"))
      .GET()
      .header("Accept", "application/json")
      .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    println(s"Status: ${response.statusCode()}")
    println(s"Body: ${response.body()}")
    println(s"Headers: ${response.headers()}")

    // Should be 200, not 404 or error page
    assertEquals(s"Expected 200 but got ${response.statusCode()}, body: ${response.body()}", 200, response.statusCode())
  }

  @Test
  def listPetsRaw(): Unit = {
    serverImpl.reset()
    val request = HttpRequest
      .newBuilder(URI.create(baseUri.toString + "/pets"))
      .GET()
      .header("Accept", "application/json")
      .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    println(s"listPets Status: ${response.statusCode()}")
    println(s"listPets Body: ${response.body()}")

    assertEquals(s"Expected 200 but got ${response.statusCode()}, body: ${response.body()}", 200, response.statusCode())
  }
}

object OpenApiIntegrationTest {
  private var testServer: PetsApiServer = uninitialized

  def setTestServer(server: PetsApiServer): Unit = {
    testServer = server
  }

  def getTestServer: PetsApiServer = testServer

  /** Spring Boot test application */
  @SpringBootApplication
  class TestApplication {
    @Bean
    def petsApiServer(): PetsApiServer = OpenApiIntegrationTest.getTestServer

    @Bean
    def objectMapper(): ObjectMapper = {
      val mapper = new ObjectMapper()
      mapper.registerModule(new Jdk8Module())
      mapper.registerModule(new JavaTimeModule())
      mapper.registerModule(DefaultScalaModule)
      mapper
    }
  }
}
