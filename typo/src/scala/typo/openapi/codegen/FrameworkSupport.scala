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
  def responseType: jvm.Type.Qualified

  /** Build a success response with status code 200 */
  def buildOkResponse(value: jvm.Code): jvm.Code

  /** Build a response with a specific status code */
  def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code
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
          val scopesCode = code"{ ${jvm.Code.Combined(req.scopes.map(s => jvm.StrLit(s).code).flatMap(c => List(c, code", ")).dropRight(1))} }"
          args += jvm.Annotation.Arg.Named(jvm.Ident("scopes"), scopesCode)
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

    // Path (using "value" for the path)
    args += jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.StrLit(path).code)

    // Consumes
    requestBody.foreach { body =>
      args += jvm.Annotation.Arg.Named(jvm.Ident("consumes"), springMediaTypeConstant(body.contentType))
    }

    // Produces
    responseContentType.foreach { contentType =>
      args += jvm.Annotation.Arg.Named(jvm.Ident("produces"), springMediaTypeConstant(contentType))
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

  override def responseType: jvm.Type.Qualified = Types.Spring.ResponseEntity
  override def buildOkResponse(value: jvm.Code): jvm.Code = code"${Types.Spring.ResponseEntity}.ok($value)"
  override def buildStatusResponse(statusCode: jvm.Code, value: jvm.Code): jvm.Code = code"${Types.Spring.ResponseEntity}.status($statusCode).body($value)"
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
}
