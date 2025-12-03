package typo.openapi.codegen

import typo.jvm
import typo.internal.codegen._
import typo.openapi.{ApiMethod, ApiParameter, FormField, HttpMethod, ParameterIn, RequestBody, SecurityRequirement, SecurityScheme}

/** Framework-specific annotation generation for API interfaces */
trait FrameworkSupport {

  /** Annotations to add to the API interface itself */
  def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation]

  /** Annotations to add to a method (HTTP method, path, produces/consumes) */
  def methodAnnotations(method: ApiMethod): List[jvm.Annotation]

  /** Security annotations for a method based on its security requirements */
  def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation]

  /** Annotations to add to a parameter (@PathParam, @QueryParam, etc) */
  def parameterAnnotations(param: ApiParameter): List[jvm.Annotation]

  /** Annotations to add to the request body parameter */
  def bodyAnnotations(body: RequestBody): List[jvm.Annotation]

  /** Check if request body is multipart (file upload) */
  def isMultipart(body: RequestBody): Boolean = body.contentType == "multipart/form-data"

  /** Type to use for file upload parameters */
  def fileUploadType: jvm.Type

  /** Annotations to add to form field parameters (for multipart requests) */
  def formFieldAnnotations(field: FormField): List[jvm.Annotation]

  /** The response type to use for endpoint wrapper methods (e.g., jakarta.ws.rs.core.Response, ResponseEntity) */
  def responseType: jvm.Type

  /** Build a success response with status code 200 */
  def buildOkResponse(value: jvm.Code): jvm.Code

  /** Build a response with a specific status code */
  def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code

  // Client-side response handling methods

  /** Get the status code from a response object */
  def getStatusCode(response: jvm.Code): jvm.Code

  /** Read entity from response with the given type */
  def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code

  /** Get a response header as a string (returns nullable/Optional) */
  def getHeaderString(response: jvm.Code, headerName: String): jvm.Code

  /** The exception type thrown when HTTP request fails (e.g., WebApplicationException) */
  def clientExceptionType: jvm.Type.Qualified

  /** Extract response from the exception */
  def getResponseFromException(exception: jvm.Code): jvm.Code

  /** Whether readEntity returns an effect type (e.g., IO[T]) rather than T directly. When true, client wrapper generation uses flatMap and effect chaining instead of synchronous value extraction.
    */
  def isAsyncEntityRead: Boolean = false

  /** Raise an error in the effect type (e.g., IO.raiseError). Only used when isAsyncEntityRead is true.
    */
  def raiseError(exception: jvm.Code): jvm.Code = code"throw $exception"

  /** Whether this framework supports generating toResponse methods on response leaf classes. When true, the framework generates toResponse methods that can convert response case classes to the
    * framework's Response type without needing asInstanceOf casts.
    */
  def supportsToResponseMethod: Boolean = false

  /** Whether this framework should generate HTTP routes in the server trait. This is used for DSL-based frameworks like Http4s that don't use annotations.
    */
  def supportsRouteGeneration: Boolean = false

  /** Generate the toResponse method body for a generic response type (e.g., Ok[T]). Takes the value expression and generates the response creation code with entity encoding.
    * @param valueExpr
    *   Expression to access the value field
    * @param encoderExpr
    *   Expression for the implicit EntityEncoder
    * @param statusCode
    *   The HTTP status code (e.g., 200, 404)
    * @return
    *   Code that creates the response
    */
  def toResponseBody(valueExpr: jvm.Code, @annotation.nowarn encoderExpr: jvm.Code, statusCode: Int): jvm.Code = {
    if (statusCode == 200) buildOkResponse(valueExpr)
    else buildStatusResponse(code"$statusCode", valueExpr)
  }

  /** Generate the toResponse method body for a range response type (e.g., ServerError5XX). Takes the statusCode field and value expression.
    * @param statusCodeExpr
    *   Expression for the statusCode field
    * @param valueExpr
    *   Expression for the value field
    * @param encoderExpr
    *   Expression for the implicit EntityEncoder
    * @return
    *   Code that creates the response
    */
  def toResponseBodyRange(statusCodeExpr: jvm.Code, valueExpr: jvm.Code, @annotation.nowarn encoderExpr: jvm.Code): jvm.Code = {
    buildStatusResponse(statusCodeExpr, valueExpr)
  }
}

