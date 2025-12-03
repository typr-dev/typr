package scripts

import bleep.{FileWatching, cli}
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typo.*
import typo.internal.codegen.*
import typo.internal.sqlfiles.SqlFileReader
import typo.internal.{FileSync, generate}

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
          databaseName = "typo",
          username = "typo",
          password = "password"
        )
        val scriptsPath = buildDir.resolve("mariadb_sql")
        val selector = Selector.All
        val typoLogger = TypoLogger.Console

        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.SingleSchema("typo")), Duration.Inf)

        val variants: Seq[(Lang, DbLibName, Option[JsonLibName], String, String)] = List(
          (LangJava, DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-mariadb-java", ""),
          (LangScala(Dialect.Scala3, TypeSupportJava), DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-mariadb-scala", ""),
          (LangKotlin, DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-mariadb-kotlin", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
            val options = Options(
              pkg = "testdb",
              lang = lang,
              dbLib = Some(dbLib),
              jsonLibs = jsonLib.toList,
              generateMockRepos = Selector.All,
              enablePrimaryKeyType = Selector.All,
              enableTestInserts = Selector.All,
              enableDsl = true
            )
            val targetSources = buildDir.resolve(s"$projectPath/generated-and-checked-in$suffix")

            val newFiles: Generated =
              generate(options, metadb, ProjectGraph(name = "", targetSources, None, selector, newSqlScripts, Nil), Map.empty).head

            newFiles
              .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
              .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }
              .foreach { case (path, synced) => logger.withContext("path", path).warn(synced.toString) }

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
      }
}
