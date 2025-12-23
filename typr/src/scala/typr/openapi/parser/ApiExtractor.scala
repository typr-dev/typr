package typr.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.Parameter
import typr.openapi._

import scala.jdk.CollectionConverters._

/** Extracts API interfaces from OpenAPI paths */
object ApiExtractor {

  /** Extract all API interfaces from the OpenAPI spec, grouped by tag */
  def extract(openApi: OpenAPI): List[ApiInterface] = {
    val paths = Option(openApi.getPaths).map(_.asScala.toMap).getOrElse(Map.empty[String, PathItem])

    val allMethods = paths.flatMap { case (path, pathItem) =>
      extractPathMethods(path, pathItem)
    }.toList

    // Group by first tag (or "Default" if no tags) - compatible with Scala 2.12
    val groupedByTag = allMethods.groupBy { method =>
      method.tags.headOption.getOrElse("Default")
    }

    groupedByTag
      .map { case (tag, methods) =>
        ApiInterface(
          name = sanitizeClassName(tag) + "Api",
          description = None,
          methods = methods.sortBy(_.name)
        )
      }
      .toList
      .sortBy(_.name)
  }

  /** Extract webhooks from OpenAPI 3.1+ spec */
  def extractWebhooks(openApi: OpenAPI): List[Webhook] = {
    val webhooks = Option(openApi.getWebhooks).map(_.asScala.toMap).getOrElse(Map.empty[String, PathItem])

    webhooks
      .map { case (name, pathItem) =>
        val description = Option(pathItem.getDescription)
        val methods = extractPathMethods(s"/webhook/$name", pathItem)

        Webhook(
          name = sanitizeClassName(name),
          description = description,
          methods = methods
        )
      }
      .toList
      .sortBy(_.name)
  }

  private def extractPathMethods(path: String, pathItem: PathItem): List[ApiMethod] = {
    val methods = List.newBuilder[ApiMethod]

    // Extract path-level parameters (shared across all operations)
    val pathLevelParams = Option(pathItem.getParameters)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .map(extractParameter)

    def addMethod(httpMethod: HttpMethod, operation: Operation): Unit = {
      if (operation != null) {
        methods += extractMethod(path, httpMethod, operation, pathLevelParams)
      }
    }

    addMethod(HttpMethod.Get, pathItem.getGet)
    addMethod(HttpMethod.Post, pathItem.getPost)
    addMethod(HttpMethod.Put, pathItem.getPut)
    addMethod(HttpMethod.Delete, pathItem.getDelete)
    addMethod(HttpMethod.Patch, pathItem.getPatch)
    addMethod(HttpMethod.Head, pathItem.getHead)
    addMethod(HttpMethod.Options, pathItem.getOptions)

    methods.result()
  }

  private def extractMethod(
      path: String,
      httpMethod: HttpMethod,
      operation: Operation,
      pathLevelParams: List[ApiParameter]
  ): ApiMethod = {
    val operationId = Option(operation.getOperationId).getOrElse(generateOperationId(httpMethod, path))

    // Merge path-level and operation-level parameters (operation params override path params)
    val operationParams = extractParameters(operation)
    val operationParamNames = operationParams.map(_.originalName).toSet
    val mergedParams = pathLevelParams.filterNot(p => operationParamNames.contains(p.originalName)) ++ operationParams

    // Extract response variants for multi-status response sum types
    val responseVariants = extractResponseVariants(operation)

    // Extract callbacks
    val callbacks = extractCallbacks(operation)

    ApiMethod(
      name = sanitizeMethodName(operationId),
      description = Option(operation.getSummary).orElse(Option(operation.getDescription)),
      httpMethod = httpMethod,
      path = path,
      parameters = mergedParams,
      requestBody = extractRequestBody(operation),
      responses = extractResponses(operation),
      deprecated = Option(operation.getDeprecated).contains(java.lang.Boolean.TRUE),
      security = extractSecurity(operation),
      tags = Option(operation.getTags).map(_.asScala.toList).getOrElse(Nil),
      responseVariants = responseVariants,
      callbacks = callbacks
    )
  }

  private def extractParameters(operation: Operation): List[ApiParameter] = {
    Option(operation.getParameters)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .map(extractParameter)
  }

