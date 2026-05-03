package typr.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import typr.openapi.OpenApiError
import typr.openapi.OpenApiError._

import scala.jdk.CollectionConverters._

/** Validates parsed OpenAPI specs and produces helpful error messages */
object SpecValidator {

  /** Validate an OpenAPI spec and return any errors found */
  def validate(openApi: OpenAPI): List[OpenApiError] = {
    val errors = List.newBuilder[OpenApiError]

    // Validate basic structure
    validateInfo(openApi, errors)
    validateSchemas(openApi, errors)
    validatePaths(openApi, errors)
    validateReferences(openApi, errors)

    errors.result()
  }

  private def validateInfo(openApi: OpenAPI, errors: collection.mutable.Builder[OpenApiError, List[OpenApiError]]): Unit = {
    if (openApi.getInfo == null) {
      errors += MissingFieldError("openapi spec", "info")
    } else {
      val info = openApi.getInfo
      if (info.getTitle == null || info.getTitle.isEmpty) {
        errors += MissingFieldError("info", "title", suggestion = Some("Add a title to your API spec"))
      }
      if (info.getVersion == null || info.getVersion.isEmpty) {
        errors += MissingFieldError("info", "version", suggestion = Some("Add a version like '1.0.0'"))
      }
    }
  }

  private def validateSchemas(openApi: OpenAPI, errors: collection.mutable.Builder[OpenApiError, List[OpenApiError]]): Unit = {
    val schemas = Option(openApi.getComponents)
      .flatMap(c => Option(c.getSchemas))
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)

    schemas.foreach { case (name, schema) =>
      // Validate discriminator
      if (schema.getDiscriminator != null) {
        val discriminator = schema.getDiscriminator
        if (discriminator.getPropertyName == null) {
          errors += InvalidDiscriminatorError(
            name,
            "discriminator.propertyName is required",
            suggestion = Some("Add a propertyName to the discriminator")
          )
        }

        // Check if discriminator property exists in schema properties
        val propertyName = discriminator.getPropertyName
        if (propertyName != null && schema.getProperties != null) {
          if (!schema.getProperties.containsKey(propertyName)) {
            errors += InvalidDiscriminatorError(
              name,
              s"discriminator property '$propertyName' not found in schema properties",
              suggestion = Some(s"Add '$propertyName' as a property in the schema")
            )
          }
        }
      }

      // Validate oneOf/anyOf with discriminator
      if (schema.getOneOf != null || schema.getAnyOf != null) {
        val subtypes = Option(schema.getOneOf).orElse(Option(schema.getAnyOf)).map(_.asScala).getOrElse(Nil)
        if (subtypes.isEmpty) {
          errors += SchemaValidationError(
            name,
            "oneOf/anyOf array is empty",
            suggestion = Some("Add at least one subtype reference")
          )
        }
      }
    }
  }

  private def validatePaths(openApi: OpenAPI, errors: collection.mutable.Builder[OpenApiError, List[OpenApiError]]): Unit = {
    val paths = Option(openApi.getPaths).map(_.asScala.toMap).getOrElse(Map.empty)

    paths.foreach { case (path, pathItem) =>
      // Validate path parameters match
      val pathParamPattern = "\\{([^}]+)\\}".r
      val pathParams = pathParamPattern.findAllMatchIn(path).map(_.group(1)).toSet

      // Check each operation
      val operations = List(
        Option(pathItem.getGet).map("GET" -> _),
        Option(pathItem.getPost).map("POST" -> _),
        Option(pathItem.getPut).map("PUT" -> _),
        Option(pathItem.getDelete).map("DELETE" -> _),
        Option(pathItem.getPatch).map("PATCH" -> _)
      ).flatten

      operations.foreach { case (method, operation) =>
        // Check that path parameters are defined
        val definedParams = Option(operation.getParameters)
          .map(_.asScala.filter(p => p.getIn == "path").map(_.getName).toSet)
          .getOrElse(Set.empty) ++
          Option(pathItem.getParameters)
            .map(_.asScala.filter(p => p.getIn == "path").map(_.getName).toSet)
            .getOrElse(Set.empty)

        val missingParams = pathParams -- definedParams
        missingParams.foreach { param =>
          errors += ValidationError(
            s"Path parameter '{$param}' in '$path' is not defined in $method operation parameters",
            suggestion = Some(s"Add a parameter with name '$param' and in: path")
          )
        }
      }
    }
  }

  private def validateReferences(openApi: OpenAPI, errors: collection.mutable.Builder[OpenApiError, List[OpenApiError]]): Unit = {
    val definedSchemas = Option(openApi.getComponents)
      .flatMap(c => Option(c.getSchemas))
      .map(_.keySet().asScala.toSet)
      .getOrElse(Set.empty)

    // This is a simplified check - a full implementation would traverse all schemas
    // and check that all $refs point to existing schemas
    def checkRef(ref: String, context: String): Unit = {
      if (ref != null && ref.startsWith("#/components/schemas/")) {
        val schemaName = ref.substring("#/components/schemas/".length)
        if (!definedSchemas.contains(schemaName)) {
          errors += UnresolvedRefError(ref, context)
        }
      }
    }

    // Check schemas for references
    Option(openApi.getComponents)
      .flatMap(c => Option(c.getSchemas))
      .map(_.asScala)
      .getOrElse(Map.empty)
      .foreach { case (name, schema) =>
        if (schema.get$ref() != null) {
          checkRef(schema.get$ref(), s"schema '$name'")
        }

        // Check properties
        Option(schema.getProperties).map(_.asScala).getOrElse(Map.empty).foreach { case (propName, propSchema) =>
          if (propSchema.get$ref() != null) {
            checkRef(propSchema.get$ref(), s"property '$propName' in schema '$name'")
          }
        }
      }
  }
}
