package typr.openapi.codegen

import typr.internal.codegen.*
import typr.jvm.Code.TypeOps
import typr.openapi.*
import typr.{OptionalSupport, jvm}

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

  /** Exception types that client methods should declare in their throws clause. For sync HTTP clients (JDK), this typically includes IOException and JsonProcessingException. For async clients or
    * annotation-based clients, this is usually empty.
    */
  def clientMethodThrows: List[jvm.Type.Qualified] = Nil

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

  /** Whether this framework generates a concrete client class (vs a trait with abstract methods). DSL-based frameworks like Http4s generate a class that takes Client[IO] and Uri as constructor
    * parameters and implements all methods with real HTTP calls.
    */
  def generatesConcreteClient: Boolean = false

  /** Constructor parameters for concrete client class. Only used when generatesConcreteClient is true.
    */
  def clientConstructorParams: List[jvm.Param[jvm.Type]] = Nil

  /** Comment for the generated client class. Only used when generatesConcreteClient is true.
    */
  def clientClassComment(apiName: String): String = s"Client implementation for $apiName"

  /** Additional imports needed for the client class. Only used when generatesConcreteClient is true.
    */
  def clientAdditionalImports: List[String] = Nil

  /** Generate HTTP method constant code (e.g., HttpMethod.GET, "GET").
    */
  def httpMethodCode(method: HttpMethod): jvm.Code = method match {
    case HttpMethod.Get     => jvm.StrLit("GET").code
    case HttpMethod.Post    => jvm.StrLit("POST").code
    case HttpMethod.Put     => jvm.StrLit("PUT").code
    case HttpMethod.Delete  => jvm.StrLit("DELETE").code
    case HttpMethod.Patch   => jvm.StrLit("PATCH").code
    case HttpMethod.Head    => jvm.StrLit("HEAD").code
    case HttpMethod.Options => jvm.StrLit("OPTIONS").code
  }

  /** Build a client request for the given method, URI, and optional body.
    * @param httpMethodCode
    *   the HTTP method code
    * @param uriCode
    *   the URI code
    * @param bodyParamName
    *   optional body parameter name (for POST/PUT)
    * @return
    *   code that creates the request
    */
  def buildClientRequest(httpMethodCode: jvm.Code, uriCode: jvm.Code, bodyParamName: Option[String]): jvm.Code =
    throw new UnsupportedOperationException("buildClientRequest not implemented for this framework")

  /** Execute the client request and get response. Returns (responseDecl, responseIdent) code.
    * @param requestIdent
    *   identifier for the request variable
    * @return
    *   code that executes the request and gets response
    */
  def executeClientRequest(requestIdent: jvm.Ident): jvm.Code =
    throw new UnsupportedOperationException("executeClientRequest not implemented for this framework")

  /** Build URI path code with segments. Base implementation uses / operator.
    * @param baseUriIdent
    *   base URI identifier
    * @param segments
    *   list of (segment, isParameter) pairs
    * @return
    *   code that builds the URI
    */
  def buildUriPath(baseUriIdent: jvm.Ident, segments: List[(String, Boolean)]): jvm.Code = {
    var uriCode: jvm.Code = baseUriIdent.code
    for ((segment, isParam) <- segments) {
      if (isParam) {
        uriCode = code"$uriCode / ${jvm.Ident(segment).code}"
      } else {
        uriCode = code"$uriCode / ${jvm.StrLit(segment).code}"
      }
    }
    uriCode
  }

  /** Add query parameter to URI.
    * @param uriCode
    *   current URI code
    * @param paramName
    *   parameter name in URL
    * @param paramIdent
    *   parameter identifier in code
    * @param required
    *   whether the parameter is required
    * @return
    *   code with query parameter added
    */
  def addQueryParam(uriCode: jvm.Code, paramName: String, paramIdent: jvm.Ident, required: Boolean): jvm.Code =
    if (required) code"$uriCode.withQueryParam(${jvm.StrLit(paramName).code}, ${paramIdent.code})"
    else code"$uriCode.withOptionQueryParam(${jvm.StrLit(paramName).code}, ${paramIdent.code})"

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

  /** Static members to add to wrapper type companion objects (e.g., Http4s path extractors). Default implementation returns empty list.
    */
  def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = Nil
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
    // Use named "value" argument with AnnotationArray for cross-language compatibility
    // - Java: @Consumes(value = { MediaType.APPLICATION_JSON })
    // - Kotlin: @Consumes(value = [MediaType.APPLICATION_JSON])
    // - Scala: @Consumes(value = Array(MediaType.APPLICATION_JSON))
    val consumesAnnotation = method.requestBody.map { body =>
      jvm.Annotation(
        Types.JaxRs.Consumes,
        List(jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.AnnotationArray(List(mediaTypeConstant(body.contentType))).code))
      )
    }

    val producesAnnotation = method.responses.headOption.flatMap(_.contentType).map { contentType =>
      jvm.Annotation(
        Types.JaxRs.Produces,
        List(jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.AnnotationArray(List(mediaTypeConstant(contentType))).code))
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
        List(jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.AnnotationArray(List(jvm.StrLit(path).code)).code))
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

  /** Generate valueOf(String) static method for Scala wrapper types to support Spring @PathVariable conversion. Spring's DefaultConversionService looks for static valueOf(String) methods for type
    * conversion. Example: {{{def valueOf(s: String): PetId = new PetId(s)}}}
    */
  override def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = {
    // Only needed for Scala - Java records and Kotlin data classes have compatible constructors
    // Generate: def valueOf(s: String): T = new T(s)
    val strParam = jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("s"),
      tpe = Types.String,
      default = None
    )

    // Body: new T(s) - using jvm.New so it renders correctly for each language
    val constructorCall = jvm.New(tpe.code, List(jvm.Arg.Pos(jvm.Ident("s").code)))

    val valueOfMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Converts a String to this type for Spring @PathVariable binding")),
      tparams = Nil,
      name = jvm.Ident("valueOf"),
      params = List(strParam),
      implicitParams = Nil,
      tpe = tpe,
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Tree(constructorCall)),
      isOverride = false,
      isDefault = false
    )

    List(valueOfMethod)
  }
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

