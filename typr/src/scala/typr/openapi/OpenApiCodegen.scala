package typr.openapi

import typr.{jvm, Lang, TypeSupportJava, TypeSupportScala}
import typr.internal.codegen.LangScala
import typr.openapi.codegen.{
  ApiCodegen,
  CirceSupport,
  FrameworkSupport,
  Http4sSupport,
  JacksonSupport,
  JaxRsSupport,
  JdkHttpClientSupport,
  Jsr380ValidationSupport,
  JsonLibSupport,
  ModelCodegen,
  NoFrameworkSupport,
  NoValidationSupport,
  QuarkusReactiveServerSupport,
  ScalaTypeMapper,
  SpringBootSupport,
  TypeMapper,
  ValidationSupport
}
import typr.openapi.parser.OpenApiParser

import java.nio.file.Path

/** Main entry point for OpenAPI code generation */
object OpenApiCodegen {

  case class Result(
      files: List[jvm.File],
      errors: List[String]
  )

  /** Generate code from an OpenAPI spec file */
  def generate(
      specPath: Path,
      options: OpenApiOptions,
      lang: Lang
  ): Result = {
    OpenApiParser.parseFile(specPath) match {
      case Left(errors) =>
        Result(Nil, errors)
      case Right(parsedSpec) =>
        generateFromSpec(parsedSpec, options, lang)
    }
  }

  /** Generate code from an OpenAPI spec URL or file path */
  def generate(
      specLocation: String,
      options: OpenApiOptions,
      lang: Lang
  ): Result = {
    OpenApiParser.parse(specLocation) match {
      case Left(errors) =>
        Result(Nil, errors)
      case Right(parsedSpec) =>
        generateFromSpec(parsedSpec, options, lang)
    }
  }

  /** Generate code from YAML/JSON content */
  def generateFromContent(
      content: String,
      options: OpenApiOptions,
      lang: Lang
  ): Result = {
    OpenApiParser.parseContent(content) match {
      case Left(errors) =>
        Result(Nil, errors)
      case Right(parsedSpec) =>
        generateFromSpec(parsedSpec, options, lang)
    }
  }

