package typr.avro.parser

import java.nio.file.{Files, Path}
import scala.collection.mutable

/** Resolves `$ref` references in Avro schema files.
  *
  * Apache Avro does not natively support `$ref` (unlike JSON Schema). This resolver collects referenced files so they can be parsed first, then replaces $ref with the type name reference.
  *
  * Supported syntax:
  *   - `{"$ref": "./Address.avsc"}` - relative to current file
  *   - `{"$ref": "../common/Address.avsc"}` - relative path
  *
  * The resolver:
  *   - Collects all referenced files for dependency ordering
  *   - Extracts the full type name from referenced schemas
  *   - Replaces $ref with the type name string
  */
object RefResolver {

  /** Result of resolving refs in a schema file */
  case class ResolveResult(
      /** The resolved JSON (with $ref replaced by type names) */
      json: String,
      /** Paths to files that must be parsed before this one */
      dependencies: Set[Path]
  )

  /** Resolve all `$ref` in a schema file.
    *
    * Returns the resolved JSON where each `{"$ref": "path"}` is replaced with the fully qualified type name from the referenced schema.
    *
    * @param schemaPath
    *   Path to the schema file being resolved
    * @param json
    *   The JSON content of the schema
    * @param resolving
    *   Set of paths currently being resolved (for cycle detection)
    * @return
    *   Either an error or the resolve result with dependencies
    */
  def resolve(
      schemaPath: Path,
      json: String,
      resolving: mutable.Set[Path]
  ): Either[AvroParseError, ResolveResult] = {
    val normalizedPath = schemaPath.toAbsolutePath.normalize()

    if (resolving.contains(normalizedPath)) {
      return Left(AvroParseError.UnexpectedError(s"Circular ${"$"}ref detected: $normalizedPath"))
    }

    resolving += normalizedPath

    val baseDir = normalizedPath.getParent
    val dependencies = mutable.Set.empty[Path]

    resolveJsonRefs(json, baseDir, resolving, dependencies) match {
      case Right(resolved) =>
        resolving -= normalizedPath
        Right(ResolveResult(resolved, dependencies.toSet))
      case Left(error) =>
        resolving -= normalizedPath
        Left(error)
    }
  }

  /** Extract the full type name (namespace.name) from an Avro schema JSON string */
  def extractTypeName(json: String): Either[AvroParseError, String] = {
    val name = extractJsonField(json, "name")
    val namespace = extractJsonField(json, "namespace")

    name match {
      case Some(n) =>
        val fullName = namespace match {
          case Some(ns) => s"$ns.$n"
          case None     => n
        }
        Right(fullName)
      case None =>
        Left(AvroParseError.UnexpectedError("Referenced schema has no 'name' field"))
    }
  }

  /** Extract a string field value from JSON (simple parsing without full JSON library) */
  private def extractJsonField(json: String, fieldName: String): Option[String] = {
    val pattern = s""""$fieldName"\\s*:\\s*"([^"]+)"""".r
    pattern.findFirstMatchIn(json).map(_.group(1))
  }

  /** Resolve `$ref` references in a JSON string.
    *
    * This uses a simple JSON parser that handles the specific case of $ref objects. It does not use a full JSON library to avoid dependencies.
    */
  private def resolveJsonRefs(
      json: String,
      baseDir: Path,
      resolving: mutable.Set[Path],
      dependencies: mutable.Set[Path]
  ): Either[AvroParseError, String] = {
    val result = new StringBuilder
    var i = 0

    while (i < json.length) {
      val c = json.charAt(i)

      if (c == '{') {
        // Check if this is a $ref object
        val (refResult, newIndex) = tryParseRefObject(json, i, baseDir, resolving, dependencies)
        refResult match {
          case Right(Some(typeName)) =>
            // Replace $ref with the type name as a string reference
            result.append('"')
            result.append(typeName)
            result.append('"')
            i = newIndex
          case Right(None) =>
            // Not a $ref object, continue parsing
            result.append(c)
            i += 1
          case Left(error) =>
            return Left(error)
        }
      } else if (c == '"') {
        // Copy string literals as-is
        val (str, newIndex) = copyString(json, i)
        result.append(str)
        i = newIndex
      } else {
        result.append(c)
        i += 1
      }
    }

    Right(result.toString)
  }

