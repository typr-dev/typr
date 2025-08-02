package scripts

import bleep.{FileWatching, cli}
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typo.*
import typo.internal.metadb.OpenEnum
import typo.internal.sqlfiles.readSqlFileDirectories
import typo.internal.{FileSync, generate}

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object GeneratedAdventureWorks {
  val buildDir = Path.of(sys.props("user.dir"))

  // çlickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        val ds = TypoDataSource.hikari(server = "localhost", port = 6432, databaseName = "Adventureworks", username = "postgres", password = "password")
        val scriptsPath = buildDir.resolve("adventureworks_sql")
        val selector = Selector.ExcludePostgresInternal and !Selector.schemas("frontpage")
        val typoLogger = TypoLogger.Console
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.MultiSchema), Duration.Inf)
        val openEnumSelector = Selector.relationNames("title", "title_domain", "issue142")
        val relationNameToOpenEnum = Await.result(
          OpenEnum.find(
            ds,
            typoLogger,
            Selector.All,
            openEnumSelector = openEnumSelector,
            metaDb = metadb
          ),
          Duration.Inf
        )
        val oldFilesRef = new AtomicReference(Map.empty[RelPath, sc.Code])
        val variants = List(
          (DbLibName.Anorm, JsonLibName.PlayJson, "typo-tester-anorm", "2.13", Dialect.Scala2XSource3),
          (DbLibName.Anorm, JsonLibName.PlayJson, "typo-tester-anorm", "3", Dialect.Scala3),
          (DbLibName.Doobie, JsonLibName.Circe, "typo-tester-doobie", "2.13", Dialect.Scala2XSource3),
          (DbLibName.Doobie, JsonLibName.Circe, "typo-tester-doobie", "3", Dialect.Scala3),
          (DbLibName.ZioJdbc, JsonLibName.ZioJson, "typo-tester-zio-jdbc", "2.13", Dialect.Scala2XSource3),
          (DbLibName.ZioJdbc, JsonLibName.ZioJson, "typo-tester-zio-jdbc", "3", Dialect.Scala3)
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(readSqlFileDirectories(typoLogger, scriptsPath, ds), Duration.Inf)

          variants.foreach { case (dbLib, jsonLib, projectPath, scalaVersion, dialect) =>
            val options = Options(
              pkg = "adventureworks",
              Some(dbLib),
              List(jsonLib),
              typeOverride = TypeOverride.relation {
                case (_, "firstname")                     => "adventureworks.userdefined.FirstName"
                case ("sales.creditcard", "creditcardid") => "adventureworks.userdefined.CustomCreditcardId"
              },
              openEnums = openEnumSelector,
              generateMockRepos = !Selector.relationNames("purchaseorderdetail"),
              enablePrimaryKeyType = !Selector.relationNames("billofmaterials"),
              enableTestInserts = Selector.All,
              readonlyRepo = Selector.relationNames("purchaseorderdetail"),
              enableDsl = true,
              dialect = dialect
            )
            val targetSources = buildDir.resolve(s"$projectPath/generated-and-checked-in-$scalaVersion")

            val newFiles: Generated =
              generate(options, metadb, ProjectGraph(name = "", targetSources, None, selector, newSqlScripts, Nil), relationNameToOpenEnum).head

            val knownUnchanged: Set[RelPath] = {
              val oldFiles = oldFilesRef.get()
              newFiles.files.iterator.collect { case (relPath, contents) if oldFiles.get(relPath).contains(contents) => relPath }.toSet
            }
            oldFilesRef.set(newFiles.files)

            newFiles
              .overwriteFolder(dialect, softWrite = FileSync.SoftWrite.Yes(knownUnchanged))
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
        // note that this does not listen to changes in db schema naturally, though I'm sure that's possible to do as well
        if (args.contains("--watch")) {
          logger.warn(s"watching for changes in .sql files under $scriptsPath")
          FileWatching(logger, Map(scriptsPath -> List("sql scripts")))(_ => go())
            .run(FileWatching.StopWhen.OnStdInput)
        }
      }
}