/** JDK HTTP Client support for Java - generates concrete client classes using java.net.http.HttpClient. This is a zero-dependency client that works standalone without any container.
  */
object JdkHttpClientSupport extends FrameworkSupport {

  // JDK HTTP Client doesn't use annotations - it's a programmatic API
  override def interfaceAnnotations(basePath: Option[String], securitySchemes: Map[String, SecurityScheme]): List[jvm.Annotation] = Nil
  override def methodAnnotations(method: ApiMethod): List[jvm.Annotation] = Nil
  override def securityAnnotations(security: List[SecurityRequirement]): List[jvm.Annotation] = Nil
  override def parameterAnnotations(param: ApiParameter): List[jvm.Annotation] = Nil
  override def bodyAnnotations(body: RequestBody): List[jvm.Annotation] = Nil
  override def fileUploadType: jvm.Type = Types.InputStream
  override def formFieldAnnotations(field: FormField): List[jvm.Annotation] = Nil

  // Response type for JDK HTTP Client
  override def responseType: jvm.Type = jvm.Type.TApply(Types.JdkHttp.HttpResponse, List(Types.String))

  override def buildOkResponse(value: jvm.Code): jvm.Code =
    throw new UnsupportedOperationException("JDK HTTP Client is client-only, no server response building")

  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code =
    throw new UnsupportedOperationException("JDK HTTP Client is client-only, no server response building")

  // Client-side: get status from HttpResponse
  override def getStatusCode(response: jvm.Code): jvm.Code = code"$response.statusCode()"

  // Client-side: read entity - this is synchronous, body is already a String
  // The actual JSON parsing happens in the generated code using ObjectMapper
  override def readEntity(response: jvm.Code, entityType: jvm.Type): jvm.Code = entityType match {
    case jvm.Type.TApply(_, _) =>
      // Generic type: use TypeReference to preserve type parameters at runtime
      val typeRefTpe = jvm.Type.TApply(Types.JacksonMapper.TypeReference, List(entityType))
      val typeRefExpr = jvm.NewWithBody(extendsClass = Some(typeRefTpe), implementsInterface = None, Nil).code
      code"objectMapper.readValue($response.body(), $typeRefExpr)"
    case _ =>
      val classLiteral = jvm.JavaClassOf(entityType).code
      code"objectMapper.readValue($response.body(), $classLiteral)"
  }

  override def getHeaderString(response: jvm.Code, headerName: String): jvm.Code =
    code"$response.headers().firstValue(${jvm.StrLit(headerName).code}).orElse(null)"

  // JDK HTTP Client throws IOException for network errors
  override def clientExceptionType: jvm.Type.Qualified = jvm.Type.Qualified("java.io.IOException")
  override def getResponseFromException(exception: jvm.Code): jvm.Code =
    throw new UnsupportedOperationException("JDK HTTP Client exceptions don't contain response")

  // JDK HTTP Client is synchronous - entity reading returns T directly
  override def isAsyncEntityRead: Boolean = false

  // JDK HTTP Client generates concrete client classes
  override def generatesConcreteClient: Boolean = true