  /** Try to parse a $ref object starting at position i.
    *
    * Returns:
    *   - Right(Some(typeName)) if this is a $ref object
    *   - Right(None) if this is not a $ref object
    *   - Left(error) if parsing failed
    */
  private def tryParseRefObject(
      json: String,
      startIndex: Int,
      baseDir: Path,
      resolving: mutable.Set[Path],
      dependencies: mutable.Set[Path]
  ): (Either[AvroParseError, Option[String]], Int) = {
    // Look for {"$ref": "..."}
    var i = startIndex + 1
    i = skipWhitespace(json, i)

    if (i >= json.length || json.charAt(i) != '"') {
      return (Right(None), startIndex + 1)
    }

    // Parse the key
    val (key, afterKey) = parseString(json, i)
    i = afterKey

    if (key != "$ref") {
      return (Right(None), startIndex + 1)
    }

    i = skipWhitespace(json, i)
    if (i >= json.length || json.charAt(i) != ':') {
      return (Right(None), startIndex + 1)
    }
    i += 1
    i = skipWhitespace(json, i)

    if (i >= json.length || json.charAt(i) != '"') {
      return (Left(AvroParseError.UnexpectedError("Expected string value for $ref")), i)
    }

    // Parse the $ref value (path)
    val (refPath, afterValue) = parseString(json, i)
    i = afterValue
    i = skipWhitespace(json, i)

    if (i >= json.length || json.charAt(i) != '}') {
      return (Left(AvroParseError.UnexpectedError("Expected closing brace after $ref value")), i)
    }
    i += 1 // Skip closing brace

    // Resolve the referenced schema path
    val referencedPath = baseDir.resolve(refPath).toAbsolutePath.normalize()
    if (!Files.exists(referencedPath)) {
      return (Left(AvroParseError.FileReadError(referencedPath.toString, "Referenced file not found")), i)
    }

    // Add to dependencies
    dependencies += referencedPath

    // Read the referenced schema to extract the type name
    try {
      val referencedJson = Files.readString(referencedPath)

      // First resolve any $refs in the referenced file (for transitive dependencies)
      resolve(referencedPath, referencedJson, resolving) match {
        case Right(result) =>
          dependencies ++= result.dependencies
          extractTypeName(referencedJson) match {
            case Right(typeName) => (Right(Some(typeName)), i)
            case Left(error)     => (Left(error), i)
          }
        case Left(error) => (Left(error), i)
      }
    } catch {
      case e: Exception =>
        (Left(AvroParseError.FileReadError(referencedPath.toString, e.getMessage)), i)
    }
  }

  /** Parse a JSON string starting at position i (which should be on the opening quote).
    *
    * @return
    *   Tuple of (parsed string without quotes, index after the closing quote)
    */
  private def parseString(json: String, startIndex: Int): (String, Int) = {
    val sb = new StringBuilder
    var i = startIndex + 1 // Skip opening quote
    var escaped = false

    while (i < json.length) {
      val c = json.charAt(i)
      if (escaped) {
        c match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '/'  => sb.append('/')
          case 'b'  => sb.append('\b')
          case 'f'  => sb.append('\f')
          case 'n'  => sb.append('\n')
          case 'r'  => sb.append('\r')
          case 't'  => sb.append('\t')
          case 'u'  =>
            // Unicode escape
            if (i + 4 < json.length) {
              val hex = json.substring(i + 1, i + 5)
              sb.append(Integer.parseInt(hex, 16).toChar)
              i += 4
            }
          case _ => sb.append(c)
        }
        escaped = false
      } else if (c == '\\') {
        escaped = true
      } else if (c == '"') {
        return (sb.toString, i + 1)
      } else {
        sb.append(c)
      }
      i += 1
    }

    (sb.toString, i)
  }

  /** Copy a JSON string literal including quotes.
    *
    * @return
    *   Tuple of (the string including quotes, index after the closing quote)
    */
  private def copyString(json: String, startIndex: Int): (String, Int) = {
    val sb = new StringBuilder
    sb.append(json.charAt(startIndex)) // Opening quote
    var i = startIndex + 1
    var escaped = false

    while (i < json.length) {
      val c = json.charAt(i)
      sb.append(c)

      if (escaped) {
        escaped = false
      } else if (c == '\\') {
        escaped = true
      } else if (c == '"') {
        return (sb.toString, i + 1)
      }
      i += 1
    }

    (sb.toString, i)
  }

  private def skipWhitespace(json: String, startIndex: Int): Int = {
    var i = startIndex
    while (i < json.length && json.charAt(i).isWhitespace) {
      i += 1
    }
    i
  }

  /** Check if a JSON string contains any $ref references.
    *
    * This is a quick check to avoid unnecessary processing.
    */
  def containsRefs(json: String): Boolean = {
    json.contains("\"$ref\"")
  }
}
