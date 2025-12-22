package scripts

import bleep.{FileWatching, cli}
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typo.*
import typo.internal.codegen.*
import typo.internal.external.{ExternalTools, ExternalToolsConfig}
import typo.internal.sqlfiles.SqlFileReader
import typo.internal.{FileSync, generate}

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/** Comprehensive Oracle code generation script
  *
  * Tests ALL Oracle-specific features:
  *   - Object types (simple and nested)
  *   - VARRAYs (variable arrays with max size)
  *   - Nested tables (unbounded collections)
  *   - All scalar types (NUMBER, VARCHAR2, DATE, TIMESTAMP, INTERVAL, BOOLEAN, etc.)
  *   - Complex nested structures (objects within objects within collections)
  *   - Views with object types
  *   - Foreign keys, constraints, identity columns
  */
object GeneratedOracle {
  val buildDir = Path.of(sys.props("user.dir"))

  // clickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        val ds = TypoDataSource.hikariOracle(
          server = "localhost",
          port = 1521,
          serviceName = "FREEPDB1", // Oracle Free pluggable database
          username = "typo",
          password = "typo_password"
        )
        val scriptsPath = buildDir.resolve("oracle_sql")
        val selector = Selector.All
        val typoLogger = TypoLogger.Console
        val externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)

        logger.warn("Fetching metadata from Oracle database...")
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.SingleSchema("TYPO"), externalTools), Duration.Inf)
        logger.warn(s"Found ${metadb.relations.size} relations (tables/views)")
        logger.warn(s"Found ${metadb.oracleObjectTypes.size} object types")
        logger.warn(s"Found ${metadb.oracleCollectionTypes.size} collection types")

        // Generate for multiple languages to test all code paths
        val variants: Seq[(Lang, DbLibName, JsonLibName, String, String)] = List(
          (LangJava, DbLibName.Typo, JsonLibName.Jackson, "typo-tester-oracle-java", ""),
          (LangScala.javaDsl(Dialect.Scala3, TypeSupportJava), DbLibName.Typo, JsonLibName.Jackson, "typo-tester-oracle-scala", ""),
          (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, JsonLibName.Jackson, "typo-tester-oracle-scala-new", ""),
          (LangKotlin(TypeSupportKotlin), DbLibName.Typo, JsonLibName.Jackson, "typo-tester-oracle-kotlin", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds, externalTools), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
            logger.warn(s"Generating code for $projectPath ($lang)")

            val options = Options(
              pkg = "oracledb", // package name
              lang = lang,
              dbLib = Some(dbLib), // Use Typo's Java DSL
              jsonLibs = List(jsonLib), // Jackson for JSON serialization
              generateMockRepos = lang match {
                case LangJava => Selector.All // Only Java has full mock support for Oracle
                case _        => Selector.None // Scala/Kotlin DSL wrappers for Oracle not yet implemented
              },
              enablePrimaryKeyType = Selector.All, // Generate type-safe ID types
              enableTestInserts = Selector.All, // Generate test data factories
              enableDsl = true // Generate type-safe SQL DSL
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

            cli(
              "add files to git",
              buildDir,
              List("git", "add", "-f", targetSources.toString),
              logger = logger,
              cli.Out.Raw
            )
          }
        }

        go()

        // demonstrate how you can `watch` for changes in sql files and immediately regenerate code
        if (args.contains("--watch")) {
          logger.warn(s"watching for changes in .sql files under $scriptsPath")
          FileWatching(logger, Map(scriptsPath -> List("sql scripts")))(_ => go())
            .run(FileWatching.StopWhen.OnStdInput)
        }

        logger.warn("Oracle code generation complete!")
      }
}
