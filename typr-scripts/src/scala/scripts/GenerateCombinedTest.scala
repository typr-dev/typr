package scripts

import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typr.*
import typr.internal.FileSync
import typr.internal.codegen.LangJava
import typr.openapi.{OpenApiJsonLib, OpenApiOptions, OpenApiServerLib}

import java.nio.file.Path

/** Generates code for the combined multi-database + OpenAPI integration test.
  *
  * This demonstrates how TypeDefinitions can match across:
  *   - PostgreSQL database columns (firstname, lastname, currentflag, etc.)
  *   - MariaDB database columns (first_name, last_name, is_active, etc.)
  *   - OpenAPI fields (firstName, lastName, isActive, etc.)
  *
  * All three sources share the same TypeDefinitions, creating unified wrapper types.
  */
object GenerateCombinedTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        val typoLogger = TypoLogger.Console

        // TypeDefinitions that match across ALL sources:
        // - PostgreSQL columns: firstname, lastname, middlename, currentflag, salariedflag
        // - MariaDB columns: first_name, last_name, email, is_active
        // - OpenAPI fields: firstName, lastName, middleName, email, isActive, isSalaried
        val sharedTypeDefinitions = TypeDefinitions(
          // Name types - match different naming conventions across sources
          TypeEntry(
            name = "FirstName",
            db = DbMatch.column("firstname", "first_name"),
            api = ApiMatch.name("firstName")
          ),
          TypeEntry(
            name = "LastName",
            db = DbMatch.column("lastname", "last_name"),
            api = ApiMatch.name("lastName")
          ),
          TypeEntry(
            name = "MiddleName",
            db = DbMatch.column("middlename"),
            api = ApiMatch.name("middleName")
          ),
          // Boolean flags with semantic meaning
          TypeEntry(
            name = "IsActive",
            db = DbMatch.column("currentflag", "is_active", "activeflag"),
            api = ApiMatch.name("isActive")
          ),
          TypeEntry(
            name = "IsSalaried",
            db = DbMatch.column("salariedflag"),
            api = ApiMatch.name("isSalaried")
          )
        )

        // Configure the three sources
        val postgresSource = SchemaSource.Database(
          name = "postgres",
          dataSource = TypoDataSource.hikariPostgres(
            server = "localhost",
            port = 6432,
            databaseName = "Adventureworks",
            username = "postgres",
            password = "password"
          ),
          pkg = jvm.QIdent("combined.postgres"),
          targetFolder = buildDir.resolve("testers/combined/java/generated-and-checked-in/postgres"),
          testTargetFolder = None,
          // Only include HR-related tables for this demo
          selector = Selector.relationNames(
            "employee",
            "person",
            "emailaddress",
            "businessentity"
          ) and Selector.schemas("humanresources", "person"),
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

        val mariaDbSource = SchemaSource.Database(
          name = "mariadb",
          dataSource = TypoDataSource.hikariMariaDb(
            server = "localhost",
            port = 3307,
            databaseName = "typr",
            username = "typr",
            password = "password"
          ),
          pkg = jvm.QIdent("combined.mariadb"),
          targetFolder = buildDir.resolve("testers/combined/java/generated-and-checked-in/mariadb"),
          testTargetFolder = None,
          // Only include customer-related tables for this demo
          selector = Selector.relationNames(
            "customers",
            "customer_status",
            "addresses",
            "products",
            "brands",
            "categories"
          ),
          schemaMode = SchemaMode.SingleSchema("typr"),
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

        val openApiSource = SchemaSource.OpenApi(
          name = "api",
          specPath = buildDir.resolve("testers/combined/specs/combined-api.yaml"),
          pkg = jvm.QIdent("combined.api"),
          targetFolder = buildDir.resolve("testers/combined/java/generated-and-checked-in/api"),
          testTargetFolder = None,
          options = OpenApiOptions
            .default(jvm.QIdent("combined.api"))
            .copy(
              jsonLib = OpenApiJsonLib.Jackson,
              serverLib = Some(OpenApiServerLib.QuarkusReactive),
              generateValidation = true,
              useGenericResponseTypes = false
            )
        )

        // Generate code from all sources with shared TypeDefinitions
        // Shared types (FirstName, LastName, etc.) go to combined.shared package
        val sharedTargetFolder = buildDir.resolve("testers/combined/java/generated-and-checked-in/shared")

        val config = GenerateConfig(
          sharedPkg = Some(jvm.QIdent("combined.shared")),
          sharedTargetFolder = Some(sharedTargetFolder),
          sources = List(postgresSource, mariaDbSource, openApiSource),
          lang = LangJava,
          jsonLibs = List(JsonLibName.Jackson),
          typeDefinitions = sharedTypeDefinitions,
          typeOverride = TypeOverride.Empty,
          fileHeader = Options.header,
          logger = typoLogger,
          inlineImplicits = true,
          fixVerySlowImplicit = true,
          keepDependencies = false,
          debugTypes = false
        )

        logger.warn("Generating combined multi-database + OpenAPI code...")
        logger.warn(s"TypeDefinitions: ${sharedTypeDefinitions.entries.map(_.name).mkString(", ")}")

        val results = generateFromConfig.applyAndWrite(
          config,
          softWrite = FileSync.SoftWrite.Yes(Set.empty)
        )

        results.foreach { case (source, synced) =>
          val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
          logger.warn(s"Source '${source.name}': ${synced.size} files (${changed.size} changed)")
          changed.foreach { case (path, status) =>
            logger.withContext("path", path).info(status.toString)
          }
        }

        // Git add the generated files (including shared types folder)
        GitOps.gitAdd(
          "add combined test files",
          buildDir,
          List(
            "testers/combined/java/generated-and-checked-in/postgres",
            "testers/combined/java/generated-and-checked-in/mariadb",
            "testers/combined/java/generated-and-checked-in/api",
            "testers/combined/java/generated-and-checked-in/shared"
          ),
          logger
        )

        logger.warn("Done!")
      }
}
