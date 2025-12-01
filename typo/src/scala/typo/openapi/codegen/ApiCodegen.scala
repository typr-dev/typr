package typo.openapi.codegen

import typo.{jvm, Lang, Scope}
import typo.jvm.Code.TypeOps
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
    val baseInterface = jvm.Class(
      annotations = Nil,
      comments = comments,
      classType = jvm.ClassType.Interface,
      name = baseTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = baseMethods,
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
          // Abstract method without annotations (just the contract from base) - override since server extends base
          val abstractMethod = generateBaseMethod(m, isOverride = true)
          // Wrapper endpoint method with annotations that returns Response (or Effect<Response> for async)
          val wrapperMethod = generateServerEndpointWrapperMethod(m, basePath, serverSupport, variants)
          List(abstractMethod, wrapperMethod)
        case None =>
          // Normal method with annotations
          val annotatedMethod = generateServerMethod(m, basePath, serverSupport)
          List(annotatedMethod)
      }
    }

    val serverInterface = jvm.Class(
      annotations = interfaceAnnotations,
      comments = jvm.Comments.Empty,
      classType = jvm.ClassType.Interface,
      name = serverTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = Some(baseTpe),
      implements = Nil,
      members = methods,
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

    // Generate methods: for methods with response variants, generate both raw method
    // (returning Response, with annotations) and a wrapper method that handles response mapping
    val methods = api.methods.flatMap { m =>
      m.responseVariants match {
        case Some(variants) =>
          // Raw method that returns Response (with framework annotations)
          val rawMethod = generateClientRawMethod(m, basePath, clientSupport)
          // Wrapper method that handles response parsing and error recovery
          val wrapperMethod = generateClientWrapperMethod(m, clientSupport, variants)
          List(rawMethod, wrapperMethod)
        case None =>
          // Normal method with annotations
          val annotatedMethod = generateClientMethod(m, basePath, clientSupport)
          List(annotatedMethod)
      }
    }

    val clientInterface = jvm.Class(
      annotations = interfaceAnnotations,
      comments = jvm.Comments.Empty,
      classType = jvm.ClassType.Interface,
      name = clientTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = Some(baseTpe),
      implements = Nil,
      members = methods,
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

      // Generate header parameters for this response variant
      // Note: TypeResolver.resolve already wraps non-required types in Optional,
      // so we don't need to wrap again here - the optionality is encoded in typeInfo
      val headerParams = variant.headers.map { header =>
        val headerType = typeMapper.map(header.typeInfo)
        jvm.Param[jvm.Type](
          annotations = jsonLib.propertyAnnotations(header.name),
          comments = header.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
          name = jvm.Ident(sanitizeHeaderName(header.name)),
          tpe = headerType,
          default = None
        )
      }

      val params = if (isRangeStatus) {
        val statusCodeParam = jvm.Param[jvm.Type](
          annotations = jsonLib.propertyAnnotations("statusCode"),
          comments = jvm.Comments(List("HTTP status code to return")),
          name = jvm.Ident("statusCode"),
          tpe = lang.Int,
          default = None
        )
        List(statusCodeParam, valueParam) ++ headerParams
      } else {
        List(valueParam) ++ headerParams
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
        constructorAnnotations = Nil,
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
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )

    // Jackson annotations for polymorphic deserialization
    // Only generate Jackson annotations for Java - Scala uses Circe derivation
    val jacksonAnnotations = lang match {
      case _: LangScala => Nil
      case _            => generateResponseSumTypeAnnotations(tpe, variants)
    }

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

    // Build nested annotations using proper AST - AnnotationArray renders as [ ] in Kotlin, { } in Java
    // Use ClassOf for annotation arguments - Kotlin auto-converts ::class (KClass) to Java Class for annotations
    val subTypesArgs = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      val subtypeTpe = jvm.Type.Qualified(tpe.value / jvm.Ident(statusName))
      jvm.Annotation(
        Types.Jackson.JsonSubTypesType,
        List(
          jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.ClassOf(subtypeTpe).code),
          jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(variant.statusCode).code)
        )
      )
    }

    val subTypesAnnotation = jvm.Annotation(
      Types.Jackson.JsonSubTypes,
      List(jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.AnnotationArray(subTypesArgs.map(_.code)).code))
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

  /** Convert header name like "X-Total-Count" to camelCase field name "xTotalCount" */
  private def sanitizeHeaderName(name: String): String = {
    val parts = name.split("-").toList
    parts match {
      case Nil          => ""
      case head :: tail => head.toLowerCase + tail.map(s => if (s.isEmpty) "" else s"${s.head.toUpper}${s.tail.toLowerCase}").mkString
    }
  }

  /** Generate code to extract a header from response and convert to the target type.
    * @param responseExpr
    *   The response expression
    * @param headerName
    *   The HTTP header name (e.g., "X-Request-Id")
    * @param targetType
    *   The target type for the header value (already mapped from typeInfo)
    * @param clientSupport
    *   Framework support for header extraction
    * @return
    *   Code that extracts and converts the header value
    */
  private def extractHeaderValue(
      responseExpr: jvm.Code,
      headerName: String,
      targetType: jvm.Type,
      clientSupport: FrameworkSupport
  ): jvm.Code = {
    val rawHeaderExpr = clientSupport.getHeaderString(responseExpr, headerName)

    // Check if the target type is Optional<X>
    targetType match {
      case jvm.Type.TApply(outer: jvm.Type.Qualified, List(innerType)) if outer.dotName == "java.util.Optional" =>
        // Optional type: wrap in Optional.ofNullable and map to convert inner type
        innerType match {
          case t if t == Types.String =>
            // Optional<String>: just wrap
            code"${Types.Java.Optional}.ofNullable($rawHeaderExpr)"
          case t if t == Types.UUID =>
            // Optional<UUID>: wrap and map to UUID.fromString
            code"${Types.Java.Optional}.ofNullable($rawHeaderExpr).map(${Types.UUID}::fromString)"
          case t if t == lang.Int || t == Types.Java.Integer =>
            // Optional<Integer>: wrap and map to Integer.parseInt
            code"${Types.Java.Optional}.ofNullable($rawHeaderExpr).map(${Types.Java.Integer}::parseInt)"
          case t if t == lang.Long || t == Types.Java.Long =>
            // Optional<Long>: wrap and map to Long.parseLong
            code"${Types.Java.Optional}.ofNullable($rawHeaderExpr).map(${Types.Java.Long}::parseLong)"
          case _ =>
            // Unknown type, just wrap as-is (likely a String-compatible type)
            code"${Types.Java.Optional}.ofNullable($rawHeaderExpr)"
        }
      case t if t == Types.String =>
        // Required String: use directly
        rawHeaderExpr
      case t if t == Types.UUID =>
        // Required UUID: convert from String
        code"${Types.UUID}.fromString($rawHeaderExpr)"
      case t if t == lang.Int || t == Types.Java.Integer =>
        // Required Integer: parse
        code"${Types.Java.Integer}.parseInt($rawHeaderExpr)"
      case t if t == lang.Long || t == Types.Java.Long =>
        // Required Long: parse
        code"${Types.Java.Long}.parseLong($rawHeaderExpr)"
      case _ =>
        // Unknown type, use as-is (may need casting in some cases)
        rawHeaderExpr
    }
  }

  /** Generate base method (no framework annotations, just return type and params) */
  private def generateBaseMethod(method: ApiMethod, isOverride: Boolean = false): jvm.Method = {
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
      body = jvm.Body.Abstract, // Interface method - no body
      isOverride = isOverride,
      isDefault = false
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
      body = jvm.Body.Abstract, // Interface method - no body
      isOverride = true, // Server interface extends base interface, so methods are overrides
      isDefault = false
    )
  }

  /** Generate client method with framework annotations */
  private def generateClientMethod(method: ApiMethod, basePath: Option[String], clientSupport: FrameworkSupport): jvm.Method = {
    // Client methods have the same structure as server methods
    generateServerMethod(method, basePath, clientSupport)
  }

  /** Generate a raw client method that returns Response (for methods with multiple status codes) */
  private def generateClientRawMethod(method: ApiMethod, basePath: Option[String], clientSupport: FrameworkSupport): jvm.Method = {
    val comments = method.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate parameters with framework annotations
    val params = generateParams(method, clientSupport)

    // Return type is Response (or Effect<Response> for async)
    val responseType = clientSupport.responseType
    val returnType = effectOps match {
      case Some(ops) => jvm.Type.TApply(ops.tpe, List(responseType))
      case None      => responseType
    }

    // Create method with relative path for framework annotations
    val methodWithRelativePath = method.copy(path = relativePath(method.path, basePath))

    // Framework annotations (@GET, @POST, @Path, @Produces, @Consumes)
    val frameworkAnnotations = clientSupport.methodAnnotations(methodWithRelativePath)

    // Security annotations (@SecurityRequirement)
    val securityAnnotations = clientSupport.securityAnnotations(method.security)

    jvm.Method(
      annotations = frameworkAnnotations ++ securityAnnotations,
      comments = comments,
      tparams = Nil,
      name = jvm.Ident(method.name + "Raw"),
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Abstract, // Interface method - no body
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate a client wrapper method that handles response parsing and error recovery */
  private def generateClientWrapperMethod(
      method: ApiMethod,
      clientSupport: FrameworkSupport,
      variants: List[ResponseVariant]
  ): jvm.Method = {
    val comments = jvm.Comments(List(s"${method.description.getOrElse(capitalize(method.name))} - handles response status codes"))

    // Generate parameters without framework annotations (these go on the raw method)
    val params = generateBaseParams(method)

    // Return type is the response sum type (or Effect<ResponseSumType> for async)
    val responseName = capitalize(method.name) + "Response"
    val responseTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName))
    val returnType = effectOps match {
      case Some(ops) => jvm.Type.TApply(ops.tpe, List(responseTpe))
      case None      => responseTpe
    }

    // Build the method body
    val body = generateClientWrapperBody(method, clientSupport, variants)

    jvm.Method(
      annotations = Nil,
      comments = comments,
      tparams = Nil,
      name = jvm.Ident(method.name),
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body(body),
      isOverride = true, // Client interface extends base interface, so this overrides the base method
      isDefault = true
    )
  }

  /** Generate the body of the client wrapper method */
  private def generateClientWrapperBody(
      method: ApiMethod,
      clientSupport: FrameworkSupport,
      variants: List[ResponseVariant]
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
    val rawMethodCall = code"${jvm.Ident(method.name + "Raw")}($argsCode)"

    val responseIdent = jvm.Ident("response")
    val responseName = capitalize(method.name) + "Response"

    // For async entity reading (e.g., Cats Effect/HTTP4s), generate flatMap-based code
    if (clientSupport.isAsyncEntityRead) {
      generateAsyncClientWrapperBody(rawMethodCall, responseIdent, responseName, variants, clientSupport)
    } else {
      generateSyncOrMutinyClientWrapperBody(rawMethodCall, responseIdent, responseName, variants, clientSupport)
    }
  }

  /** Generate client wrapper body for async entity reading (Cats Effect/HTTP4s style) */
  private def generateAsyncClientWrapperBody(
      rawMethodCall: jvm.Code,
      responseIdent: jvm.Ident,
      responseName: String,
      variants: List[ResponseVariant],
      clientSupport: FrameworkSupport
  ): List[jvm.Code] = {
    // Generate flatMap-based handling:
    // rawMethodCall.flatMap { response =>
    //   val statusCode = response.status.code
    //   if (statusCode == 200) response.as[Pet].map(v => GetPetResponse.Status200(v))
    //   else if (statusCode == 404) response.as[Error].map(v => GetPetResponse.Status404(v))
    //   else IO.raiseError(new IllegalStateException(s"Unexpected status code: $statusCode"))
    // }

    val statusCodeIdent = jvm.Ident("statusCode")
    val statusCodeExpr = clientSupport.getStatusCode(responseIdent.code)
    val statusCodeDecl = code"val ${statusCodeIdent.code} = $statusCodeExpr"

    // Build if-else chain for each variant, using response.as[T].map for entity reading
    val ifElseCases = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      val subtypeTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName) / jvm.Ident(statusName))
      val bodyType = typeMapper.map(variant.typeInfo)
      val readEntityCode = clientSupport.readEntity(responseIdent.code, bodyType)

      // Check if this is a range status code (has statusCode field)
      val isRangeStatus = variant.statusCode.toLowerCase match {
        case "4xx" | "5xx" | "default" | "2xx" => true
        case _                                 => false
      }

      val condition = variant.statusCode.toLowerCase match {
        case "2xx"     => code"${statusCodeIdent.code} >= 200 && ${statusCodeIdent.code} < 300"
        case "4xx"     => code"${statusCodeIdent.code} >= 400 && ${statusCodeIdent.code} < 500"
        case "5xx"     => code"${statusCodeIdent.code} >= 500 && ${statusCodeIdent.code} < 600"
        case "default" => code"true" // default case matches everything
        case s =>
          val statusInt = scala.util.Try(s.toInt).getOrElse(500)
          code"${statusCodeIdent.code} == $statusInt"
      }

      // For async: response.as[T].map(v => ResponseType(v)) or response.as[T].map(v => ResponseType(statusCode, v))
      val valueIdent = jvm.Ident("v")
      val constructorCall = if (isRangeStatus) {
        code"$subtypeTpe(${statusCodeIdent.code}, ${valueIdent.code})"
      } else {
        code"$subtypeTpe(${valueIdent.code})"
      }
      val result = code"$readEntityCode.map(${valueIdent.code} => $constructorCall)"

      (condition, result, variant.statusCode.toLowerCase == "default")
    }

    // Generate if-else chain, putting default case last
    val (defaultCases, specificCases) = ifElseCases.partition(_._3)

    val ifElseCode = if (specificCases.isEmpty && defaultCases.nonEmpty) {
      // Only default case
      defaultCases.head._2
    } else if (specificCases.isEmpty) {
      // No cases - raise error
      val errorExpr = Types.IllegalStateException.construct(jvm.StrLit("No response handler for status code").code)
      clientSupport.raiseError(errorExpr)
    } else {
      // Build if-else expression
      val ifCases = specificCases.zipWithIndex.map { case ((cond, body, _), idx) =>
        if (idx == 0) code"if ($cond) $body"
        else code"else if ($cond) $body"
      }
      val elseCase = defaultCases.headOption
        .map(_._2)
        .getOrElse {
          val errorExpr = Types.IllegalStateException.construct(lang.s(code"Unexpected status code: ${statusCodeIdent.code}").code)
          clientSupport.raiseError(errorExpr)
        }
      val elseCode = code"else $elseCase"
      (ifCases :+ elseCode).mkCode("\n")
    }

    // Generate flatMap body with braces
    val flatMapBody = code"""{
  $statusCodeDecl
  $ifElseCode
}"""
    val flatMapCall = code"$rawMethodCall.flatMap { ${responseIdent.code} => $flatMapBody }"

    List(flatMapCall)
  }

  /** Generate client wrapper body for sync or Mutiny-style async */
  private def generateSyncOrMutinyClientWrapperBody(
      rawMethodCall: jvm.Code,
      responseIdent: jvm.Ident,
      responseName: String,
      variants: List[ResponseVariant],
      clientSupport: FrameworkSupport
  ): List[jvm.Code] = {
    // Generate status code handling - an if-else chain matching on status codes
    val statusCodeHandlingCode = generateStatusCodeHandling(responseIdent, responseName, variants, clientSupport)

    effectOps match {
      case Some(ops) =>
        // Async: recover from WebApplicationException first, then map once
        // Pattern: rawMethodCall.onFailure(ExceptionType.class).recoverWithItem(e -> ((ExceptionType) e).getResponse()).map(response -> handleStatus(response))
        val exceptionIdent = jvm.Ident("e")
        val exceptionType = clientSupport.clientExceptionType

        // Build the recover lambda: e -> ((ExceptionType) e).getResponse()
        // Note: Mutiny's API always uses Function<Throwable, T>, so we need to cast
        val castException = jvm.Cast(exceptionType, exceptionIdent.code)
        val exceptionResponseCode = clientSupport.getResponseFromException(castException.code)
        val recoverLambda = jvm.SamLambda(
          Types.Java.Function(Types.Throwable, clientSupport.responseType),
          jvm.Lambda(List(jvm.LambdaParam.typed(exceptionIdent, Types.Throwable)), jvm.Body.Expr(exceptionResponseCode))
        )
        val exceptionClassLiteral = jvm.JavaClassOf(exceptionType).code
        val recoveredCall = code"$rawMethodCall.onFailure($exceptionClassLiteral).recoverWithItem(${recoverLambda.code})"

        // Now map the recovered Uni<Response> to handle status codes
        val mapLambda = jvm.Lambda(List(jvm.LambdaParam.typed(responseIdent, clientSupport.responseType)), jvm.Body.Expr(statusCodeHandlingCode))
        val mappedCall = ops.map(recoveredCall, mapLambda.code)

        List(mappedCall)

      case None =>
        // Sync: wrap in try-catch
        val exceptionIdent = jvm.Ident("e")
        val exceptionType = clientSupport.clientExceptionType
        val rawResponseType = clientSupport.responseType

        // Build the exception handling code
        val exceptionResponseCode = clientSupport.getResponseFromException(exceptionIdent.code)
        val exceptionHandlingCode = generateStatusCodeHandlingFromResponse(exceptionResponseCode, responseName, variants, clientSupport)

        // Generate try-catch block
        val tryCatch = jvm.TryCatch(
          tryBlock = List(
            code"$rawResponseType ${responseIdent.code} = $rawMethodCall;",
            statusCodeHandlingCode
          ),
          catches = List(
            jvm.TryCatch.Catch(exceptionType, exceptionIdent, List(exceptionHandlingCode))
          ),
          finallyBlock = Nil
        )
        // Add null at the end so the method generates "return null;" (unreachable but satisfies compiler)
        List(tryCatch.code, code"null")
    }
  }

  /** Generate if-else chain for handling different status codes from a response variable */
  private def generateStatusCodeHandling(
      responseIdent: jvm.Ident,
      responseName: String,
      variants: List[ResponseVariant],
      clientSupport: FrameworkSupport
  ): jvm.Code = {
    generateStatusCodeHandlingFromResponse(responseIdent.code, responseName, variants, clientSupport)
  }

  /** Generate if-else chain for handling different status codes from a response expression */
  private def generateStatusCodeHandlingFromResponse(
      responseExpr: jvm.Code,
      responseName: String,
      variants: List[ResponseVariant],
      clientSupport: FrameworkSupport
  ): jvm.Code = {
    val statusCodeExpr = clientSupport.getStatusCode(responseExpr)

    // Build if-else chain for each variant
    val ifElseCases = variants.map { variant =>
      val statusName = "Status" + normalizeStatusCode(variant.statusCode)
      val subtypeTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(responseName) / jvm.Ident(statusName))
      val bodyType = typeMapper.map(variant.typeInfo)
      val readEntityCode = clientSupport.readEntity(responseExpr, bodyType)

      // Check if this is a range status code (has statusCode field)
      val isRangeStatus = variant.statusCode.toLowerCase match {
        case "4xx" | "5xx" | "default" | "2xx" => true
        case _                                 => false
      }

      val condition = variant.statusCode.toLowerCase match {
        case "2xx"     => code"$statusCodeExpr >= 200 && $statusCodeExpr < 300"
        case "4xx"     => code"$statusCodeExpr >= 400 && $statusCodeExpr < 500"
        case "5xx"     => code"$statusCodeExpr >= 500 && $statusCodeExpr < 600"
        case "default" => code"true" // default case matches everything
        case s =>
          val statusInt = scala.util.Try(s.toInt).getOrElse(500)
          code"$statusCodeExpr == $statusInt"
      }

      // Extract header values for this variant
      val headerValues = variant.headers.map { header =>
        val headerType = typeMapper.map(header.typeInfo)
        extractHeaderValue(responseExpr, header.name, headerType, clientSupport)
      }

      // Use AST construct method for cross-language compatibility (no `new` in Kotlin)
      // Don't include `return` - in Kotlin lambdas, the last expression is the return value
      // Constructor order: [statusCode], value, headers...
      val allArgs = if (isRangeStatus) {
        List(statusCodeExpr, readEntityCode) ++ headerValues
      } else {
        List(readEntityCode) ++ headerValues
      }
      val constructorCall = subtypeTpe.construct(allArgs: _*)

      (condition, constructorCall, variant.statusCode.toLowerCase == "default")
    }

    // Generate if-else chain, putting default case last
    val (defaultCases, specificCases) = ifElseCases.partition(_._3)

    if (specificCases.isEmpty && defaultCases.nonEmpty) {
      // Only default case
      defaultCases.head._2
    } else if (specificCases.isEmpty) {
      // No cases - throw error
      jvm.Throw(Types.IllegalStateException.construct(jvm.StrLit("No response handler for status code").code)).code
    } else {
      // Build if-else chain
      val ifElse = jvm.IfElseChain(
        cases = specificCases.map { case (cond, result, _) => (cond, result) },
        elseCase = defaultCases.headOption
          .map(_._2)
          .getOrElse(
            jvm.Throw(Types.IllegalStateException.construct(code"${jvm.StrLit("Unexpected status code: ").code} + $statusCodeExpr")).code
          )
      )
      ifElse.code
    }
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
      body = jvm.Body(body),
      isOverride = false,
      isDefault = true
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

      // Use lang.prop for proper field access syntax (Java: .value(), Scala: .value)
      val valueAccess = lang.prop(bindingIdent.code, jvm.Ident("value"))
      val statusCodeAccess = lang.prop(bindingIdent.code, jvm.Ident("statusCode"))

      val body = if (isRangeStatus) {
        // Use the user-provided statusCode field
        serverSupport.buildStatusResponse(statusCodeAccess, valueAccess)
      } else if (defaultStatusCode >= 200 && defaultStatusCode < 300) {
        // Fixed success response
        serverSupport.buildOkResponse(valueAccess)
      } else {
        // Fixed error response
        serverSupport.buildStatusResponse(code"$defaultStatusCode", valueAccess)
      }

      jvm.TypeSwitch.Case(subtypeTpe, bindingIdent, body)
    }

    effectOps match {
      case Some(ops) =>
        // Async: wrap in lambda and use map operation
        val fallbackCase = Some(jvm.Throw(Types.IllegalStateException.construct(jvm.StrLit("Unexpected response type").code)).code)
        val typeSwitch = jvm.TypeSwitch(responseIdent.code, switchCases, nullCase = None, defaultCase = fallbackCase)
        val lambda = jvm.Lambda(List(jvm.LambdaParam.typed(responseIdent, responseTpe)), jvm.Body.Expr(typeSwitch.code))
        val mappedCall = ops.map(methodCall, lambda.code)
        List(mappedCall)

      case None =>
        // Sync: directly switch on the result and return
        val fallbackCase = Some(jvm.Throw(Types.IllegalStateException.construct(jvm.StrLit("Unexpected response type").code)).code)
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

  /** Generate webhook handler interface. Webhooks are callbacks that the API server will call to notify you of events. The generated interface should be implemented by the webhook receiver.
    */
  def generateWebhook(webhook: Webhook): List[jvm.File] = {
    val webhookName = webhook.name + "Webhook"
    val webhookTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(webhookName))
    val comments = webhook.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate response sum types for methods with multiple response variants
    val responseSumTypeFiles = webhook.methods.flatMap { method =>
      method.responseVariants.map { variants =>
        generateResponseSumType(method.name, variants)
      }
    }

    // Generate handler methods for each webhook operation
    val methods = webhook.methods.map(m => generateBaseMethod(m))

    val webhookInterface = jvm.Class(
      annotations = Nil,
      comments = comments,
      classType = jvm.ClassType.Interface,
      name = webhookTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(webhookInterface, lang.Ctx.Empty)
    val baseFile = jvm.File(webhookTpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)

    baseFile :: responseSumTypeFiles
  }

  /** Generate callback handler interface. Callbacks are endpoints the API will call back to after certain operations. For example, after createPet, the API might call back to a URL provided in the
    * request body with the created pet data. The callback name is derived from the operation name and callback name (e.g., CreatePetOnPetCreatedCallback).
    */
  def generateCallback(method: ApiMethod, callback: Callback): List[jvm.File] = {
    val callbackName = capitalize(method.name) + callback.name + "Callback"
    val callbackTpe = jvm.Type.Qualified(apiPkg / jvm.Ident(callbackName))
    val comments = jvm.Comments(List(s"Callback handler for ${method.name} - ${callback.name}", s"Runtime expression: ${callback.expression}"))

    // Generate response sum types for callback methods with multiple response variants
    val responseSumTypeFiles = callback.methods.flatMap { m =>
      m.responseVariants.map { variants =>
        generateResponseSumType(m.name, variants)
      }
    }

    // Generate handler methods for each callback operation
    val methods = callback.methods.map(m => generateBaseMethod(m))

    val callbackInterface = jvm.Class(
      annotations = Nil,
      comments = comments,
      classType = jvm.ClassType.Interface,
      name = callbackTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(callbackInterface, lang.Ctx.Empty)
    val baseFile = jvm.File(callbackTpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)

    baseFile :: responseSumTypeFiles
  }
}

/** Types used in API generation */
object ApiTypes {
  val Void = jvm.Type.Qualified("java.lang.Void")
}
