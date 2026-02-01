package typr.avro.parser

import typr.avro.{AvroSchemaFile, SchemaEvolution, SchemaRole}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.util.{Try, Success, Failure}

/** Client for fetching schemas from Confluent Schema Registry */
object SchemaRegistryClient {

  case class RegistryError(message: String) extends Exception(message)

  /** Schema with version metadata from registry */
  case class VersionedSchema(
      schemaJson: String,
      version: Int,
      schemaId: Int
  )

  /** Fetch all schemas from a Schema Registry.
    *
    * Fetches both -value and -key subjects, marking them with appropriate SchemaRole.
    */
  def fetchSchemas(registryUrl: String): Either[AvroParseError, List[AvroSchemaFile]] =
    fetchSchemasWithEvolution(registryUrl, SchemaEvolution.LatestOnly)

  /** Fetch schemas with schema evolution support.
    *
    * @param registryUrl
    *   The Schema Registry URL
    * @param evolution
    *   The schema evolution strategy:
    *   - LatestOnly: Fetch only the latest version of each subject
    *   - AllVersions: Fetch all versions, generating versioned types (e.g., OrderV1, OrderV2)
    *   - WithMigrations: Same as AllVersions, plus generate migration helpers
    */
  def fetchSchemasWithEvolution(
      registryUrl: String,
      evolution: SchemaEvolution
  ): Either[AvroParseError, List[AvroSchemaFile]] = {
    val client = HttpClient.newHttpClient()
    val baseUrl = registryUrl.stripSuffix("/")
    val fetchAllVersions = evolution != SchemaEvolution.LatestOnly

    for {
      subjects <- listSubjects(client, baseUrl)
      valueSubjects = subjects.filter(_.endsWith("-value"))
      keySubjects = subjects.filter(_.endsWith("-key"))
      valueSchemas <- fetchAllSchemasWithVersioning(client, baseUrl, valueSubjects, SchemaRole.Value, fetchAllVersions)
      keySchemas <- fetchAllSchemasWithVersioning(client, baseUrl, keySubjects, SchemaRole.Key, fetchAllVersions)
    } yield valueSchemas ++ keySchemas
  }

