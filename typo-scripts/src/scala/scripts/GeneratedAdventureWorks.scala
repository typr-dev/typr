package scripts

import bleep.{FileWatching, cli}
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typo.*
import typo.internal.pg.OpenEnum
import typo.internal.codegen.*
import typo.internal.sqlfiles.SqlFileReader
import typo.internal.{FileSync, generate}

import java.nio.file.Path
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
        val ds = TypoDataSource.hikariPostgres(server = "localhost", port = 6432, databaseName = "Adventureworks", username = "postgres", password = "password")
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
        val variants: Seq[(Lang, DbLibName, Option[JsonLibName], String, String)] = List(
          (LangScala(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.Anorm, Some(JsonLibName.PlayJson), "typo-tester-anorm", "-2.13"),
          (LangScala(Dialect.Scala3, TypeSupportScala), DbLibName.Anorm, Some(JsonLibName.PlayJson), "typo-tester-anorm", "-3"),
          (LangScala(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.Doobie, Some(JsonLibName.Circe), "typo-tester-doobie", "-2.13"),
          (LangScala(Dialect.Scala3, TypeSupportScala), DbLibName.Doobie, Some(JsonLibName.Circe), "typo-tester-doobie", "-3"),
          (LangScala(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.ZioJdbc, Some(JsonLibName.ZioJson), "typo-tester-zio-jdbc", "-2.13"),
          (LangScala(Dialect.Scala3, TypeSupportScala), DbLibName.ZioJdbc, Some(JsonLibName.ZioJson), "typo-tester-zio-jdbc", "-3"),
          (LangJava, DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-typo-java", ""),
          (LangScala(Dialect.Scala3, TypeSupportJava), DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-typo-scala", ""),
          (LangKotlin, DbLibName.Typo, Some(JsonLibName.Jackson), "typo-tester-typo-kotlin", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
            val options = Options(
              pkg = "adventureworks",
              lang = lang,
              dbLib = Some(dbLib),
              jsonLibs = jsonLib.toList,
              typeOverride = TypeOverride.relation {
                case (_, "firstname")                     => "adventureworks.userdefined.FirstName"
                case ("sales.creditcard", "creditcardid") => "adventureworks.userdefined.CustomCreditcardId"
              },
              openEnums = openEnumSelector,
              generateMockRepos = !Selector.relationNames("purchaseorderdetail"),
              enablePrimaryKeyType = !Selector.relationNames("billofmaterials"),
              enableTestInserts = Selector.All,
              readonlyRepo = Selector.relationNames("purchaseorderdetail"),
              enableDsl = true
            )
            val targetSources = buildDir.resolve(s"$projectPath/generated-and-checked-in$suffix")

            val newFiles: Generated =
              generate(options, metadb, ProjectGraph(name = "", targetSources, None, selector, newSqlScripts, Nil), relationNameToOpenEnum).head

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
        // note that this does not listen to changes in db schema naturally, though I'm sure that's possible to do as well
        if (args.contains("--watch")) {
          logger.warn(s"watching for changes in .sql files under $scriptsPath")
          FileWatching(logger, Map(scriptsPath -> List("sql scripts")))(_ => go())
            .run(FileWatching.StopWhen.OnStdInput)
        }
      }
}