/** No framework annotations - just generate plain interfaces */
object NoFrameworkSupport extends FrameworkSupport {
  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = Nil
  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] = Nil
  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] = Nil
  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] = Nil
  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] = Nil
  override def fileUploadType: jvm.Type = Types.InputStream
  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] = Nil
  override def responseType: jvm.Type.Qualified = Types.JaxRs.Response // Default to JAX-RS Response
  override def buildOkResponse(value: jvm.Code): jvm.Code = code"${Types.JaxRs.Response}.ok($value).build()"
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = code"${Types.JaxRs.Response}.status($statusCode).entity($value).build()"
  override def getStatusCode(response: jvm.Code): jvm.Code = code"$response.getStatus()"
  // For generic types (e.g., List<Animal>), use GenericType to preserve type params at runtime
  // For simple types, use JavaClassOf for cross-language .class syntax
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = JaxRsSupport.readEntity(response, entityType)
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code = JaxRsSupport.getHeaderString(response, headerName)
  override def clientExceptionType: jvm.Type.Qualified = Types.JaxRs.WebApplicationException
  override def getResponseFromException(exception: jvm.Code): jvm.Code = code"$exception.getResponse()"
}

/** JAX-RS (Jakarta EE) framework support */
object JaxRsSupport extends FrameworkSupport {

  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = {
    val pathAnnotation = basePath.map { path =>
      jvm.Annotation(
        Types.JaxRs.Path,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(path).code))
      )
    }.toList

    // Generate @SecurityScheme annotations for each scheme defined
    val schemeAnnotations = securitySchemes.toList.flatMap { case (name, scheme) =>
      generateSecuritySchemeAnnotation(name, scheme)
    }

    pathAnnotation ++ schemeAnnotations
  }

  def generateSecuritySchemeAnnotation(name: String, scheme: SecurityScheme): Option[jvm.Annotation] = {
    scheme match {
      case SecurityScheme.Http(httpScheme, bearerFormat) =>
        val args = List.newBuilder[jvm.Annotation.Arg]
        args += jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(name).code)
        args += jvm.Annotation.Arg.Named(jvm.Ident("type"), code"${Types.OpenApiAnnotations.SecuritySchemeType}.HTTP")
        args += jvm.Annotation.Arg.Named(jvm.Ident("scheme"), jvm.StrLit(httpScheme).code)
        bearerFormat.foreach { format =>
          args += jvm.Annotation.Arg.Named(jvm.Ident("bearerFormat"), jvm.StrLit(format).code)
        }
        Some(jvm.Annotation(Types.OpenApiAnnotations.SecurityScheme, args.result()))

      case SecurityScheme.ApiKey(keyName, in) =>
        val inValue = in match {
          case ParameterIn.Header => "HEADER"
          case ParameterIn.Query  => "QUERY"
          case ParameterIn.Cookie => "COOKIE"
          case _                  => "HEADER"
        }
        Some(
          jvm.Annotation(
            Types.OpenApiAnnotations.SecurityScheme,
            List(
              jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(name).code),
              jvm.Annotation.Arg.Named(jvm.Ident("type"), code"${Types.OpenApiAnnotations.SecuritySchemeType}.APIKEY"),
              jvm.Annotation.Arg.Named(jvm.Ident("in"), code"${Types.OpenApiAnnotations.SecuritySchemeIn}.$inValue"),
              jvm.Annotation.Arg.Named(jvm.Ident("paramName"), jvm.StrLit(keyName).code)
            )
          )
        )

      case SecurityScheme.OAuth2(_) =>
        // OAuth2 schemes are complex - generate basic annotation
        Some(
          jvm.Annotation(
            Types.OpenApiAnnotations.SecurityScheme,
            List(
              jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(name).code),
              jvm.Annotation.Arg.Named(jvm.Ident("type"), code"${Types.OpenApiAnnotations.SecuritySchemeType}.OAUTH2")
            )
          )
        )

      case SecurityScheme.OpenIdConnect(url) =>
        Some(
          jvm.Annotation(
            Types.OpenApiAnnotations.SecurityScheme,
            List(
              jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(name).code),
              jvm.Annotation.Arg.Named(jvm.Ident("type"), code"${Types.OpenApiAnnotations.SecuritySchemeType}.OPENIDCONNECT"),
              jvm.Annotation.Arg.Named(jvm.Ident("openIdConnectUrl"), jvm.StrLit(url).code)
            )
          )
        )
    }
  }

  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] = {
    if (security.isEmpty) Nil
    else {
      security.map { req =>
        val args = List.newBuilder[jvm.Annotation.Arg]
        args += jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(req.schemeName).code)
        if (req.scopes.nonEmpty) {
          // Use AnnotationArray for proper rendering in both Java ({}) and Kotlin ([])
          val scopesArray = jvm.AnnotationArray(req.scopes.map(s => jvm.StrLit(s).code))
          args += jvm.Annotation.Arg.Named(jvm.Ident("scopes"), scopesArray.code)
        }
        jvm.Annotation(Types.OpenApiAnnotations.SecurityRequirement, args.result())
      }
    }
  }

  override def fileUploadType: jvm.Type = Types.InputStream

  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] = {
    // JAX-RS uses @FormDataParam from Jersey multipart
    List(
      jvm.Annotation(
        Types.JaxRsMultipart.FormDataParam,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(field.name).code))
      )
    )
  }

  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] = {
    val httpMethodAnnotation = httpMethodToAnnotation(method.httpMethod)

    val pathAnnotation = jvm.Annotation(
      Types.JaxRs.Path,
      List(jvm.Annotation.Arg.Positional(jvm.StrLit(method.path).code))
    )

    // Determine content types from request/response
    val consumesAnnotation = method.requestBody.map { body =>
      jvm.Annotation(
        Types.JaxRs.Consumes,
        List(jvm.Annotation.Arg.Positional(mediaTypeConstant(body.contentType)))
      )
    }

    val producesAnnotation = method.responses.headOption.flatMap(_.contentType).map { contentType =>
      jvm.Annotation(
        Types.JaxRs.Produces,
        List(jvm.Annotation.Arg.Positional(mediaTypeConstant(contentType)))
      )
    }

    List(httpMethodAnnotation, pathAnnotation) ++ consumesAnnotation.toList ++ producesAnnotation.toList
  }

  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] = {
    val paramAnnotation = param.in match {
      case ParameterIn.Path =>
        jvm.Annotation(
          Types.JaxRs.PathParam,
          List(jvm.Annotation.Arg.Positional(jvm.StrLit(param.originalName).code))
        )
      case ParameterIn.Query =>
        jvm.Annotation(
          Types.JaxRs.QueryParam,
          List(jvm.Annotation.Arg.Positional(jvm.StrLit(param.originalName).code))
        )
      case ParameterIn.Header =>
        jvm.Annotation(
          Types.JaxRs.HeaderParam,
          List(jvm.Annotation.Arg.Positional(jvm.StrLit(param.originalName).code))
        )
      case ParameterIn.Cookie =>
        jvm.Annotation(
          Types.JaxRs.CookieParam,
          List(jvm.Annotation.Arg.Positional(jvm.StrLit(param.originalName).code))
        )
    }

    val defaultValueAnnotation = param.defaultValue.map { value =>
      jvm.Annotation(
        Types.JaxRs.DefaultValue,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(value).code))
      )
    }

    List(paramAnnotation) ++ defaultValueAnnotation.toList
  }

  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] = {
    // JAX-RS doesn't require special annotations for the body parameter
    // The unannotated parameter is treated as the request body
    Nil
  }

  private def httpMethodToAnnotation(method: HttpMethod): jvm.Annotation = {
    val tpe = method match {
      case HttpMethod.Get     => Types.JaxRs.GET
      case HttpMethod.Post    => Types.JaxRs.POST
      case HttpMethod.Put     => Types.JaxRs.PUT
      case HttpMethod.Delete  => Types.JaxRs.DELETE
      case HttpMethod.Patch   => Types.JaxRs.PATCH
      case HttpMethod.Head    => Types.JaxRs.HEAD
      case HttpMethod.Options => Types.JaxRs.OPTIONS
    }
    jvm.Annotation(tpe, Nil)
  }

  private def mediaTypeConstant(contentType: String): jvm.Code = {
    contentType match {
      case "application/json"                  => code"${Types.JaxRs.MediaType}.APPLICATION_JSON"
      case "application/xml"                   => code"${Types.JaxRs.MediaType}.APPLICATION_XML"
      case "text/plain"                        => code"${Types.JaxRs.MediaType}.TEXT_PLAIN"
      case "application/octet-stream"          => code"${Types.JaxRs.MediaType}.APPLICATION_OCTET_STREAM"
      case "application/x-www-form-urlencoded" => code"${Types.JaxRs.MediaType}.APPLICATION_FORM_URLENCODED"
      case "multipart/form-data"               => code"${Types.JaxRs.MediaType}.MULTIPART_FORM_DATA"
      case other                               => jvm.StrLit(other).code
    }
  }

  override def responseType: jvm.Type.Qualified = Types.JaxRs.Response
  override def buildOkResponse(value: jvm.Code): jvm.Code = code"${Types.JaxRs.Response}.ok($value).build()"
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = code"${Types.JaxRs.Response}.status($statusCode).entity($value).build()"
  override def getStatusCode(response: jvm.Code): jvm.Code = code"$response.getStatus()"
  // For generic types (e.g., List<Animal>), use GenericType to preserve type params at runtime
  // For simple types, use JavaClassOf for cross-language .class syntax
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = entityType match {
    case jvm.Type.TApply(_, _) =>
      // Generic type: use GenericType anonymous class to preserve type parameters
      // Java: new GenericType<List<Animal>>() {}
      // Kotlin: object : GenericType<List<Animal>>() {}
      val genericTypeTpe = jvm.Type.TApply(Types.JaxRs.GenericType, List(entityType))
      val genericTypeExpr = jvm.NewWithBody(extendsClass = Some(genericTypeTpe), implementsInterface = None, Nil).code
      code"$response.readEntity($genericTypeExpr)"
    case _ =>
      val classLiteral = jvm.JavaClassOf(entityType).code
      code"$response.readEntity($classLiteral)"
  }
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code =
    code"$response.getHeaderString(${jvm.StrLit(headerName).code})"
  override def clientExceptionType: jvm.Type.Qualified = Types.JaxRs.WebApplicationException
  override def getResponseFromException(exception: jvm.Code): jvm.Code = code"$exception.getResponse()"
}