  private def extractParameter(param: Parameter): ApiParameter = {
    val paramIn = param.getIn.toLowerCase match {
      case "path"   => ParameterIn.Path
      case "query"  => ParameterIn.Query
      case "header" => ParameterIn.Header
      case "cookie" => ParameterIn.Cookie
      case _        => ParameterIn.Query
    }

    val required = paramIn == ParameterIn.Path || Option(param.getRequired).contains(java.lang.Boolean.TRUE)
    val schema = param.getSchema
    val typeInfo = TypeResolver.resolve(schema, required)

    // Extract default value if present
    val defaultValue = Option(schema).flatMap { s =>
      Option(s.getDefault).map(d => formatDefaultValue(d))
    }

    ApiParameter(
      name = sanitizeParameterName(param.getName),
      originalName = param.getName,
      description = Option(param.getDescription),
      in = paramIn,
      typeInfo = typeInfo,
      required = required,
      deprecated = Option(param.getDeprecated).contains(java.lang.Boolean.TRUE),
      defaultValue = defaultValue
    )
  }

  private def formatDefaultValue(value: Any): String = {
    // Return raw value - quoting is handled by the code generator
    value match {
      case s: String  => s
      case n: Number  => n.toString
      case b: Boolean => b.toString
      case other      => other.toString
    }
  }

  private def extractRequestBody(operation: Operation): Option[RequestBody] = {
    val body = operation.getRequestBody
    if (body == null) None
    else {
      val content = Option(body.getContent).map(_.asScala.toMap).getOrElse(Map.empty[String, io.swagger.v3.oas.models.media.MediaType])

      // Prefer JSON content type, but handle multipart specially
      val multipartEntry = content.find(_._1 == "multipart/form-data")
      val jsonEntry = content.find(_._1.contains("json"))
      val entry = multipartEntry.orElse(jsonEntry).orElse(content.headOption)

      entry.flatMap { case (contentType, mediaType) =>
        val schema = mediaType.getSchema
        if (schema == null) None
        else {
          val formFields = if (contentType == "multipart/form-data") {
            extractFormFields(schema)
          } else {
            Nil
          }

          Some(
            RequestBody(
              description = Option(body.getDescription),
              typeInfo = TypeResolver.resolveBase(schema),
              required = Option(body.getRequired).contains(java.lang.Boolean.TRUE),
              contentType = contentType,
              formFields = formFields
            )
          )
        }
      }
    }
  }

  private def extractFormFields(schema: io.swagger.v3.oas.models.media.Schema[_]): List[FormField] = {
    val properties = Option(schema.getProperties).map(_.asScala.toMap).getOrElse(Map.empty)
    val requiredFields = Option(schema.getRequired).map(_.asScala.toSet).getOrElse(Set.empty[String])

    properties
      .map { case (name, propSchema) =>
        val isBinary = Option(propSchema.getType).contains("string") &&
          Option(propSchema.getFormat).contains("binary")

        FormField(
          name = name,
          description = Option(propSchema.getDescription),
          typeInfo = TypeResolver.resolveBase(propSchema),
          required = requiredFields.contains(name),
          isBinary = isBinary
        )
      }
      .toList
      .sortBy(_.name)
  }

