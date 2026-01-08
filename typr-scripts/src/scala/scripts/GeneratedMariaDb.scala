package scripts

import bleep.FileWatching
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typr.*
import typr.internal.codegen.*
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.sqlfiles.SqlFileReader
import typr.internal.{FileSync, generate}

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object GeneratedMariaDb {
  val buildDir = Path.of(sys.props("user.dir"))

  // clickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        val ds = TypoDataSource.hikariMariaDb(
          server = "localhost",
          port = 3307,
          databaseName = "typr",
          username = "typr",
          password = "password"
        )
        val scriptsPath = buildDir.resolve("sql-scripts/mariadb")
        val selector = Selector.All
        // Only enable precision types for the dedicated precision test tables
        val precisionTypesSelector = Selector.relationNames("precision_types", "precision_types_null")
        val typoLogger = TypoLogger.Console

        val externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.SingleSchema("typr"), externalTools), Duration.Inf)

        val variants: Seq[(Lang, DbLibName, JsonLibName, String, String)] = List(
          (LangJava, DbLibName.Typo, JsonLibName.Jackson, "testers/mariadb/java", ""),
          (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, JsonLibName.Jackson, "testers/mariadb/scala", ""),
          (LangKotlin(TypeSupportKotlin), DbLibName.Typo, JsonLibName.Jackson, "testers/mariadb/kotlin", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds, externalTools), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
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

            newFiles
              .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
              .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }
              .foreach { case (path, synced) => logger.withContext("path", path).warn(synced.toString) }

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
      }
}