/** Spring Boot / Spring MVC framework support */
object SpringBootSupport extends FrameworkSupport {

  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = {
    val restController = jvm.Annotation(Types.Spring.RestController, Nil)

    val requestMapping = basePath.map { path =>
      jvm.Annotation(
        Types.Spring.RequestMapping,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(path).code))
      )
    }

    // Generate @SecurityScheme annotations (same as JAX-RS, using OpenAPI annotations)
    val schemeAnnotations = securitySchemes.toList.flatMap { case (name, scheme) =>
      JaxRsSupport.generateSecuritySchemeAnnotation(name, scheme)
    }

    List(restController) ++ requestMapping.toList ++ schemeAnnotations
  }

  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] = {
    val mappingAnnotation = httpMethodToMapping(method.httpMethod, method.path, method.requestBody, method.responses.headOption.flatMap(_.contentType))

    List(mappingAnnotation)
  }

  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] = {
    // Use the same OpenAPI annotations as JAX-RS
    JaxRsSupport.securityAnnotations(security)
  }

  override def fileUploadType: jvm.Type = Types.Spring.MultipartFile

  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] = {
    // Spring uses @RequestPart for multipart form fields
    val args = List.newBuilder[jvm.Annotation.Arg]
    args += jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(field.name).code)
    if (!field.required) {
      args += jvm.Annotation.Arg.Named(jvm.Ident("required"), code"false")
    }
    List(jvm.Annotation(Types.Spring.RequestPart, args.result()))
  }

  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] = {
    val paramAnnotation = param.in match {
      case ParameterIn.Path =>
        val args = List(jvm.Annotation.Arg.Positional(jvm.StrLit(param.originalName).code)) ++
          (if (!param.required) List(jvm.Annotation.Arg.Named(jvm.Ident("required"), code"false")) else Nil)
        jvm.Annotation(Types.Spring.PathVariable, args)

      case ParameterIn.Query =>
        val args = List.newBuilder[jvm.Annotation.Arg]
        args += jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(param.originalName).code)
        if (!param.required) {
          args += jvm.Annotation.Arg.Named(jvm.Ident("required"), code"false")
        }
        param.defaultValue.foreach { value =>
          args += jvm.Annotation.Arg.Named(jvm.Ident("defaultValue"), jvm.StrLit(value).code)
        }
        jvm.Annotation(Types.Spring.RequestParam, args.result())

      case ParameterIn.Header =>
        val args = List(jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(param.originalName).code)) ++
          (if (!param.required) List(jvm.Annotation.Arg.Named(jvm.Ident("required"), code"false")) else Nil)
        jvm.Annotation(Types.Spring.RequestHeader, args)

      case ParameterIn.Cookie =>
        val args = List(jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(param.originalName).code)) ++
          (if (!param.required) List(jvm.Annotation.Arg.Named(jvm.Ident("required"), code"false")) else Nil)
        jvm.Annotation(Types.Spring.CookieValue, args)
    }

    List(paramAnnotation)
  }

  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] = {
    // Spring requires @RequestBody annotation
    List(jvm.Annotation(Types.Spring.RequestBody, Nil))
  }

  private def httpMethodToMapping(
      method: HttpMethod,
      path: String,
      requestBody: Option[RequestBody],
      responseContentType: Option[String]
  ): jvm.Annotation = {
    val mappingType = method match {
      case HttpMethod.Get     => Types.Spring.GetMapping
      case HttpMethod.Post    => Types.Spring.PostMapping
      case HttpMethod.Put     => Types.Spring.PutMapping
      case HttpMethod.Delete  => Types.Spring.DeleteMapping
      case HttpMethod.Patch   => Types.Spring.PatchMapping
      case HttpMethod.Head    => Types.Spring.GetMapping // Spring doesn't have HeadMapping
      case HttpMethod.Options => Types.Spring.RequestMapping // Use RequestMapping with method
    }

    val args = List.newBuilder[jvm.Annotation.Arg]

    // Path (using "value" for the path) - Spring expects an array type
    val valueArray = jvm.AnnotationArray(List(jvm.StrLit(path).code))
    args += jvm.Annotation.Arg.Named(jvm.Ident("value"), valueArray.code)

    // Consumes - Spring expects an array type
    requestBody.foreach { body =>
      val consumesArray = jvm.AnnotationArray(List(springMediaTypeConstant(body.contentType)))
      args += jvm.Annotation.Arg.Named(jvm.Ident("consumes"), consumesArray.code)
    }

    // Produces - Spring expects an array type
    responseContentType.foreach { contentType =>
      val producesArray = jvm.AnnotationArray(List(springMediaTypeConstant(contentType)))
      args += jvm.Annotation.Arg.Named(jvm.Ident("produces"), producesArray.code)
    }

    jvm.Annotation(mappingType, args.result())
  }

  private def springMediaTypeConstant(contentType: String): jvm.Code = {
    contentType match {
      case "application/json"                  => code"${Types.Spring.MediaType}.APPLICATION_JSON_VALUE"
      case "application/xml"                   => code"${Types.Spring.MediaType}.APPLICATION_XML_VALUE"
      case "text/plain"                        => code"${Types.Spring.MediaType}.TEXT_PLAIN_VALUE"
      case "application/octet-stream"          => code"${Types.Spring.MediaType}.APPLICATION_OCTET_STREAM_VALUE"
      case "application/x-www-form-urlencoded" => code"${Types.Spring.MediaType}.APPLICATION_FORM_URLENCODED_VALUE"
      case "multipart/form-data"               => code"${Types.Spring.MediaType}.MULTIPART_FORM_DATA_VALUE"
      case other                               => jvm.StrLit(other).code
    }
  }

  override def responseType: jvm.Type = jvm.Type.TApply(Types.Spring.ResponseEntity, List(jvm.Type.Wildcard))
  override def buildOkResponse(value: jvm.Code): jvm.Code = code"${Types.Spring.ResponseEntity}.ok($value)"
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = code"${Types.Spring.ResponseEntity}.status($statusCode).body($value)"
  // For Spring, use HttpStatusCodeException (RestTemplate) - getStatusCode() returns HttpStatusCode
  override def getStatusCode(response: jvm.Code): jvm.Code = code"$response.getStatusCode().value()"
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = code"$response.getBody()"
  // For client exceptions, headers are in getResponseHeaders()
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code =
    code"$response.getResponseHeaders().getFirst(${jvm.StrLit(headerName).code})"
  override def clientExceptionType: jvm.Type.Qualified = Types.Spring.HttpStatusCodeException
  override def getResponseFromException(exception: jvm.Code): jvm.Code = code"$exception" // Exception itself has the response data
}

