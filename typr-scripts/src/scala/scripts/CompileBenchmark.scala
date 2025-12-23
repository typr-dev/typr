package scripts

import bleep.*
import bleep.commands.SourceGen
import bleep.model.{CrossProjectName, ProjectName}
import typr.*
import typr.internal.codegen.LangScala
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.generate
import typr.internal.sqlfiles.SqlFileReader

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object CompileBenchmark extends BleepScript("CompileBenchmark") {
  val buildDir = Path.of(sys.props("user.dir"))

  case class Result(
      lib: String,
      crossId: model.CrossId,
      inlineImplicits: Boolean,
      fixVerySlowImplicit: Boolean,
      avgtime: Long,
      mintime: Long,
      times: List[Long]
  )

  override def run(started: Started, commands: Commands, args: List[String]): Unit = {
    val ds = TypoDataSource.hikariPostgres(server = "localhost", port = 6432, databaseName = "Adventureworks", username = "postgres", password = "password")
    val externalTools = ExternalTools.init(TypoLogger.Noop, ExternalToolsConfig.default)
    val metadb = Await.result(MetaDb.fromDb(logger = TypoLogger.Noop, ds = ds, viewSelector = Selector.All, schemaMode = SchemaMode.MultiSchema, externalTools = externalTools), Duration.Inf)
    val sqlFiles = Await.result(SqlFileReader(TypoLogger.Noop, buildDir.resolve("adventureworks_sql"), ds, externalTools), Duration.Inf)

    val crossIds = List(
      "jvm212",
      "jvm213",
      "jvm3"
    ).map(str => model.CrossId(str))
    val variants = List(
      ("baseline (only case class)", None, JsonLibName.PlayJson, "typr-tester-none", true),
      ("zio-jdbc", Some(DbLibName.ZioJdbc), JsonLibName.ZioJson, "typr-tester-zio-jdbc", true),
      ("doobie (with fix)", Some(DbLibName.Doobie), JsonLibName.Circe, "typr-tester-doobie", true),
      ("doobie (without fix)", Some(DbLibName.Doobie), JsonLibName.Circe, "typr-tester-doobie", false),
      ("anorm", Some(DbLibName.Anorm), JsonLibName.PlayJson, "typr-tester-anorm", true),
      ("zio-json", None, JsonLibName.ZioJson, "typr-tester-zio-jdbc", true),
      ("circe", None, JsonLibName.Circe, "typr-tester-doobie", true),
      ("play-json", None, JsonLibName.PlayJson, "typr-tester-anorm", true)
    )
    val GeneratedAndCheckedIn = Path.of("generated-and-checked-in")

    val results: List[Result] =
      variants.flatMap { case (lib, dbLib, jsonLib, projectName, fixVerySlowImplicit) =>
        val targetSources = buildDir.resolve(projectName).resolve(GeneratedAndCheckedIn)

        List(false, true).flatMap { inlineImplicits =>
          val options = Options(
            pkg = "adventureworks",
            lang = LangScala.javaDsl(Dialect.Scala2XSource3, TypeSupportScala),
            dbLib = dbLib,
            jsonLibs = List(jsonLib),
            enableTestInserts = Selector.All,
            enableStreamingInserts = false,
            enableDsl = dbLib.nonEmpty,
            inlineImplicits = inlineImplicits,
            fixVerySlowImplicit = fixVerySlowImplicit
          )
          generate(
            options,
            metadb,
            ProjectGraph(
              name = "",
              targetSources,
              None,
              Selector.ExcludePostgresInternal, // All
              sqlFiles,
              Nil
            ),
            Map.empty
          ).foreach(_.overwriteFolder())

          crossIds.map { crossId =>
            started.projectPaths(CrossProjectName(ProjectName(projectName), Some(crossId))).sourcesDirs.fromSourceLayout.foreach { p =>
              started.logger.warn(s"Deleting $p because tests will not compile")
              bleep.internal.FileUtils.deleteDirectory(p)
            }

            val desc = s"${crossId.value}, lib=$lib, inlineImplicits=$inlineImplicits, fixVerySlowImplicit=$fixVerySlowImplicit"
            println(s"START $desc")
            val times = 0.to(2).map { _ =>
              val crossProjectName = model.CrossProjectName(model.ProjectName(projectName), Some(crossId))
              commands.clean(List(crossProjectName))
              SourceGen(false, Array(crossProjectName)).run(started).orThrow
              time(commands.compile(List(crossProjectName)))
            }
            val avgtime = times.sum / times.length
            val mintime = times.min
            val res = Result(
              lib = lib,
              crossId = crossId,
              inlineImplicits = inlineImplicits,
              fixVerySlowImplicit = fixVerySlowImplicit,
              avgtime = avgtime,
              mintime = mintime,
              times = times.toList
            )
            println(res)
            res
          }
        }
      }

    results foreach println
  }

  def time(run: => Unit): Long = {
    val start = System.currentTimeMillis()
    run
    System.currentTimeMillis() - start
  }
}
