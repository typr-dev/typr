package typr.jsonschema

import typr.{jvm, Lang, TypeDefinitions}
import typr.internal.codegen.LangScala
import typr.openapi.OpenApiJsonLib
import typr.openapi.codegen.{
  CirceSupport,
  JacksonSupport,
  JsonLibSupport,
  Jsr380ValidationSupport,
  ModelCodegen,
  NoFrameworkSupport,
  NoValidationSupport,
  ScalaTypeMapper,
  TypeMapper,
  ValidationSupport
}

import java.nio.file.Path

object JsonSchemaCodegen {

  case class Result(
      files: List[jvm.File],
      errors: List[String]
  )

  def generate(
      schemaPath: Path,
      options: JsonSchemaOptions,
      lang: Lang
  ): Result = {
    JsonSchemaParser.parseFile(schemaPath) match {
      case Left(errors) =>
        Result(Nil, errors)
      case Right(parsed) =>
        generateFromParsed(parsed, options, lang)
    }
  }

  def generateFromContent(
      content: String,
      isYaml: Boolean,
      options: JsonSchemaOptions,
      lang: Lang
  ): Result = {
    JsonSchemaParser.parseContent(content, isYaml) match {
      case Left(errors) =>
        Result(Nil, errors)
      case Right(parsed) =>
        generateFromParsed(parsed, options, lang)
    }
  }

  private def generateFromParsed(
      parsed: JsonSchemaParser.ParsedJsonSchema,
      options: JsonSchemaOptions,
      lang: Lang
  ): Result = {
    val isScala = lang.isInstanceOf[LangScala]

    val jsonLib: JsonLibSupport = (isScala, options.jsonLib) match {
      case (_, OpenApiJsonLib.Jackson) => JacksonSupport
      case (_, OpenApiJsonLib.Circe)   => CirceSupport
      case (true, _)                   => CirceSupport
      case (false, _)                  => JacksonSupport
    }

    val validationSupport: ValidationSupport =
      if (options.generateValidation && !isScala) Jsr380ValidationSupport
      else NoValidationSupport

    val useJackson = options.jsonLib == OpenApiJsonLib.Jackson
    val typeMapper: TypeMapper = if (isScala) {
      new ScalaTypeMapper(options.pkg, options.typeOverrides, lang, useJackson)
    } else {
      new TypeMapper(options.pkg, options.typeOverrides, lang)
    }

    val modelCodegen = new ModelCodegen(
      options.pkg,
      typeMapper,
      lang,
      jsonLib,
      validationSupport,
      NoFrameworkSupport,
      TypeDefinitions.Empty,
      Map.empty
    )

    val files = List.newBuilder[jvm.File]

    parsed.models.foreach { model =>
      files += modelCodegen.generate(model)
    }

    parsed.sumTypes.foreach { sumType =>
      files += modelCodegen.generateSumType(sumType)
    }

    Result(files.result(), Nil)
  }
}
