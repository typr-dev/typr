package scripts

import bleep.{FileWatching, cli}
import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typr.*
import typr.internal.codegen.*
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.SqlFileReader
import typr.internal.{FileSync, generate}

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
        // Only include tables actually used by tests to reduce compile time
        // This reduces generated repos from ~177 to ~31
        val usedTables = Selector.relationNames(
          // humanresources
          "department",
          "employee",
          // person
          "address",
          "addresstype",
          "businessentity",
          "businessentityaddress",
          "countryregion",
          "emailaddress",
          "password",
          "person",
          "stateprovince",
          // production
          "product",
          "productcategory",
          "productcosthistory",
          "productmodel",
          "productsubcategory",
          "unitmeasure",
          // public
          "flaff",
          "identity-test",
          "issue142",
          "issue142_2",
          "only_pk_columns",
          "pgtest",
          "pgtestnull",
          "title",
          "title_domain",
          "titledperson",
          "users",
          // sales
          "salesperson",
          "salesterritory"
        )
        val usedSchemas = Selector.schemas("humanresources", "person", "production", "public", "sales")
        val selector = Selector.ExcludePostgresInternal and !Selector.schemas("frontpage") and usedSchemas and usedTables
        val typoLogger = TypoLogger.Console
        val externalTools = ExternalTools.init(typoLogger, ExternalToolsConfig.default)
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.MultiSchema, externalTools), Duration.Inf)
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
        val variants: Seq[(Lang, DbLibName, JsonLibName, String, String)] = List(
          (LangScala.javaDsl(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.Anorm, JsonLibName.PlayJson, "testers/pg/scala/anorm", "-2.13"),
          (LangScala.javaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Anorm, JsonLibName.PlayJson, "testers/pg/scala/anorm", "-3"),
          (LangScala.javaDsl(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.Doobie, JsonLibName.Circe, "testers/pg/scala/doobie", "-2.13"),
          (LangScala.javaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Doobie, JsonLibName.Circe, "testers/pg/scala/doobie", "-3"),
          (LangScala.javaDsl(Dialect.Scala2XSource3, TypeSupportScala), DbLibName.ZioJdbc, JsonLibName.ZioJson, "testers/pg/scala/zio-jdbc", "-2.13"),
          (LangScala.javaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.ZioJdbc, JsonLibName.ZioJson, "testers/pg/scala/zio-jdbc", "-3"),
          (LangJava, DbLibName.Typo, JsonLibName.Jackson, "testers/pg/java", ""),
          (LangScala.javaDsl(Dialect.Scala3, TypeSupportJava), DbLibName.Typo, JsonLibName.Jackson, "testers/pg/scala/javatypes", ""),
          (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, JsonLibName.Jackson, "testers/pg/scala/scalatypes", "")
        )

        def go(): Unit = {
          val newSqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds, externalTools), Duration.Inf)

          variants.foreach { case (lang, dbLib, jsonLib, projectPath, suffix) =>
            val options = Options(
              pkg = "adventureworks",
              lang = lang,
              dbLib = Some(dbLib),
              jsonLibs = List(jsonLib),
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
