package typo.openapi

import typo.{jvm, Lang}
import typo.internal.codegen.LangScala
import typo.openapi.codegen.{
  ApiCodegen,
  FrameworkSupport,
  Http4sSupport,
  JacksonSupport,
  JaxRsSupport,
  Jsr380ValidationSupport,
  JsonLibSupport,
  MicroProfileRestClientSupport,
  ModelCodegen,
  NoFrameworkSupport,
  NoJsonLibSupport,
  NoValidationSupport,
  QuarkusReactiveServerSupport,
  ScalaTypeMapper,
  SpringBootSupport,
  TypeMapper,
  ValidationSupport
}
import typo.openapi.parser.OpenApiParser

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
      case (true, _)                       => NoJsonLibSupport // Scala uses derivation
      case (false, OpenApiJsonLib.Jackson) => JacksonSupport
      case (false, _)                      => JacksonSupport // Default to Jackson for Java
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

    // Determine client framework support based on clientLib
    val clientFrameworkSupport: Option[FrameworkSupport] = options.clientLib.map {
      case OpenApiClientLib.MicroProfileReactive => MicroProfileRestClientSupport
      case OpenApiClientLib.MicroProfileBlocking => MicroProfileRestClientSupport
      case OpenApiClientLib.SpringWebClient      => SpringBootSupport // TODO: implement
      case OpenApiClientLib.SpringRestTemplate   => SpringBootSupport // TODO: implement
      case OpenApiClientLib.VertxMutiny          => MicroProfileRestClientSupport // Similar to MicroProfile
      case OpenApiClientLib.Http4s               => Http4sSupport
      case OpenApiClientLib.Sttp                 => NoFrameworkSupport // TODO: implement
      case OpenApiClientLib.ZioHttp              => NoFrameworkSupport // TODO: implement
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

    val typeMapper: TypeMapper = if (isScala) {
      new ScalaTypeMapper(modelPkg, options.typeOverrides, lang)
    } else {
      new TypeMapper(modelPkg, options.typeOverrides, lang)
    }
    val modelCodegen = new ModelCodegen(modelPkg, typeMapper, lang, jsonLib, validationSupport)
    val apiCodegen = new ApiCodegen(
      apiPkg,
      typeMapper,
      lang,
      jsonLib,
      serverFrameworkSupport,
      clientFrameworkSupport,
      sumTypeNames,
      spec.securitySchemes,
      effectTypeWithOps
    )

    val files = List.newBuilder[jvm.File]

    // Generate model classes
    spec.models.foreach { model =>
      files += modelCodegen.generate(model)
    }

    // Generate sum types
    spec.sumTypes.foreach { sumType =>
      files += modelCodegen.generateSumType(sumType)
    }

    // Generate API interfaces (base, server, client, and response sum types)
    if (options.generateApiInterfaces) {
      spec.apis.foreach { api =>
        files ++= apiCodegen.generate(api)
      }
    }

    Result(files.result(), Nil)
  }
}