  /** Generate code from a parsed spec */
  def generateFromSpec(
      spec: ParsedSpec,
      options: OpenApiOptions,
      lang: Lang
  ): Result = {
    val modelPkg = options.pkg / jvm.Ident(options.modelPackage)
    val apiPkg = options.pkg / jvm.Ident(options.apiPackage)

    val isScala = lang.isInstanceOf[LangScala]

    val jsonLib: JsonLibSupport = (isScala, options.jsonLib) match {
      case (_, OpenApiJsonLib.Jackson) => JacksonSupport
      case (_, OpenApiJsonLib.Circe)   => CirceSupport
      case (true, _)                   => CirceSupport // Default to Circe for Scala
      case (false, _)                  => JacksonSupport // Default to Jackson for Java/Kotlin
    }

    // Determine server framework support based on serverLib
    val serverFrameworkSupport: Option[FrameworkSupport] = options.serverLib.map {
      case OpenApiServerLib.QuarkusReactive => QuarkusReactiveServerSupport
      case OpenApiServerLib.QuarkusBlocking => JaxRsSupport
      case OpenApiServerLib.SpringWebFlux   => SpringBootSupport // TODO: reactive version
      case OpenApiServerLib.SpringMvc       => SpringBootSupport
      case OpenApiServerLib.JaxRsAsync      => JaxRsSupport
      case OpenApiServerLib.JaxRsSync       => JaxRsSupport
      case OpenApiServerLib.Http4s          => Http4sSupport
      case OpenApiServerLib.ZioHttp         => NoFrameworkSupport // TODO: implement
    }

    // Validate TypeSupport compatibility with server framework
    (options.serverLib, lang) match {
      case (Some(OpenApiServerLib.SpringMvc | OpenApiServerLib.SpringWebFlux), ls: LangScala) =>
        require(
          ls.typeSupport == TypeSupportJava,
          "Spring MVC/WebFlux with Scala requires TypeSupportJava (use java.util.Optional, java.util.List, etc.)"
        )
      case (Some(OpenApiServerLib.Http4s), ls: LangScala) =>
        require(
          ls.typeSupport == TypeSupportScala,
          "Http4s requires TypeSupportScala (use scala.Option, scala.List, etc.)"
        )
      case _ => // No validation needed for other combinations
    }

    // Determine client framework support based on clientLib
    val clientFrameworkSupport: Option[FrameworkSupport] = options.clientLib.map {
      case OpenApiClientLib.JdkHttpClient(_)   => JdkHttpClientSupport
      case OpenApiClientLib.SpringWebClient    => SpringBootSupport // TODO: implement
      case OpenApiClientLib.SpringRestTemplate => SpringBootSupport // TODO: implement
      case OpenApiClientLib.Http4s             => Http4sSupport
      case OpenApiClientLib.Sttp               => NoFrameworkSupport // TODO: implement
      case OpenApiClientLib.ZioHttp            => NoFrameworkSupport // TODO: implement
    }

    // Scala doesn't use JSR-380 annotations - validation is done differently
    val validationSupport: ValidationSupport =
      if (options.generateValidation && !isScala) Jsr380ValidationSupport
      else NoValidationSupport

    // Collect sum type names for nested sum type detection
    val sumTypeNames = spec.sumTypes.map(_.name).toSet

    // Get effect type and ops from server or client lib
    val effectTypeWithOps: Option[(jvm.Type.Qualified, EffectTypeOps)] = {
      def extractEffectTypeWithOps(effectType: OpenApiEffectType): Option[(jvm.Type.Qualified, EffectTypeOps)] =
        for {
          tpe <- effectType.effectType
          ops <- effectType.ops
        } yield (tpe, ops)

      options.serverLib
        .flatMap(lib => extractEffectTypeWithOps(lib.effectType))
        .orElse(options.clientLib.flatMap(lib => extractEffectTypeWithOps(lib.effectType)))
    }

    // Collect all unique response shapes if using generic response types
    val responseShapes: Map[String, ResponseShape] = if (options.useGenericResponseTypes) {
      collectAllResponseShapes(spec)
    } else {
      Map.empty
    }

    // Build mapping of status code -> list of shapes that contain it
    val statusCodeToShapes: Map[String, List[ResponseShape]] = if (options.useGenericResponseTypes) {
      buildStatusCodeToShapesMapping(responseShapes.values.toList)
    } else {
      Map.empty
    }

    val typeMapper: TypeMapper = if (isScala) {
      new ScalaTypeMapper(modelPkg, options.typeOverrides, lang)
    } else {
      new TypeMapper(modelPkg, options.typeOverrides, lang)
    }
    val modelCodegen = new ModelCodegen(
      modelPkg,
      typeMapper,
      lang,
      jsonLib,
      validationSupport,
      serverFrameworkSupport.getOrElse(NoFrameworkSupport),
      options.typeDefinitions,
      options.fieldTypeOverrides
    )
    val apiCodegen = new ApiCodegen(
      apiPkg,
      typeMapper,
      lang,
      jsonLib,
      serverFrameworkSupport,
      clientFrameworkSupport,
      sumTypeNames,
      spec.securitySchemes,
      effectTypeWithOps,
      options.useGenericResponseTypes
    )

    val files = List.newBuilder[jvm.File]

    // Generate wrapper types for TypeDefinitions entries that have api name patterns
    if (!options.typeDefinitions.isEmpty) {
      options.typeDefinitions.entries.foreach { entry =>
        if (entry.api.name.nonEmpty) {
          val wrapperFile = generateWrapperTypeForEntry(entry, modelPkg, lang, jsonLib, serverFrameworkSupport.getOrElse(NoFrameworkSupport))
          files += wrapperFile
        }
      }
    }

    // Generate model classes
    spec.models.foreach { model =>
      files += modelCodegen.generate(model)
    }

    // Generate sum types
    spec.sumTypes.foreach { sumType =>
      files += modelCodegen.generateSumType(sumType)
    }

    // Generate generic response types if enabled
    // For Java: each type in its own file (Java requires one public type per file)
    // For Scala/Kotlin: all types in one file (allowed by language)
    if (options.useGenericResponseTypes && responseShapes.nonEmpty) {
      files ++= apiCodegen.generateAllResponseTypes(responseShapes.values.toList, statusCodeToShapes)
    }

    // Generate API interfaces (base, server, client, and response sum types)
    if (options.generateApiInterfaces) {
      spec.apis.foreach { api =>
        files ++= apiCodegen.generate(api)
      }
    }

    // Generate webhook handler interfaces (OpenAPI 3.1+)
    if (options.generateWebhooks) {
      spec.webhooks.foreach { webhook =>
        files ++= apiCodegen.generateWebhook(webhook)
      }
    }

    // Generate callback handler interfaces for methods that have callbacks
    if (options.generateCallbacks) {
      spec.apis.foreach { api =>
        api.methods.foreach { method =>
          method.callbacks.foreach { callback =>
            files ++= apiCodegen.generateCallback(method, callback)
          }
        }
      }
    }

    Result(files.result(), Nil)
  }