/** Quarkus with RESTEasy Reactive - uses JAX-RS annotations with Mutiny Uni return types. The effect type wrapping (Uni<T>) is handled by ApiCodegen via the effectType parameter.
  */
object QuarkusReactiveServerSupport extends FrameworkSupport {

  // Delegate to JAX-RS support since Quarkus uses the same annotations
  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] =
    JaxRsSupport.interfaceAnnotations(basePath, securitySchemes)

  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] =
    JaxRsSupport.methodAnnotations(method)

  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] =
    JaxRsSupport.securityAnnotations(security)

  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] =
    JaxRsSupport.parameterAnnotations(param)

  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] =
    JaxRsSupport.bodyAnnotations(body)

  override def fileUploadType: jvm.Type = Types.InputStream

  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] =
    JaxRsSupport.formFieldAnnotations(field)

  override def responseType: jvm.Type.Qualified = JaxRsSupport.responseType
  override def buildOkResponse(value: jvm.Code): jvm.Code = JaxRsSupport.buildOkResponse(value)
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = JaxRsSupport.buildStatusResponse(statusCode, value)
  override def getStatusCode(response: jvm.Code): jvm.Code = JaxRsSupport.getStatusCode(response)
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = JaxRsSupport.readEntity(response, entityType)
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code = JaxRsSupport.getHeaderString(response, headerName)
  override def clientExceptionType: jvm.Type.Qualified = JaxRsSupport.clientExceptionType
  override def getResponseFromException(exception: jvm.Code): jvm.Code = JaxRsSupport.getResponseFromException(exception)
}