  private def extractResponses(operation: Operation): List[ApiResponse] = {
    Option(operation.getResponses)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty[String, io.swagger.v3.oas.models.responses.ApiResponse])
      .map { case (statusCode, response) =>
        val status = parseResponseStatus(statusCode)
        val content = Option(response.getContent).map(_.asScala.toMap).getOrElse(Map.empty[String, io.swagger.v3.oas.models.media.MediaType])

        // Prefer JSON content type
        val jsonEntry = content.find(_._1.contains("json"))
        val (contentType, typeInfo) = jsonEntry match {
          case Some((ct, mt)) =>
            val schema = mt.getSchema
            (Some(ct), Option(schema).map(TypeResolver.resolveBase))
          case None =>
            (content.headOption.map(_._1), None)
        }

        // Extract response headers
        val headers = extractResponseHeaders(response)

        ApiResponse(
          statusCode = status,
          description = Option(response.getDescription),
          typeInfo = typeInfo,
          contentType = contentType,
          headers = headers
        )
      }
      .toList
      .sortBy(r => responseStatusOrder(r.statusCode))
  }

  private def extractResponseHeaders(response: io.swagger.v3.oas.models.responses.ApiResponse): List[ResponseHeader] = {
    Option(response.getHeaders)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)
      .map { case (name, header) =>
        val schema = header.getSchema
        val typeInfo = if (schema != null) {
          TypeResolver.resolve(schema, Option(header.getRequired).contains(java.lang.Boolean.TRUE))
        } else {
          TypeInfo.Primitive(PrimitiveType.String)
        }

        ResponseHeader(
          name = name,
          description = Option(header.getDescription),
          typeInfo = typeInfo,
          required = Option(header.getRequired).contains(java.lang.Boolean.TRUE)
        )
      }
      .toList
      .sortBy(_.name)
  }

  /** Extract response variants when there are ≥2 response types with content */
  private def extractResponseVariants(operation: Operation): Option[List[ResponseVariant]] = {
    val responses = Option(operation.getResponses).map(_.asScala.toMap).getOrElse(Map.empty)

    val variants = responses
      .flatMap { case (statusCode, response) =>
        val content = Option(response.getContent).map(_.asScala.toMap).getOrElse(Map.empty)

        // Prefer JSON content type
        val jsonEntry = content.find(_._1.contains("json"))
        jsonEntry.flatMap { case (_, mediaType) =>
          Option(mediaType.getSchema).map { schema =>
            ResponseVariant(
              statusCode = statusCode,
              typeInfo = TypeResolver.resolveBase(schema),
              description = Option(response.getDescription),
              headers = extractResponseHeaders(response)
            )
          }
        }
      }
      .toList
      .sortBy(v => responseStatusCodeOrder(v.statusCode))

    // Only generate sum type if there are ≥2 variants
    if (variants.size >= 2) Some(variants) else None
  }

  private def responseStatusCodeOrder(statusCode: String): Int = {
    scala.util.Try(statusCode.toInt).toOption match {
      case Some(c) => c
      case None =>
        statusCode.toLowerCase match {
          case "default" => 1000
          case "2xx"     => 200
          case "4xx"     => 400
          case "5xx"     => 500
          case _         => 999
        }
    }
  }

  private def parseResponseStatus(code: String): ResponseStatus = {
    code.toLowerCase match {
      case "default" => ResponseStatus.Default
      case "2xx"     => ResponseStatus.Success2XX
      case "4xx"     => ResponseStatus.ClientError4XX
      case "5xx"     => ResponseStatus.ServerError5XX
      case s =>
        scala.util.Try(s.toInt).toOption match {
          case Some(c) => ResponseStatus.Specific(c)
          case None    => ResponseStatus.Default
        }
    }
  }

  private def responseStatusOrder(status: ResponseStatus): Int = status match {
    case ResponseStatus.Specific(c)    => c
    case ResponseStatus.Success2XX     => 200
    case ResponseStatus.ClientError4XX => 400
    case ResponseStatus.ServerError5XX => 500
    case ResponseStatus.Default        => 1000
  }

  /** Extract callbacks from an operation */
  private def extractCallbacks(operation: Operation): List[Callback] = {
    val callbacks = Option(operation.getCallbacks).map(_.asScala.toMap).getOrElse(Map.empty)

    callbacks
      .flatMap { case (callbackName, callback) =>
        // A Callback extends LinkedHashMap<String, PathItem> where keys are runtime expressions
        callback.asScala.map { case (expression, pathItem) =>
          val methods = extractPathMethods(expression, pathItem)
          Callback(
            name = sanitizeClassName(callbackName),
            expression = expression,
            methods = methods
          )
        }
      }
      .toList
      .sortBy(_.name)
  }

  private def extractSecurity(operation: Operation): List[SecurityRequirement] = {
    Option(operation.getSecurity)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .flatMap { secReq =>
        secReq.asScala.map { case (name, scopes) =>
          SecurityRequirement(name, Option(scopes).map(_.asScala.toList).getOrElse(Nil))
        }
      }
  }

  private def generateOperationId(method: HttpMethod, path: String): String = {
    val methodName = method match {
      case HttpMethod.Get     => "get"
      case HttpMethod.Post    => "create"
      case HttpMethod.Put     => "update"
      case HttpMethod.Delete  => "delete"
      case HttpMethod.Patch   => "patch"
      case HttpMethod.Head    => "head"
      case HttpMethod.Options => "options"
    }

    val pathParts = path
      .split("/")
      .filter(_.nonEmpty)
      .filterNot(_.startsWith("{"))
      .map(_.capitalize)
      .mkString

    methodName + pathParts
  }

  private def sanitizeClassName(name: String): String = {
    name
      .split("[^a-zA-Z0-9]")
      .filter(_.nonEmpty)
      .map(_.capitalize)
      .mkString
  }

  private def sanitizeMethodName(name: String): String = {
    val sanitized = name.replace("-", "_").replace(".", "_")
    if (sanitized.headOption.exists(_.isDigit)) s"_$sanitized"
    else if (sanitized.headOption.exists(_.isUpper)) s"${sanitized.head.toLower}${sanitized.tail}"
    else sanitized
  }

  private def sanitizeParameterName(name: String): String = {
    val sanitized = name.replace("-", "_").replace(".", "_")
    if (sanitized.headOption.exists(_.isDigit)) s"_$sanitized" else sanitized
  }
}
