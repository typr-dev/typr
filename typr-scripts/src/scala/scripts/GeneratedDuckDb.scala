package scripts

import bleep.FileWatching
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typr.*
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.codegen.*
import typr.internal.sqlfiles.SqlFileReader
import typr.internal.{FileSync, generate}

import java.nio.file.{Files, Path}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/** DuckDB code generation script
  *
  * DuckDB is an embedded database, so we:
  *   1. Create an in-memory database 2. Execute the schema SQL to set up tables 3. Generate code from the schema
  *
  * Tests DuckDB-specific features:
  *   - All scalar types (INTEGER, VARCHAR, DECIMAL, DATE, TIMESTAMP, UUID, JSON, etc.)
  *   - LIST types (INTEGER[], VARCHAR[], etc.)
  *   - MAP types (MAP(VARCHAR, INTEGER), etc.)
  *   - ENUM types
  *   - Foreign keys, constraints, composite primary keys
  *   - Views
  */
object GeneratedDuckDb {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  // clickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        // DuckDB is embedded - create in-memory database and load schema
        val ds = TypoDataSource.hikariDuckDbInMemory(":memory:")
        val schemaPath = buildDir.resolve("sql-init/duckdb/00-schema.sql")
        val scriptsPath = buildDir.resolve("sql-scripts/duckdb")
        val selector = Selector.All
        // Only enable precision types for the dedicated precision test tables
        val precisionTypesSelector = Selector.relationNames("precision_types", "precision_types_null")
        val typoLogger = TypoLogger.Console
        val externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)

        // Execute schema SQL to set up the database
        logger.warn(s"Loading DuckDB schema from $schemaPath...")
        Await.result(
          ds.run { conn =>
            val schemaSql = Files.readString(schemaPath)
            val stmt = conn.createStatement()
            // DuckDB can execute multiple statements at once
            stmt.execute(schemaSql)
            stmt.close()
          },
          Duration.Inf
        )

        logger.warn("Fetching metadata from DuckDB database...")
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.SingleSchema("main"), externalTools), Duration.Inf)
        logger.warn(s"Found ${metadb.relations.size} relations (tables/views)")
        logger.warn(s"Found ${metadb.enums.size} enums")

        // Generate for multiple languages to test all code paths
        val variants: Seq[(Lang, DbLibName, JsonLibName, String, String)] = List(
          (LangJava, DbLibName.Typo, JsonLibName.Jackson, "testers/duckdb/java", ""),
          (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, JsonLibName.Jackson, "testers/duckdb/scala", ""),
          (LangKotlin(TypeSupportKotlin), DbLibName.Typo, JsonLibName.Jackson, "testers/duckdb/kotlin", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds, externalTools), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
            logger.warn(s"Generating code for $projectPath ($lang)")

            val options = Options(
              pkg = "testdb",
              lang = lang,
              dbLib = Some(dbLib),
              jsonLibs = List(jsonLib),
              generateMockRepos = Selector.All,
              enablePrimaryKeyType = Selector.All,
              enableTestInserts = Selector.All,
              enableDsl = true,
              enablePreciseTypes = precisionTypesSelector
            )
            val targetSources = buildDir.resolve(s"$projectPath/generated-and-checked-in$suffix")

            val newFiles: Generated =
              generate(options, metadb, ProjectGraph(name = "", targetSources, None, selector, newSqlScripts, Nil), Map.empty).head

            val changedFiles = newFiles
              .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
              .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }

            changedFiles.foreach { case (path, synced) =>
              logger.withContext("path", path).warn(synced.toString)
            }

            logger.warn(s"Generated ${changedFiles.size} files for $projectPath")

            GitOps.gitAdd("add files to git", buildDir, List(targetSources.toString), logger)
          }
        }

        go()

        // demonstrate how you can `watch` for changes in sql files and immediately regenerate code
        if (args.contains("--watch")) {
          logger.warn(s"watching for changes in .sql files under $scriptsPath")
          FileWatching(logger, Map(scriptsPath -> List("sql scripts")))(_ => go())
            .run(FileWatching.StopWhen.OnStdInput)
        }

        logger.warn("DuckDB code generation complete!")
      }
}
