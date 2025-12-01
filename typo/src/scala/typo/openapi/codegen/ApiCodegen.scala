package typo.openapi.codegen

import typo.{jvm, Lang, Scope}
import typo.internal.codegen._
import typo.openapi._

/** Generates jvm.File for API interfaces with optional server and client traits */
class ApiCodegen(
    apiPkg: jvm.QIdent,
    typeMapper: TypeMapper,
    lang: Lang,
    jsonLib: JsonLibSupport,
    serverFrameworkSupport: Option[FrameworkSupport],
    clientFrameworkSupport: Option[FrameworkSupport],
    sumTypeNames: Set[String],
    securitySchemes: Map[String, SecurityScheme],
    effectTypeWithOps: Option[(jvm.Type.Qualified, EffectTypeOps)]
) {
  private val effectType: Option[jvm.Type.Qualified] = effectTypeWithOps.map(_._1)
  private val effectOps: Option[EffectTypeOps] = effectTypeWithOps.map(_._2)

  /** Generate API interface files: base trait, optional server trait, optional client trait, and response sum types */
  def generate(api: ApiInterface): List[jvm.File] = {
    val baseTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(api.name))
    val comments = api.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate response sum types for methods that have multiple response variants
    val responseSumTypeFiles = api.methods.flatMap { method =>
      method.responseVariants.map { variants =>
        generateResponseSumType(method.name, variants)
      }
    }

    // Find common base path for all methods in this API
    val basePath = findCommonBasePath(api.methods.map(_.path))

    // Generate base trait (no framework annotations on methods)
    val baseMethods = api.methods.map(m => generateBaseMethod(m))
    val baseInterface = jvm.Adt.Sum(
      annotations = Nil,
      comments = comments,
      name = baseTpe,
      tparams = Nil,
      members = baseMethods,
      implements = Nil,
      subtypes = Nil,
      staticMembers = Nil
    )
    val baseGeneratedCode = lang.renderTree(baseInterface, lang.Ctx.Empty)
    val baseFile = jvm.File(baseTpe, baseGeneratedCode, secondaryTypes = Nil, scope = Scope.Main)

    val files = List.newBuilder[jvm.File]
    files += baseFile

    // Generate server trait if serverFrameworkSupport is provided
    serverFrameworkSupport.foreach { serverSupport =>
      files += generateServerTrait(api, baseTpe, basePath, serverSupport)
    }

    // Generate client trait if clientFrameworkSupport is provided
    clientFrameworkSupport.foreach { clientSupport =>
      files += generateClientTrait(api, baseTpe, basePath, clientSupport)
    }

    files ++= responseSumTypeFiles
    files.result()
  }

  /** Generate server trait that extends the base trait */
  private def generateServerTrait(
      api: ApiInterface,
      baseTpe: jvm.Type.Qualified,
      basePath: Option[String],
      serverSupport: FrameworkSupport
  ): jvm.File = {
    val serverTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(api.name + "Server"))

    // Framework annotations on interface (e.g., @Path for JAX-RS, @SecurityScheme)
    val interfaceAnnotations = serverSupport.interfaceAnnotations(basePath, securitySchemes)

    // Generate methods: for methods with response variants, generate both abstract method
    // (without framework annotations) and a wrapper endpoint method that returns Response (with annotations)
    val methods = api.methods.flatMap { m =>
      m.responseVariants match {
        case Some(variants) =>
          // Abstract method without annotations (just the contract from base)
          val abstractMethod = generateBaseMethod(m)
          // Wrapper endpoint method with annotations that returns Response (or Effect<Response> for async)
          val wrapperMethod = generateServerEndpointWrapperMethod(m, basePath, serverSupport, variants)
          List(abstractMethod, wrapperMethod)
        case None =>
          // Normal method with annotations
          val annotatedMethod = generateServerMethod(m, basePath, serverSupport)
          List(annotatedMethod)
      }
    }

    val serverInterface = jvm.Adt.Sum(
      annotations = interfaceAnnotations,
      comments = jvm.Comments.Empty,
      name = serverTpe,
      tparams = Nil,
      members = methods,
      implements = List(baseTpe),
      subtypes = Nil,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(serverInterface, lang.Ctx.Empty)
    jvm.File(serverTpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate client trait that extends the base trait */
  private def generateClientTrait(
      api: ApiInterface,
      baseTpe: jvm.Type.Qualified,
      basePath: Option[String],
      clientSupport: FrameworkSupport
  ): jvm.File = {
    val clientTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(api.name + "Client"))

    // Framework annotations on interface (e.g., @RegisterRestClient, @Path)
    val interfaceAnnotations = clientSupport.interfaceAnnotations(basePath, securitySchemes)

    // Generate methods with framework annotations
    val methods = api.methods.map(m => generateClientMethod(m, basePath, clientSupport))

    val clientInterface = jvm.Adt.Sum(
      annotations = interfaceAnnotations,
      comments = jvm.Comments.Empty,
      name = clientTpe,
      tparams = Nil,
      members = methods,
      implements = List(baseTpe),
      subtypes = Nil,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(clientInterface, lang.Ctx.Empty)
    jvm.File(clientTpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Find the common base path prefix for a list of paths */
  private def findCommonBasePath(paths: List[String]): Option[String] = {
    if (paths.isEmpty) return None

    // Split paths into segments
    val segments = paths.map(_.split("/").filter(_.nonEmpty).toList)
    if (segments.exists(_.isEmpty)) return None

    // Find common prefix segments (but not path parameters like {id})
    val firstSegments = segments.head
    val commonSegments = firstSegments.takeWhile { seg =>
      !seg.startsWith("{") && segments.forall(_.headOption.contains(seg))
    }

    if (commonSegments.isEmpty) None
    else Some("/" + commonSegments.mkString("/"))
  }

  /** Calculate relative path from base path */
  private def relativePath(fullPath: String, basePath: Option[String]): String = {
    basePath match {
      case Some(base) if fullPath.startsWith(base) =>
        val relative = fullPath.stripPrefix(base)
        if (relative.isEmpty) "/" else relative
      case _ => fullPath
    }
  }

  /** Generate a response sum type for methods with multiple response variants */
  private def generateResponseSumType(methodName: String, variants: List[ResponseVariant]): jvm.File = {
    // Check for nested sum types
    variants.foreach { variant =>
      variant.typeInfo match {
        case TypeInfo.Ref(name) if sumTypeNames.contains(name) =>
          throw new IllegalArgumentException(
            s"Nested sum types are not supported: method '${methodName}' has response status '${variant.statusCode}' " +
              s"with type '$name' which is a sum type. Consider inlining the sum type or using a wrapper."
          )
        case _ => // OK
      }
    }

    val responseName = capitalize(methodName) + "Response"
    val tpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName))

    // Generate subtypes for each status code
    val subtypes = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      val subtypeTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName) / jvm.Ident(statusName))
      val valueType = typeMapper.map(variant.typeInfo)

      val valueParam = jvm.Param[jvm.Type](
        annotations = jsonLib.propertyAnnotations("value"),
        comments = jvm.Comments.Empty,
        name = jvm.Ident("value"),
        tpe = valueType,
        default = None
      )

      // For range status codes (4xx, 5xx, default), include a statusCode field so users can specify the actual status
      val isRangeStatus = variant.statusCode.toLowerCase match {
        case "4xx" | "5xx" | "default" | "2xx" => true
        case _                                 => false
      }

      val params = if (isRangeStatus) {
        val statusCodeParam = jvm.Param[jvm.Type](
          annotations = jsonLib.propertyAnnotations("statusCode"),
          comments = jvm.Comments(List("HTTP status code to return")),
          name = jvm.Ident("statusCode"),
          tpe = Types.Int,
          default = None
        )
        List(statusCodeParam, valueParam)
      } else {
        List(valueParam)
      }

      // Override status() method to return the status code string
      // isLazy=true makes LangJava render this as a method override instead of a field
      val statusOverride = jvm.Value(
        annotations = Nil,
        name = jvm.Ident("status"),
        tpe = Types.String,
        body = Some(jvm.StrLit(variant.statusCode).code),
        isLazy = true,
        isOverride = true
      )

      jvm.Adt.Record(
        annotations = Nil,
        isWrapper = false,
        comments = variant.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = subtypeTpe,
        tparams = Nil,
        params = params,
        implicitParams = Nil,
        `extends` = None,
        implements = List(tpe),
        members = List(statusOverride),
        staticMembers = Nil
      )
    }

    // Abstract status method in the sealed interface
    val statusMethod = jvm.Method(
      annotations = jsonLib.propertyAnnotations("status"),
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("status"),
      params = Nil,
      implicitParams = Nil,
      tpe = Types.String,
      throws = Nil,
      body = Nil
    )

    // Jackson annotations for polymorphic deserialization
    val jacksonAnnotations = generateResponseSumTypeAnnotations(tpe, variants)

    val sumAdt = jvm.Adt.Sum(
      annotations = jacksonAnnotations,
      comments = jvm.Comments.Empty,
      name = tpe,
      tparams = Nil,
      members = List(statusMethod),
      implements = Nil,
      subtypes = subtypes,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(sumAdt, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateResponseSumTypeAnnotations(tpe: jvm.Type.Qualified, variants: List[ResponseVariant]): List[jvm.Annotation] = {
    val typeInfoAnnotation = jvm.Annotation(
      Types.Jackson.JsonTypeInfo,
      List(
        jvm.Annotation.Arg.Named(jvm.Ident("use"), code"${Types.Jackson.JsonTypeInfo}.Id.NAME"),
        jvm.Annotation.Arg.Named(jvm.Ident("include"), code"${Types.Jackson.JsonTypeInfo}.As.PROPERTY"),
        jvm.Annotation.Arg.Named(jvm.Ident("property"), jvm.StrLit("status").code)
      )
    )

    val subTypesArgs = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      code"@${Types.Jackson.JsonSubTypes}.Type(value = $tpe.$statusName.class, name = ${jvm.StrLit(variant.statusCode)})"
    }

    val subTypesAnnotation = jvm.Annotation(
      Types.Jackson.JsonSubTypes,
      List(jvm.Annotation.Arg.Positional(code"{ ${jvm.Code.Combined(subTypesArgs.flatMap(c => List(c, code", ")).dropRight(1))} }"))
    )

    List(typeInfoAnnotation, subTypesAnnotation)
  }

  private def normalizeStatusCode(statusCode: String): String = {
    // Convert status codes like "2XX", "default" to valid identifiers
    statusCode.toLowerCase match {
      case "default" => "Default"
      case "2xx"     => "2XX"
      case "4xx"     => "4XX"
      case "5xx"     => "5XX"
      case s         => s
    }
  }

  private def capitalize(s: String): String =
    if (s.isEmpty) s else s.head.toUpper.toString + s.tail

  /** Generate base method (no framework annotations, just return type and params) */
  private def generateBaseMethod(method: ApiMethod): jvm.Method = {
    val comments = method.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate parameters without framework annotations
    val params = generateBaseParams(method)

    // Determine return type
    val returnType = inferReturnType(method)

    // Add deprecation annotation if needed
    val deprecationAnnotation = if (method.deprecated) {
      List(jvm.Annotation(jvm.Type.Qualified("java.lang.Deprecated"), Nil))
    } else {
      Nil
    }

    jvm.Method(
      annotations = deprecationAnnotation,
      comments = comments,
      tparams = Nil,
      name = jvm.Ident(method.name),
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = Nil // Interface method - no body
    )
  }

  /** Generate server method with framework annotations */
  private def generateServerMethod(method: ApiMethod, basePath: Option[String], serverSupport: FrameworkSupport): jvm.Method = {
    val comments = method.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate parameters with framework annotations
    val params = generateParams(method, serverSupport)

    // Determine return type
    val returnType = inferReturnType(method)

    // Create method with relative path for framework annotations
    val methodWithRelativePath = method.copy(path = relativePath(method.path, basePath))

    // Framework annotations (@GET, @POST, @Path, @Produces, @Consumes)
    val frameworkAnnotations = serverSupport.methodAnnotations(methodWithRelativePath)

    // Security annotations (@SecurityRequirement)
    val securityAnnotations = serverSupport.securityAnnotations(method.security)

    // Add deprecation annotation if needed
    val deprecationAnnotation = if (method.deprecated) {
      List(jvm.Annotation(jvm.Type.Qualified("java.lang.Deprecated"), Nil))
    } else {
      Nil
    }

    jvm.Method(
      annotations = frameworkAnnotations ++ securityAnnotations ++ deprecationAnnotation,
      comments = comments,
      tparams = Nil,
      name = jvm.Ident(method.name),
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = Nil // Interface method - no body
    )
  }

  /** Generate client method with framework annotations */
  private def generateClientMethod(method: ApiMethod, basePath: Option[String], clientSupport: FrameworkSupport): jvm.Method = {
    // Client methods have the same structure as server methods
    generateServerMethod(method, basePath, clientSupport)
  }

  /** Generate base parameters without framework annotations */
  private def generateBaseParams(method: ApiMethod): List[jvm.Param[jvm.Type]] = {
    // Add path, query, header parameters without framework annotations
    val methodParams = method.parameters.map { param =>
      val paramType = typeMapper.map(param.typeInfo)

      jvm.Param[jvm.Type](
        annotations = Nil,
        comments = param.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = jvm.Ident(param.name),
        tpe = paramType,
        default = None
      )
    }

    // Add request body as parameter if present
    val bodyParams = method.requestBody.toList.flatMap { body =>
      if (body.isMultipart && body.formFields.nonEmpty) {
        // For multipart forms, generate separate parameters for each form field
        body.formFields.map { field =>
          val fieldType = if (field.isBinary) {
            Types.InputStream
          } else {
            typeMapper.map(field.typeInfo)
          }

          jvm.Param[jvm.Type](
            annotations = Nil,
            comments = field.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
            name = jvm.Ident(field.name),
            tpe = fieldType,
            default = None
          )
        }
      } else {
        // Standard request body
        val bodyType = typeMapper.map(body.typeInfo)

        List(
          jvm.Param[jvm.Type](
            annotations = Nil,
            comments = body.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
            name = jvm.Ident("body"),
            tpe = bodyType,
            default = None
          )
        )
      }
    }

    methodParams ++ bodyParams
  }

  /** Generate parameters with framework annotations */
  private def generateParams(method: ApiMethod, frameworkSupport: FrameworkSupport): List[jvm.Param[jvm.Type]] = {
    // Add path, query, header parameters
    val methodParams = method.parameters.map { param =>
      val paramType = typeMapper.map(param.typeInfo)
      val annotations = generateParamAnnotations(param, frameworkSupport)

      jvm.Param[jvm.Type](
        annotations = annotations,
        comments = param.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = jvm.Ident(param.name),
        tpe = paramType,
        default = None
      )
    }

    // Add request body as parameter if present
    val bodyParams = method.requestBody.toList.flatMap { body =>
      if (body.isMultipart && body.formFields.nonEmpty) {
        // For multipart forms, generate separate parameters for each form field
        body.formFields.map { field =>
          val fieldType = if (field.isBinary) {
            frameworkSupport.fileUploadType
          } else {
            typeMapper.map(field.typeInfo)
          }
          val annotations = frameworkSupport.formFieldAnnotations(field)

          jvm.Param[jvm.Type](
            annotations = annotations,
            comments = field.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
            name = jvm.Ident(field.name),
            tpe = fieldType,
            default = None
          )
        }
      } else {
        // Standard request body
        val bodyType = typeMapper.map(body.typeInfo)
        val bodyAnnotations = generateBodyAnnotations(body, frameworkSupport)

        List(
          jvm.Param[jvm.Type](
            annotations = bodyAnnotations,
            comments = body.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
            name = jvm.Ident("body"),
            tpe = bodyType,
            default = None
          )
        )
      }
    }

    methodParams ++ bodyParams
  }

  private def generateParamAnnotations(param: ApiParameter, frameworkSupport: FrameworkSupport): List[jvm.Annotation] = {
    // Framework annotations (@PathParam, @QueryParam, etc.)
    val frameworkAnnotations = frameworkSupport.parameterAnnotations(param)

    // JSON property annotation for name mapping if different
    val jsonAnnotations = if (param.name != param.originalName) {
      jsonLib.propertyAnnotations(param.originalName)
    } else {
      Nil
    }

    frameworkAnnotations ++ jsonAnnotations
  }

  private def generateBodyAnnotations(body: RequestBody, frameworkSupport: FrameworkSupport): List[jvm.Annotation] = {
    frameworkSupport.bodyAnnotations(body)
  }

  private def inferReturnType(method: ApiMethod): jvm.Type = {
    // If there are response variants, return the response sum type
    val baseType: jvm.Type = method.responseVariants match {
      case Some(_) =>
        val responseName = capitalize(method.name) + "Response"
        jvm.Type.Qualified(apiPkg / jvm.Ident(responseName))

      case None =>
        // Find the success response (2xx or default)
        val successResponse = method.responses
          .find(r => isSuccessStatus(r.statusCode))
          .orElse(method.responses.find(_.statusCode == ResponseStatus.Default))

        successResponse.flatMap(_.typeInfo) match {
          case Some(typeInfo) => typeMapper.map(typeInfo)
          case None           => Types.Void
        }
    }

    // Wrap with effect type if provided (e.g., Uni<T>, Mono<T>)
    effectType match {
      case Some(effect) => jvm.Type.TApply(effect, List(baseType))
      case None         => baseType
    }
  }

  private def isSuccessStatus(status: ResponseStatus): Boolean = status match {
    case ResponseStatus.Specific(code) => code >= 200 && code < 300
    case ResponseStatus.Success2XX     => true
    case _                             => false
  }

  /** Generate a wrapper endpoint method that returns Response and maps the response sum type */
  private def generateServerEndpointWrapperMethod(
      method: ApiMethod,
      basePath: Option[String],
      serverSupport: FrameworkSupport,
      variants: List[ResponseVariant]
  ): jvm.Method = {
    val comments = jvm.Comments(List(s"Endpoint wrapper for ${method.name} - handles response status codes"))

    // Generate parameters with framework annotations (same as the abstract method)
    val params = generateParams(method, serverSupport)

    // Return type depends on whether we have effect types:
    // - Async (with effect type): Effect<Response> (e.g., Uni<Response>)
    // - Sync (no effect type): Response directly
    val responseType = serverSupport.responseType
    val returnType = effectOps match {
      case Some(ops) => jvm.Type.TApply(ops.tpe, List(responseType))
      case None      => responseType
    }

    // Create method with relative path for framework annotations
    val methodWithRelativePath = method.copy(path = relativePath(method.path, basePath))

    // Framework annotations (@GET, @POST, @Path, @Produces, @Consumes)
    val frameworkAnnotations = serverSupport.methodAnnotations(methodWithRelativePath)

    // Security annotations (@SecurityRequirement)
    val securityAnnotations = serverSupport.securityAnnotations(method.security)

    // Build the method body that calls the base method and maps the result
    val body = generateEndpointWrapperBody(method, variants, serverSupport)

    jvm.Method(
      annotations = frameworkAnnotations ++ securityAnnotations,
      comments = comments,
      tparams = Nil,
      name = jvm.Ident(method.name + "Endpoint"),
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = body
    )
  }

  /** Generate the body of the endpoint wrapper method */
  private def generateEndpointWrapperBody(
      method: ApiMethod,
      variants: List[ResponseVariant],
      serverSupport: FrameworkSupport
  ): List[jvm.Code] = {
    // Build argument list from method parameters
    val argNames = method.parameters.map(p => jvm.Ident(p.name).code) ++
      method.requestBody.toList.flatMap { body =>
        if (body.isMultipart && body.formFields.nonEmpty) {
          body.formFields.map(f => jvm.Ident(f.name).code)
        } else {
          List(jvm.Ident("body").code)
        }
      }

    val argsCode = argNames.mkCode(", ")
    val methodCall = code"${jvm.Ident(method.name)}($argsCode)"

    // Build the response sum type name
    val responseName = capitalize(method.name) + "Response"
    val responseTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName))

    val responseIdent = jvm.Ident("response")

    // Generate the type switch cases for each variant
    val switchCases = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      val subtypeTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName) / jvm.Ident(statusName))
      val defaultStatusCode = statusCodeToInt(variant.statusCode)
      val bindingIdent = jvm.Ident("r")

      // Check if this is a range status code (has statusCode field)
      val isRangeStatus = variant.statusCode.toLowerCase match {
        case "4xx" | "5xx" | "default" | "2xx" => true
        case _                                 => false
      }

      val body = if (isRangeStatus) {
        // Use the user-provided statusCode field
        serverSupport.buildStatusResponse(code"$bindingIdent.statusCode()", code"$bindingIdent.value()")
      } else if (defaultStatusCode >= 200 && defaultStatusCode < 300) {
        // Fixed success response
        serverSupport.buildOkResponse(code"$bindingIdent.value()")
      } else {
        // Fixed error response
        serverSupport.buildStatusResponse(code"$defaultStatusCode", code"$bindingIdent.value()")
      }

      jvm.TypeSwitch.Case(subtypeTpe, bindingIdent, body)
    }

    effectOps match {
      case Some(ops) =>
        // Async: wrap in lambda and use map operation
        val fallbackCase = Some(code"throw new IllegalStateException(${jvm.StrLit("Unexpected response type: ").code} + $responseIdent.getClass())")
        val typeSwitch = jvm.TypeSwitch(responseIdent.code, switchCases, nullCase = None, defaultCase = fallbackCase)
        val lambda = jvm.TypedLambda1(responseTpe, responseIdent, typeSwitch.code)
        val mappedCall = ops.map(methodCall, lambda.code)
        List(mappedCall)

      case None =>
        // Sync: directly switch on the result and return
        // For default case, use a generic error message since we don't have a named variable
        val fallbackCase = Some(code"throw new IllegalStateException(${jvm.StrLit("Unexpected response type")})")
        val switchOnResult = jvm.TypeSwitch(methodCall, switchCases, nullCase = None, defaultCase = fallbackCase)
        List(switchOnResult.code)
    }
  }

  /** Convert status code string to integer */
  private def statusCodeToInt(statusCode: String): Int = {
    statusCode.toLowerCase match {
      case "default" => 500
      case "2xx"     => 200
      case "4xx"     => 400
      case "5xx"     => 500
      case s         => scala.util.Try(s.toInt).getOrElse(500)
    }
  }
}

/** Types used in API generation */
object ApiTypes {
  val Void = jvm.Type.Qualified("java.lang.Void")
}
