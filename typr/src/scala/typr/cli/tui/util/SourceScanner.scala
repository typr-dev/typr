package typr.cli.tui.util

import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Scans a directory for potential source files (OpenAPI specs, JSON schemas, etc.) */
object SourceScanner {

  /** Type of discovered source */
  sealed trait DiscoveredSourceType
  object DiscoveredSourceType {
    case object OpenApi extends DiscoveredSourceType
    case object JsonSchema extends DiscoveredSourceType
    case object YamlConfig extends DiscoveredSourceType
  }

  /** A discovered source file */
  case class DiscoveredSource(
      path: Path,
      relativePath: String,
      sourceType: DiscoveredSourceType,
      title: Option[String],
      preview: String
  )

  /** Scan a directory for source files */
  def scan(baseDir: Path, maxDepth: Int = 3): List[DiscoveredSource] = {
    if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
      return Nil
    }

    val results = List.newBuilder[DiscoveredSource]

    try {
      Files.walk(baseDir, maxDepth).iterator().asScala.foreach { path =>
        if (Files.isRegularFile(path)) {
          val fileName = path.getFileName.toString.toLowerCase

          // Check YAML files
          if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            detectYamlType(baseDir, path).foreach(results += _)
          }
          // Check JSON files
          else if (fileName.endsWith(".json")) {
            detectJsonType(baseDir, path).foreach(results += _)
          }
        }
      }
    } catch {
      case _: Exception =>
    }

    results.result().sortBy(_.relativePath)
  }

  private def detectYamlType(baseDir: Path, path: Path): Option[DiscoveredSource] = {
    Try {
      val content = Files.readString(path)
      val lines = content.linesIterator.take(50).toList
      val relativePath = baseDir.relativize(path).toString

      // Check for OpenAPI spec
      if (lines.exists(l => l.trim.startsWith("openapi:") || l.trim.startsWith("swagger:"))) {
        val title = extractYamlTitle(lines)
        val preview = lines.take(5).mkString("\n")
        Some(
          DiscoveredSource(
            path = path,
            relativePath = relativePath,
            sourceType = DiscoveredSourceType.OpenApi,
            title = title,
            preview = preview
          )
        )
      }
      // Check for JSON Schema in YAML format
      else if (lines.exists(l => l.contains("$schema") || l.contains("$defs") || l.contains("definitions"))) {
        val title = extractYamlTitle(lines)
        val preview = lines.take(5).mkString("\n")
        Some(
          DiscoveredSource(
            path = path,
            relativePath = relativePath,
            sourceType = DiscoveredSourceType.JsonSchema,
            title = title,
            preview = preview
          )
        )
      } else {
        None
      }
    }.toOption.flatten
  }

  private def detectJsonType(baseDir: Path, path: Path): Option[DiscoveredSource] = {
    Try {
      val content = Files.readString(path)
      val relativePath = baseDir.relativize(path).toString
      val preview = content.take(200)

      // Simple JSON detection without full parsing
      val lowerContent = content.toLowerCase

      // Check for OpenAPI spec
      if (lowerContent.contains("\"openapi\"") || lowerContent.contains("\"swagger\"")) {
        val title = extractJsonTitle(content)
        Some(
          DiscoveredSource(
            path = path,
            relativePath = relativePath,
            sourceType = DiscoveredSourceType.OpenApi,
            title = title,
            preview = preview
          )
        )
      }
      // Check for JSON Schema
      else if (
        lowerContent.contains("\"$schema\"") || lowerContent.contains("\"$defs\"") ||
        lowerContent.contains("\"definitions\"") || path.toString.endsWith(".schema.json")
      ) {
        val title = extractJsonTitle(content)
        Some(
          DiscoveredSource(
            path = path,
            relativePath = relativePath,
            sourceType = DiscoveredSourceType.JsonSchema,
            title = title,
            preview = preview
          )
        )
      } else {
        None
      }
    }.toOption.flatten
  }

  private def extractYamlTitle(lines: List[String]): Option[String] = {
    lines.collectFirst {
      case l if l.trim.startsWith("title:") =>
        l.trim.stripPrefix("title:").trim.stripPrefix("\"").stripSuffix("\"").trim
    }
  }

  private def extractJsonTitle(content: String): Option[String] = {
    // Simple regex to extract title from JSON
    val titlePattern = """"title"\s*:\s*"([^"]+)"""".r
    titlePattern.findFirstMatchIn(content).map(_.group(1))
  }

  /** Get a suggested source name from a file path */
  def suggestSourceName(path: Path): String = {
    val fileName = path.getFileName.toString
    val baseName = fileName
      .replaceAll("\\.(yaml|yml|json)$", "")
      .replaceAll("\\.schema$", "")
      .replaceAll("[^a-zA-Z0-9_-]", "-")
      .toLowerCase

    if (baseName.isEmpty) "source" else baseName
  }

  /** Get display name for source type */
  def sourceTypeDisplay(sourceType: DiscoveredSourceType): String = sourceType match {
    case DiscoveredSourceType.OpenApi    => "OpenAPI"
    case DiscoveredSourceType.JsonSchema => "JSON Schema"
    case DiscoveredSourceType.YamlConfig => "YAML Config"
  }
}
