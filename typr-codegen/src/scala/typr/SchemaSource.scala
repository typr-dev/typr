package typr

import typr.openapi.{OpenApiOptions, ParsedSpec}

import java.nio.file.Path

/** A source of schema information for code generation.
  *
  * SchemaSource represents different origins of type information:
  *   - Database tables and views
  *   - OpenAPI specifications
  *
  * Multiple sources can be combined in a single generation run, allowing TypeDefinitions to match across all sources.
  */
sealed trait SchemaSource {

  /** Unique name for this source (used in error messages and logging) */
  def name: String

  /** The output package for generated code from this source */
  def pkg: jvm.QIdent

  /** Output directory for generated code */
  def targetFolder: Path

  /** Optional test output directory */
  def testTargetFolder: Option[Path]
}

object SchemaSource {

  /** A database schema source.
    *
    * @param name
    *   Unique identifier for this database (e.g., "postgres", "mariadb")
    * @param dataSource
    *   Connection to the database
    * @param pkg
    *   Output package for generated code
    * @param targetFolder
    *   Output directory
    * @param testTargetFolder
    *   Optional test output directory
    * @param selector
    *   Which tables/views to include
    * @param schemaMode
    *   How to handle database schemas
    * @param scriptsPaths
    *   Paths to SQL script files
    * @param dbLib
    *   Database library to use (Typo, Anorm, etc.)
    * @param nullabilityOverride
    *   Custom nullability rules
    * @param generateMockRepos
    *   Which tables get mock repositories
    * @param enablePrimaryKeyType
    *   Which tables get type-safe ID types
    * @param enableTestInserts
    *   Which tables get test data factories
    * @param enableFieldValue
    *   Which tables get FieldValue support
    * @param readonlyRepo
    *   Which tables get read-only repositories
    * @param enableDsl
    *   Whether to generate SQL DSL
    * @param openEnums
    *   Which tables use open enums
    */
  case class Database(
      name: String,
      dataSource: TypoDataSource,
      pkg: jvm.QIdent,
      targetFolder: Path,
      testTargetFolder: Option[Path],
      selector: Selector,
      schemaMode: SchemaMode,
      scriptsPaths: List[Path],
      dbLib: DbLibName,
      nullabilityOverride: NullabilityOverride,
      generateMockRepos: Selector,
      enablePrimaryKeyType: Selector,
      enableTestInserts: Selector,
      enableFieldValue: Selector,
      readonlyRepo: Selector,
      enableDsl: Boolean,
      openEnums: Selector
  ) extends SchemaSource

  object Database {
    def apply(
        name: String,
        dataSource: TypoDataSource,
        pkg: jvm.QIdent,
        targetFolder: Path
    ): Database = Database(
      name = name,
      dataSource = dataSource,
      pkg = pkg,
      targetFolder = targetFolder,
      testTargetFolder = None,
      selector = Selector.ExcludePostgresInternal,
      schemaMode = SchemaMode.MultiSchema,
      scriptsPaths = Nil,
      dbLib = DbLibName.Typo,
      nullabilityOverride = NullabilityOverride.Empty,
      generateMockRepos = Selector.All,
      enablePrimaryKeyType = Selector.All,
      enableTestInserts = Selector.None,
      enableFieldValue = Selector.None,
      readonlyRepo = Selector.None,
      enableDsl = true,
      openEnums = Selector.None
    )
  }

  /** An OpenAPI specification source.
    *
    * @param name
    *   Unique identifier for this spec (e.g., "petstore-api")
    * @param specPath
    *   Path to the OpenAPI YAML/JSON file
    * @param pkg
    *   Output package for generated code
    * @param targetFolder
    *   Output directory
    * @param testTargetFolder
    *   Optional test output directory
    * @param options
    *   OpenAPI-specific generation options
    */
  case class OpenApi(
      name: String,
      specPath: Path,
      pkg: jvm.QIdent,
      targetFolder: Path,
      testTargetFolder: Option[Path],
      options: OpenApiOptions
  ) extends SchemaSource

  object OpenApi {
    def apply(
        name: String,
        specPath: Path,
        pkg: jvm.QIdent,
        targetFolder: Path,
        options: OpenApiOptions
    ): OpenApi = OpenApi(
      name = name,
      specPath = specPath,
      pkg = pkg,
      targetFolder = targetFolder,
      testTargetFolder = None,
      options = options
    )
  }

  /** An already-parsed OpenAPI specification source.
    *
    * Useful when you want to manipulate the spec before generation, or when generating from in-memory specs.
    */
  case class OpenApiParsed(
      name: String,
      spec: ParsedSpec,
      pkg: jvm.QIdent,
      targetFolder: Path,
      testTargetFolder: Option[Path],
      options: OpenApiOptions
  ) extends SchemaSource

  /** Synthetic source for shared/aligned types generated across multiple sources.
    *
    * This is created internally by generateFromConfig when sharedPkg is set.
    */
  case class Shared(
      pkg: jvm.QIdent,
      targetFolder: Path
  ) extends SchemaSource {
    def name: String = "shared"
    def testTargetFolder: Option[Path] = None
  }
}