  // Constructor parameters: HttpClient, URI (baseUri), ObjectMapper
  override def clientConstructorParams: List[jvm.Param[jvm.Type]] = List(
    jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments(List("JDK HTTP client for making HTTP requests")),
      name = jvm.Ident("httpClient"),
      tpe = Types.JdkHttp.HttpClient,
      default = None
    ),
    jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments(List("Base URI for API requests")),
      name = jvm.Ident("baseUri"),
      tpe = Types.URI,
      default = None
    ),
    jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments(List("Jackson ObjectMapper for JSON serialization")),
      name = jvm.Ident("objectMapper"),
      tpe = Types.JacksonMapper.ObjectMapper,
      default = None
    )
  )

  override def clientClassComment(apiName: String): String = s"JDK HTTP Client implementation for $apiName"

  override def clientAdditionalImports: List[String] = Nil // No additional imports needed

  // JDK HTTP Client throws checked exceptions: IOException from httpClient.send() and InterruptedException
  // Using Exception to cover both since JsonProcessingException extends IOException
  override def clientMethodThrows: List[jvm.Type.Qualified] = List(Types.Exception)

  override def httpMethodCode(method: HttpMethod): jvm.Code = jvm
    .StrLit(method match {
      case HttpMethod.Get     => "GET"
      case HttpMethod.Post    => "POST"
      case HttpMethod.Put     => "PUT"
      case HttpMethod.Delete  => "DELETE"
      case HttpMethod.Patch   => "PATCH"
      case HttpMethod.Head    => "HEAD"
      case HttpMethod.Options => "OPTIONS"
    })
    .code

  override def buildClientRequest(httpMethodCode: jvm.Code, uriCode: jvm.Code, bodyParamName: Option[String]): jvm.Code = {
    val bodyPublisher = bodyParamName match {
      case Some(paramName) =>
        code"${Types.JdkHttp.BodyPublishers}.ofString(objectMapper.writeValueAsString(${jvm.Ident(paramName).code}))"
      case None =>
        code"${Types.JdkHttp.BodyPublishers}.noBody()"
    }
    code"${Types.JdkHttp.HttpRequest}.newBuilder($uriCode).method($httpMethodCode, $bodyPublisher).header(${jvm.StrLit("Content-Type").code}, ${jvm.StrLit("application/json").code}).header(${jvm
        .StrLit("Accept")
        .code}, ${jvm.StrLit("application/json").code}).build()"
  }

  override def executeClientRequest(requestIdent: jvm.Ident): jvm.Code =
    code"httpClient.send(${requestIdent.code}, ${Types.JdkHttp.BodyHandlers}.ofString())"

  /** Execute the client request asynchronously (returns a lambda that yields CompletableFuture). Used with effectOps.fromCompletionStage() to wrap in the effect type.
    */
  def executeClientRequestAsyncSupplier(requestIdent: jvm.Ident): jvm.Code = {
    val asyncCall = code"httpClient.sendAsync(${requestIdent.code}, ${Types.JdkHttp.BodyHandlers}.ofString())"
    jvm.Lambda(jvm.Body.Expr(asyncCall)).code
  }

  override def buildUriPath(baseUriIdent: jvm.Ident, segments: List[(String, Boolean)]): jvm.Code = {
    // For JDK, we need to build the path string and use URI.resolve
    val pathParts = segments.map {
      case (segment, true)  => code"${jvm.Ident(segment).code}.toString()"
      case (segment, false) => jvm.StrLit(segment).code
    }
    if (pathParts.isEmpty) {
      baseUriIdent.code
    } else {
      // Build: baseUri.resolve("/" + seg1 + "/" + seg2 + ...)
      val pathExpr = pathParts.zipWithIndex
        .map { case (part, idx) =>
          if (idx == 0) code"${jvm.StrLit("/").code} + $part"
          else code"${jvm.StrLit("/").code} + $part"
        }
        .reduceLeft((a, b) => code"$a + $b")
      code"${baseUriIdent.code}.resolve($pathExpr)"
    }
  }

  /** Build the full URI including query parameters for JDK HTTP Client. Returns a URI - converts path + query string to URI using URI.create().
    */
  def buildFullClientUri(
      baseUriIdent: jvm.Ident,
      segments: List[(String, Boolean)],
      queryParams: List[(String, jvm.Ident, Boolean)],
      optionalSupport: OptionalSupport
  ): jvm.Code = {
    // Build path as string first
    val pathParts = segments.map {
      case (segment, true)  => code"${jvm.Ident(segment).code}.toString()"
      case (segment, false) => jvm.StrLit(segment).code
    }

    // Build full path string starting from base URI as string
    var uriStringCode: jvm.Code = code"${baseUriIdent.code}.toString()"
    for (part <- pathParts) {
      uriStringCode = code"$uriStringCode + ${jvm.StrLit("/").code} + $part"
    }

    // Add query parameters with proper ? and & separators
    queryParams.zipWithIndex.foreach { case ((paramName, paramIdent, required), idx) =>
      val separator = if (idx == 0) "?" else "&"
      if (required) {
        uriStringCode = code"$uriStringCode + ${jvm.StrLit(s"$separator$paramName=").code} + ${paramIdent.code}.toString()"
      } else {
        // For optional params: if (opt.isDefined) separator + paramName + "=" + opt.get().toString() else ""
        val folded = optionalSupport.fold(
          paramIdent.code,
          jvm.StrLit("").code,
          v => code"${jvm.StrLit(s"$separator$paramName=").code} + $v.toString()"
        )
        uriStringCode = code"$uriStringCode + $folded"
      }
    }

    // Convert final string to URI
    code"${Types.URI}.create($uriStringCode)"
  }
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

  /** Http4s generates a concrete client class that takes Client[IO] and Uri as constructor parameters */
  override def generatesConcreteClient: Boolean = true

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

  /** Generate Http4s path extractor and SegmentEncoder for wrapper types.
    *
    * For server-side route matching, creates an unapply method:
    * {{{
    * case GET -> Root / "pets" / PetId(petId) => getPet(petId)
    * }}}
    *
    * For client-side path building, creates a SegmentEncoder:
    * {{{
    * val uri = baseUri / "pets" / petId  // petId: PetId
    * }}}
    *
    * For a wrapper type PetId(value: String), generates:
    * {{{
    * def unapply(str: String): Option[PetId] = Some(PetId(str))
    * given segmentEncoder: SegmentEncoder[PetId] = SegmentEncoder[String].contramap(_.value)
    * }}}
    */
  override def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = {
    // Generate: def unapply(str: String): Option[T] = Some(T(str))
    // This allows the wrapper type to be used as a path extractor in Http4s routes
    val strParam = jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("str"),
      tpe = Types.String,
      default = None
    )

    val optionType = jvm.Type.TApply(ScalaTypes.Option, List(tpe))

    // Body: Some(T(str))
    val constructorCall = tpe.construct(jvm.Ident("str").code)
    val unapplyBody = code"${ScalaTypes.Some}($constructorCall)"

    val unapplyMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Path extractor for Http4s routes")),
      tparams = Nil,
      name = jvm.Ident("unapply"),
      params = List(strParam),
      implicitParams = Nil,
      tpe = optionType,
      throws = Nil,
      body = jvm.Body.Expr(unapplyBody),
      isOverride = false,
      isDefault = false
    )

    // Generate: given segmentEncoder: SegmentEncoder[T] = SegmentEncoder[Underlying].contramap(_.value)
    // This allows the wrapper type to be used in Uri path building with the / operator
    val segmentEncoderGiven = jvm.Given(
      tparams = Nil,
      name = jvm.Ident("segmentEncoder"),
      implicitParams = Nil,
      tpe = jvm.Type.TApply(Types.Http4s.SegmentEncoder, List(tpe)),
      body = code"${Types.Http4s.SegmentEncoder}[$underlyingType].contramap(_.value)"
    )

    List(unapplyMethod, segmentEncoderGiven)
  }

  // Constructor parameters: Client[IO] and Uri
  override def clientConstructorParams: List[jvm.Param[jvm.Type]] = List(
    jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments(List("Http4s client for making HTTP requests")),
      name = jvm.Ident("client"),
      tpe = jvm.Type.TApply(Types.Http4s.Client, List(Types.Cats.IO)),
      default = None
    ),
    jvm.Param[jvm.Type](
      annotations = Nil,
      comments = jvm.Comments(List("Base URI for API requests")),
      name = jvm.Ident("baseUri"),
      tpe = Types.Http4s.Uri,
      default = None
    )
  )

  override def clientClassComment(apiName: String): String = s"Http4s client implementation for $apiName"

  override def clientAdditionalImports: List[String] = List(
    "org.http4s.circe.CirceEntityEncoder.circeEntityEncoder",
    "org.http4s.circe.CirceEntityDecoder.circeEntityDecoder"
  )

  override def httpMethodCode(method: HttpMethod): jvm.Code = method match {
    case HttpMethod.Get     => code"${Types.Http4s.Method}.GET"
    case HttpMethod.Post    => code"${Types.Http4s.Method}.POST"
    case HttpMethod.Put     => code"${Types.Http4s.Method}.PUT"
    case HttpMethod.Delete  => code"${Types.Http4s.Method}.DELETE"
    case HttpMethod.Patch   => code"${Types.Http4s.Method}.PATCH"
    case HttpMethod.Head    => code"${Types.Http4s.Method}.HEAD"
    case HttpMethod.Options => code"${Types.Http4s.Method}.OPTIONS"
  }
}
