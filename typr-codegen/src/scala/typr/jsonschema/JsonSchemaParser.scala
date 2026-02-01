package typr.jsonschema

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.ObjectMapperFactory
import typr.openapi.parser.ModelExtractor
import typr.openapi.{ModelClass, SumType}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

object JsonSchemaParser {

  case class ParsedJsonSchema(
      models: List[ModelClass],
      sumTypes: List[SumType]
  )

  def parseFile(path: Path): Either[List[String], ParsedJsonSchema] = {
    val content = Files.readString(path)
    val isYaml = path.toString.endsWith(".yaml") || path.toString.endsWith(".yml")
    parseContent(content, isYaml)
  }

  def parseContent(content: String, isYaml: Boolean): Either[List[String], ParsedJsonSchema] = {
    try {
      val mapper = if (isYaml) ObjectMapperFactory.createYaml() else ObjectMapperFactory.createJson()
      val rootNode = mapper.readTree(content)

      val defsNode = Option(rootNode.get("$defs"))
        .orElse(Option(rootNode.get("definitions")))

      defsNode match {
        case Some(defs) if defs.isObject =>
          val schemasMap = new java.util.LinkedHashMap[String, Schema[?]]()
          defs.fields().asScala.foreach { entry =>
            val schemaJson = entry.getValue.toString
            val schema = Json.mapper().readValue(schemaJson, classOf[Schema[?]])
            schemasMap.put(entry.getKey, schema)
          }

          val openApi = new OpenAPI()
          openApi.setOpenapi("3.1.0")
          val info = new Info()
          info.setTitle("Generated")
          info.setVersion("1.0.0")
          openApi.setInfo(info)
          val components = new io.swagger.v3.oas.models.Components()
          components.setSchemas(schemasMap)
          openApi.setComponents(components)

          val extracted = ModelExtractor.extract(openApi)
          val linkedModels = linkSumTypeParents(extracted.models, extracted.sumTypes)

          Right(ParsedJsonSchema(linkedModels, extracted.sumTypes))

        case Some(_) =>
          Left(List("$defs or definitions must be an object"))

        case None =>
          if (rootNode.has("type") || rootNode.has("properties")) {
            val schemaJson = rootNode.toString
            val schema = Json.mapper().readValue(schemaJson, classOf[Schema[?]])
            val schemasMap = new java.util.LinkedHashMap[String, Schema[?]]()
            schemasMap.put("Root", schema)

            val openApi = new OpenAPI()
            openApi.setOpenapi("3.1.0")
            val info = new Info()
            info.setTitle("Generated")
            info.setVersion("1.0.0")
            openApi.setInfo(info)
            val components = new io.swagger.v3.oas.models.Components()
            components.setSchemas(schemasMap)
            openApi.setComponents(components)

            val extracted = ModelExtractor.extract(openApi)
            Right(ParsedJsonSchema(extracted.models, extracted.sumTypes))
          } else {
            Left(List("JSON Schema must have $defs, definitions, or be a root schema with type/properties"))
          }
      }
    } catch {
      case e: Exception =>
        Left(List(s"Failed to parse JSON Schema: ${e.getMessage}"))
    }
  }

  private def linkSumTypeParents(models: List[ModelClass], sumTypes: List[SumType]): List[ModelClass] = {
    val pairs = sumTypes.flatMap(st => st.subtypeNames.map(sub => sub -> st.name))
    val sumTypesBySubtype: Map[String, List[String]] = pairs.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2) }

    // Only add discriminator values for property-based discrimination (not type inference)
    val discriminatorPairs = sumTypes.filterNot(_.usesTypeInference).flatMap { st =>
      st.subtypeNames.map { subtypeName =>
        val discValue = st.discriminator.mapping.getOrElse(subtypeName, subtypeName)
        subtypeName -> (st.discriminator.propertyName -> discValue)
      }
    }
    val discriminatorsBySubtype: Map[String, Map[String, String]] = discriminatorPairs
      .groupBy(_._1)
      .map { case (k, vs) => k -> vs.map(_._2).toMap }

    models.map {
      case obj: ModelClass.ObjectType =>
        val parents = sumTypesBySubtype.getOrElse(obj.name, Nil)
        val discriminators = discriminatorsBySubtype.getOrElse(obj.name, Map.empty)
        if (parents.nonEmpty || discriminators.nonEmpty) {
          obj.copy(sumTypeParents = parents, discriminatorValues = discriminators)
        } else {
          obj
        }
      case other => other
    }
  }
}