  /** Collect all unique response shapes from the spec */
  private def collectAllResponseShapes(spec: ParsedSpec): Map[String, ResponseShape] = {
    val allVariants: List[List[ResponseVariant]] = {
      val apiVariants = spec.apis.flatMap(_.methods).flatMap(_.responseVariants)
      val webhookVariants = spec.webhooks.flatMap(_.methods).flatMap(_.responseVariants)
      apiVariants ++ webhookVariants
    }

    allVariants
      .map(ResponseShape.fromVariants)
      .groupBy(_.shapeId)
      .map { case (k, v) => (k, v.head) }
  }

  /** Build mapping of status code -> list of shapes that contain it */
  private def buildStatusCodeToShapesMapping(shapes: List[ResponseShape]): Map[String, List[ResponseShape]] = {
    shapes
      .flatMap(shape => shape.statusCodes.map(code => (code, shape)))
      .groupBy(_._1)
      .map { case (code, pairs) => (code, pairs.map(_._2)) }
  }

  /** Generate a wrapper type for a TypeDefinitions entry.
    *
    * The underlying type is inferred from the entry name:
    *   - Names containing "Is" or ending with "Flag" -> Boolean
    *   - Everything else -> String
    */
  private def generateWrapperTypeForEntry(
      entry: typr.TypeEntry,
      modelPkg: jvm.QIdent,
      lang: Lang,
      jsonLib: codegen.JsonLibSupport,
      serverFramework: codegen.FrameworkSupport
  ): jvm.File = {
    import typr.Scope

    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(entry.name))

    // Infer underlying type based on naming convention
    val underlyingType: jvm.Type = {
      val nameLower = entry.name.toLowerCase
      if (nameLower.startsWith("is") || nameLower.endsWith("flag") || nameLower.contains("active") || nameLower.contains("salaried")) {
        lang.Boolean
      } else {
        lang.String
      }
    }

    val valueParam = jvm.Param(
      annotations = jsonLib.valueAnnotations,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("value"),
      tpe = underlyingType,
      default = None
    )

    val staticMembers = jsonLib.wrapperTypeStaticMembers(tpe, underlyingType) ++
      serverFramework.wrapperTypeStaticMembers(tpe, underlyingType)

    val record = jvm.Adt.Record(
      annotations = jsonLib.wrapperAnnotations(tpe),
      constructorAnnotations = jsonLib.constructorAnnotations,
      isWrapper = true,
      privateConstructor = false,
      comments = jvm.Comments.Empty,
      name = tpe,
      tparams = Nil,
      params = List(valueParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = staticMembers
    )

    val generatedCode = lang.renderTree(record, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }
}
