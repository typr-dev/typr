package typr

import java.nio.file.Path

/** Configuration for unified code generation across multiple schema sources.
  *
  * This allows combining database schemas and OpenAPI specs in a single generation run, with shared TypeDefinitions that can match across all sources.
  *
  * @param sharedPkg
  *   Optional package for unified shared types. When specified, TypeDefinitions-matched types are aligned across sources and placed here instead of in each source's userdefined package.
  * @param sharedTargetFolder
  *   Where to write shared type files. Required when sharedPkg is set.
  * @param sources
  *   List of schema sources (databases, OpenAPI specs)
  * @param lang
  *   Target language for code generation
  * @param jsonLibs
  *   JSON libraries to generate support for
  * @param typeDefinitions
  *   Shared type definitions that can match across all sources
  * @param typeOverride
  *   Additional type overrides (applied after TypeDefinitions)
  * @param fileHeader
  *   Header to add to generated files
  * @param logger
  *   Logger for status messages
  * @param inlineImplicits
  *   Whether to inline implicits (Scala-specific)
  * @param fixVerySlowImplicit
  *   Whether to fix slow implicit resolution (Doobie-specific)
  * @param keepDependencies
  *   Whether to keep transitive dependencies in output
  * @param debugTypes
  *   Whether to output debug info about types
  */
case class GenerateConfig(
    sharedPkg: Option[jvm.QIdent],
    sharedTargetFolder: Option[Path],
    sources: List[SchemaSource],
    lang: Lang,
    jsonLibs: List[JsonLibName],
    typeDefinitions: TypeDefinitions,
    typeOverride: TypeOverride,
    fileHeader: String,
    logger: TypoLogger,
    inlineImplicits: Boolean,
    fixVerySlowImplicit: Boolean,
    keepDependencies: Boolean,
    debugTypes: Boolean
)

object GenerateConfig {

  /** Create a config with sensible defaults (no unified shared types) */
  def apply(
      sources: List[SchemaSource],
      lang: Lang
  ): GenerateConfig = GenerateConfig(
    sharedPkg = None,
    sharedTargetFolder = None,
    sources = sources,
    lang = lang,
    jsonLibs = List(JsonLibName.Jackson),
    typeDefinitions = TypeDefinitions.Empty,
    typeOverride = TypeOverride.Empty,
    fileHeader = Options.header,
    logger = TypoLogger.Console,
    inlineImplicits = true,
    fixVerySlowImplicit = true,
    keepDependencies = false,
    debugTypes = false
  )

  /** Create a config with unified shared types in a shared package */
  def withSharedTypes(
      sharedPkg: jvm.QIdent,
      sharedTargetFolder: java.nio.file.Path,
      sources: List[SchemaSource],
      lang: Lang
  ): GenerateConfig = GenerateConfig(
    sharedPkg = Some(sharedPkg),
    sharedTargetFolder = Some(sharedTargetFolder),
    sources = sources,
    lang = lang,
    jsonLibs = List(JsonLibName.Jackson),
    typeDefinitions = TypeDefinitions.Empty,
    typeOverride = TypeOverride.Empty,
    fileHeader = Options.header,
    logger = TypoLogger.Console,
    inlineImplicits = true,
    fixVerySlowImplicit = true,
    keepDependencies = false,
    debugTypes = false
  )

  /** Create a config with a single database source */
  def database(
      source: SchemaSource.Database,
      lang: Lang
  ): GenerateConfig = apply(List(source), lang)

  /** Create a config with a single OpenAPI source */
  def openApi(
      source: SchemaSource.OpenApi,
      lang: Lang
  ): GenerateConfig = apply(List(source), lang)

  /** Create a config combining database and OpenAPI sources */
  def combined(
      databases: List[SchemaSource.Database],
      apis: List[SchemaSource.OpenApi],
      lang: Lang
  ): GenerateConfig = apply(databases ++ apis, lang)
}
