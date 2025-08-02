package scripts

import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import typo.*
import typo.internal.metadb.OpenEnum
import typo.internal.sqlfiles.readSqlFileDirectories
import typo.internal.{FileSync, generate}

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object GeneratedFrontpage {
  val buildDir = Path.of(sys.props("user.dir"))

  // clickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        val ds = TypoDataSource.hikari(server = "localhost", port = 6432, databaseName = "Adventureworks", username = "postgres", password = "password")
        val scriptsPath = buildDir.resolve("init/data/frontpage")
        val selector = Selector.schemas("frontpage")
        val typoLogger = TypoLogger.Console
        val metadb = Await.result(MetaDb.fromDb(typoLogger, ds, selector, schemaMode = SchemaMode.MultiSchema), Duration.Inf)
        val relationNameToOpenEnum = Map.empty[db.RelationName, OpenEnum]

        val sqlScripts = Await.result(readSqlFileDirectories(typoLogger, scriptsPath, ds), Duration.Inf)

        val options = Options(
          pkg = "frontpage",
          Some(DbLibName.Anorm),
          List(JsonLibName.PlayJson),
          typeOverride = TypeOverride.Empty,
          openEnums = Selector.None,
          generateMockRepos = Selector.All,
          enablePrimaryKeyType = Selector.All,
          enableTestInserts = Selector.All,
          readonlyRepo = Selector.None,
          enableDsl = true
        )
        val targetSources = buildDir.resolve(s"frontpage-generated")

        val newFiles: Generated =
          generate(options, metadb, ProjectGraph(name = "", targetSources, None, selector, sqlScripts, Nil), relationNameToOpenEnum).head

        newFiles
          .overwriteFolder(options.dialect, softWrite = FileSync.SoftWrite.Yes(Set.empty))
          .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }
          .foreach { case (path, synced) => logger.withContext("path", path).warn(synced.toString) }

        logger.info(s"Generated frontpage code to $targetSources")
      }
}