/** MicroProfile Rest Client - generates client interface with JAX-RS annotations that can be used with the MicroProfile Rest Client to make HTTP calls.
  */
object MicroProfileRestClientSupport extends FrameworkSupport {

  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = {
    // @RegisterRestClient annotation
    val registerRestClient = jvm.Annotation(
      Types.MicroProfile.RegisterRestClient,
      Nil
    )

    // Add @Path if there's a base path
    val pathAnnotation = basePath.map { path =>
      jvm.Annotation(
        Types.JaxRs.Path,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(path).code))
      )
    }.toList

    List(registerRestClient) ++ pathAnnotation
  }

  // Use JAX-RS annotations for methods and parameters
  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] =
    JaxRsSupport.methodAnnotations(method)

  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] =
    JaxRsSupport.securityAnnotations(security)

  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] =
    JaxRsSupport.parameterAnnotations(param)

  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] =
    JaxRsSupport.bodyAnnotations(body)

  override def fileUploadType: jvm.Type = Types.InputStream

  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] =
    JaxRsSupport.formFieldAnnotations(field)

  override def responseType: jvm.Type.Qualified = JaxRsSupport.responseType
  override def buildOkResponse(value: jvm.Code): jvm.Code = JaxRsSupport.buildOkResponse(value)
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = JaxRsSupport.buildStatusResponse(statusCode, value)
  override def getStatusCode(response: jvm.Code): jvm.Code = JaxRsSupport.getStatusCode(response)
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = JaxRsSupport.readEntity(response, entityType)
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code = JaxRsSupport.getHeaderString(response, headerName)
  override def clientExceptionType: jvm.Type.Qualified = JaxRsSupport.clientExceptionType
  override def getResponseFromException(exception: jvm.Code): jvm.Code = JaxRsSupport.getResponseFromException(exception)
}