  /** List all subjects in the registry */
  private def listSubjects(client: HttpClient, baseUrl: String): Either[AvroParseError, List[String]] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"$baseUrl/subjects"))
      .header("Accept", "application/vnd.schemaregistry.v1+json")
      .GET()
      .build()

    Try(client.send(request, HttpResponse.BodyHandlers.ofString())) match {
      case Success(response) if response.statusCode() == 200 =>
        parseJsonArray(response.body())
      case Success(response) =>
        Left(AvroParseError.UnexpectedError(s"Schema Registry returned status ${response.statusCode()}: ${response.body()}"))
      case Failure(e) =>
        Left(AvroParseError.UnexpectedError(s"Failed to connect to Schema Registry: ${e.getMessage}"))
    }
  }

  /** Fetch schemas for all given subjects with optional versioning support */
  private def fetchAllSchemasWithVersioning(
      client: HttpClient,
      baseUrl: String,
      subjects: List[String],
      schemaRole: SchemaRole,
      fetchAllVersions: Boolean
  ): Either[AvroParseError, List[AvroSchemaFile]] = {
    // Determine suffix to strip based on role
    val suffix = schemaRole match {
      case SchemaRole.Value => "-value"
      case SchemaRole.Key   => "-key"
    }

    val results = subjects.flatMap { subject =>
      val topicName = subject.stripSuffix(suffix)
      val directoryGroup = if (topicName.contains("-")) Some(topicName) else None

      if (fetchAllVersions) {
        // Fetch all versions for this subject
        fetchAllVersionsForSubject(client, baseUrl, subject) match {
          case Left(error) => List(Left(error))
          case Right(versionedSchemas) =>
            versionedSchemas.map { vs =>
              parseSchemaJsonWithVersion(vs.schemaJson, topicName, directoryGroup, schemaRole, Some(vs.version))
            }
        }
      } else {
        // Fetch only the latest version
        List(
          fetchLatestSchema(client, baseUrl, subject).flatMap { schemaJson =>
            parseSchemaJsonWithVersion(schemaJson, topicName, directoryGroup, schemaRole, version = None)
          }
        )
      }
    }

    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) {
      Left(AvroParseError.MultipleErrors(errors.map(_.message)))
    } else {
      Right(results.collect { case Right(s) => s })
    }
  }

  /** Fetch all versions for a subject from Schema Registry */
  private def fetchAllVersionsForSubject(
      client: HttpClient,
      baseUrl: String,
      subject: String
  ): Either[AvroParseError, List[VersionedSchema]] = {
    // First, get the list of version numbers
    listVersions(client, baseUrl, subject).flatMap { versions =>
      // Fetch each version's schema
      val results = versions.sorted.map { version =>
        fetchSchemaByVersion(client, baseUrl, subject, version)
      }

      val errors = results.collect { case Left(e) => e }
      if (errors.nonEmpty) {
        Left(AvroParseError.MultipleErrors(errors.map(_.message)))
      } else {
        Right(results.collect { case Right(vs) => vs })
      }
    }
  }

  /** List all version numbers for a subject */
  private def listVersions(
      client: HttpClient,
      baseUrl: String,
      subject: String
  ): Either[AvroParseError, List[Int]] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"$baseUrl/subjects/$subject/versions"))
      .header("Accept", "application/vnd.schemaregistry.v1+json")
      .GET()
      .build()

    Try(client.send(request, HttpResponse.BodyHandlers.ofString())) match {
      case Success(response) if response.statusCode() == 200 =>
        parseIntArray(response.body())
      case Success(response) =>
        Left(AvroParseError.UnexpectedError(s"Failed to list versions for $subject: ${response.statusCode()}"))
      case Failure(e) =>
        Left(AvroParseError.UnexpectedError(s"Failed to list versions for $subject: ${e.getMessage}"))
    }
  }

  /** Fetch a specific version of a schema */
  private def fetchSchemaByVersion(
      client: HttpClient,
      baseUrl: String,
      subject: String,
      version: Int
  ): Either[AvroParseError, VersionedSchema] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"$baseUrl/subjects/$subject/versions/$version"))
      .header("Accept", "application/vnd.schemaregistry.v1+json")
      .GET()
      .build()

    Try(client.send(request, HttpResponse.BodyHandlers.ofString())) match {
      case Success(response) if response.statusCode() == 200 =>
        extractVersionedSchemaFromResponse(response.body(), version)
      case Success(response) =>
        Left(AvroParseError.UnexpectedError(s"Failed to fetch version $version for $subject: ${response.statusCode()}"))
      case Failure(e) =>
        Left(AvroParseError.UnexpectedError(s"Failed to fetch version $version for $subject: ${e.getMessage}"))
    }
  }

  /** Extract schema and id from versioned registry response */
  private def extractVersionedSchemaFromResponse(responseBody: String, version: Int): Either[AvroParseError, VersionedSchema] = {
    for {
      schemaJson <- extractSchemaFromResponse(responseBody)
      schemaId <- extractIdFromResponse(responseBody)
    } yield VersionedSchema(schemaJson, version, schemaId)
  }

  /** Extract schema ID from registry response */
  private def extractIdFromResponse(responseBody: String): Either[AvroParseError, Int] = {
    try {
      val idStart = responseBody.indexOf("\"id\":")
      if (idStart < 0) {
        return Left(AvroParseError.UnexpectedError("No 'id' field in registry response"))
      }

      val afterColon = responseBody.indexOf(':', idStart) + 1
      val remaining = responseBody.substring(afterColon).trim
      val endIdx = remaining.indexWhere(c => !c.isDigit && c != '-')
      val numStr = if (endIdx < 0) remaining else remaining.substring(0, endIdx)
      Right(numStr.toInt)
    } catch {
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(s"Failed to parse schema id: ${e.getMessage}"))
    }
  }

  /** Parse a JSON array of integers */
  private def parseIntArray(json: String): Either[AvroParseError, List[Int]] = {
    try {
      val trimmed = json.trim
      if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
        return Left(AvroParseError.UnexpectedError("Expected JSON array"))
      }

      val content = trimmed.substring(1, trimmed.length - 1).trim
      if (content.isEmpty) {
        return Right(Nil)
      }

      Right(content.split(",").map(_.trim.toInt).toList)
    } catch {
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(s"Failed to parse integer array: ${e.getMessage}"))
    }
  }

  /** Fetch the latest schema for a subject */
  private def fetchLatestSchema(
      client: HttpClient,
      baseUrl: String,
      subject: String
  ): Either[AvroParseError, String] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"$baseUrl/subjects/$subject/versions/latest"))
      .header("Accept", "application/vnd.schemaregistry.v1+json")
      .GET()
      .build()

    Try(client.send(request, HttpResponse.BodyHandlers.ofString())) match {
      case Success(response) if response.statusCode() == 200 =>
        extractSchemaFromResponse(response.body())
      case Success(response) =>
        Left(AvroParseError.UnexpectedError(s"Failed to fetch schema for $subject: ${response.statusCode()}"))
      case Failure(e) =>
        Left(AvroParseError.UnexpectedError(s"Failed to fetch schema for $subject: ${e.getMessage}"))
    }
  }

  /** Parse the schema JSON from the registry response */
  private def extractSchemaFromResponse(responseBody: String): Either[AvroParseError, String] = {
    // The response is JSON like: {"subject":"...", "version":1, "id":1, "schema":"..."}
    // The "schema" field contains an escaped JSON string
    try {
      val schemaStart = responseBody.indexOf("\"schema\":")
      if (schemaStart < 0) {
        return Left(AvroParseError.UnexpectedError("No 'schema' field in registry response"))
      }

      val afterColon = responseBody.indexOf(':', schemaStart) + 1
      val trimmed = responseBody.substring(afterColon).trim

      if (!trimmed.startsWith("\"")) {
        return Left(AvroParseError.UnexpectedError("Schema field is not a string"))
      }

      // Find the end of the escaped string
      var i = 1
      var escaped = false
      val sb = new StringBuilder()
      while (i < trimmed.length) {
        val c = trimmed.charAt(i)
        if (escaped) {
          c match {
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case 'n'  => sb.append('\n')
            case 'r'  => sb.append('\r')
            case 't'  => sb.append('\t')
            case _    => sb.append(c)
          }
          escaped = false
        } else if (c == '\\') {
          escaped = true
        } else if (c == '"') {
          // End of string
          return Right(sb.toString())
        } else {
          sb.append(c)
        }
        i += 1
      }

      Left(AvroParseError.UnexpectedError("Unterminated schema string"))
    } catch {
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(s"Failed to parse registry response: ${e.getMessage}"))
    }
  }

  /** Parse a JSON array of strings */
  private def parseJsonArray(json: String): Either[AvroParseError, List[String]] = {
    try {
      val trimmed = json.trim
      if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
        return Left(AvroParseError.UnexpectedError("Expected JSON array"))
      }

      val content = trimmed.substring(1, trimmed.length - 1).trim
      if (content.isEmpty) {
        return Right(Nil)
      }

      // Simple parsing for string arrays
      val items = content
        .split(",")
        .map(_.trim)
        .map { item =>
          if (item.startsWith("\"") && item.endsWith("\"")) {
            item.substring(1, item.length - 1)
          } else {
            item
          }
        }
        .toList

      Right(items)
    } catch {
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(s"Failed to parse JSON array: ${e.getMessage}"))
    }
  }

  /** Parse schema JSON into an AvroSchemaFile with optional version */
  private def parseSchemaJsonWithVersion(
      schemaJson: String,
      topicName: String,
      directoryGroup: Option[String],
      schemaRole: SchemaRole,
      version: Option[Int]
  ): Either[AvroParseError, AvroSchemaFile] = {
    AvroParser.parseSchemaWithRole(schemaJson, Some(topicName), schemaRole).map { schemaFile =>
      schemaFile.copy(directoryGroup = directoryGroup, version = version)
    }
  }
}