/** HTTP4s framework support for Scala - generates clean traits without annotations. HTTP4s uses a DSL-based approach rather than annotations, so this generates plain Scala traits that can be wired to
  * HTTP4s routes/client.
  */
object Http4sSupport extends FrameworkSupport {

  // HTTP4s doesn't use annotations - it uses a DSL approach
  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = Nil

  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] = Nil

  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] = Nil

  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] = Nil

  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] = Nil

  override def fileUploadType: jvm.Type = Types.InputStream

  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] = Nil

  // For HTTP4s, Response[F] is the response type (we use Response[IO] for simplicity)
  override def responseType: jvm.Type = Types.Http4s.Response // Response[IO]

  override def buildOkResponse(value: jvm.Code): jvm.Code =
    code"${Types.Http4s.ResponseCtor}.apply(${Types.Http4s.Status}.Ok).withEntity($value)"

  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code =
    code"${Types.Http4s.ResponseCtor}.apply(${Types.Http4s.Status}.fromInt($statusCode).getOrElse(${Types.Http4s.Status}.InternalServerError)).withEntity($value)"

  // Client-side: get status from Response
  override def getStatusCode(response: jvm.Code): jvm.Code = code"$response.status.code"

  // Client-side: read entity - this is async in http4s, returns IO[A]
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code =
    code"$response.as[$entityType]"

  // HTTP4s client throws UnexpectedStatus for non-2xx responses
  override def clientExceptionType: jvm.Type.Qualified = Types.Http4s.UnexpectedStatus

  // UnexpectedStatus contains the response
  override def getResponseFromException(exception: jvm.Code): jvm.Code = code"$exception.response"

  // HTTP4s headers are accessed via Headers API - returns Option[Header.Raw].map(_.value)
  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code =
    code"$response.headers.get(${Types.Http4s.CIString}(${jvm.StrLit(headerName).code})).map(_.head.value).orNull"

  // HTTP4s/Cats Effect: entity reading is async (returns IO[T])
  override def isAsyncEntityRead: Boolean = true

  // Cats Effect uses IO.raiseError for error handling
  override def raiseError(exception: jvm.Code): jvm.Code = code"${Types.Cats.IO}.raiseError($exception)"

  /** Whether this framework supports generating toResponse methods on response leaf classes. When true, the framework generates toResponse methods that can convert response case classes to the
    * framework's Response type without needing asInstanceOf casts.
    */
  override def supportsToResponseMethod: Boolean = true

  /** Http4s uses a DSL-based approach for routing, so we generate routes in the server trait */
  override def supportsRouteGeneration: Boolean = true

  /** Generate the toResponse method body for a generic response type (e.g., Ok[T]). Takes the value expression and generates the response creation code with entity encoding.
    * @param valueExpr
    *   Expression to access the value field
    * @param encoderExpr
    *   Expression for the implicit EntityEncoder (unused for Http4s - implicit resolution happens via withEntity)
    * @param statusCode
    *   The HTTP status code (e.g., 200, 404)
    * @return
    *   Code that creates an IO[Response[IO]]
    */
  override def toResponseBody(valueExpr: jvm.Code, encoderExpr: jvm.Code, statusCode: Int): jvm.Code = {
    val responseCode = if (statusCode == 200) {
      buildOkResponse(valueExpr)
    } else {
      buildStatusResponse(code"$statusCode", valueExpr)
    }
    code"${Types.Cats.IO}.pure($responseCode)"
  }

  /** Generate the toResponse method body for a range response type (e.g., ServerError5XX). Takes the statusCode field and value expression.
    * @param statusCodeExpr
    *   Expression for the statusCode field
    * @param valueExpr
    *   Expression for the value field
    * @param encoderExpr
    *   Expression for the implicit EntityEncoder (unused for Http4s - implicit resolution happens via withEntity)
    * @return
    *   Code that creates an IO[Response[IO]]
    */
  override def toResponseBodyRange(statusCodeExpr: jvm.Code, valueExpr: jvm.Code, encoderExpr: jvm.Code): jvm.Code = {
    val responseCode = buildStatusResponse(statusCodeExpr, valueExpr)
    code"${Types.Cats.IO}.pure($responseCode)"
  }
}
