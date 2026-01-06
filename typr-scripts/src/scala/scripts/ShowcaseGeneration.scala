package scripts

import bleep.cli
import ryddig.Logger
import typr.*
import typr.internal.codegen.*
import typr.internal.external.ExternalTools
import typr.internal.sqlfiles.SqlFileReader
import typr.internal.FileSync

import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

/** Shared showcase code generation logic used by all database generator scripts.
  *
  * The showcase schema demonstrates Typr's capabilities across all databases with a common base schema and database-specific extensions. Output is used for website examples and documentation.
  */
object ShowcaseGeneration {

  /** Language variants for showcase - one of each language */
  val variants: Seq[(Lang, DbLibName, String)] = List(
    (LangJava, DbLibName.Typo, "java"),
    (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, "scala"),
    (LangKotlin(TypeSupportKotlin), DbLibName.Typo, "kotlin")
  )

  /** Generate showcase code for a database.
    *
    * @param dbName
    *   Short name for output directory (e.g., "postgres", "mariadb")
    * @param metadb
    *   Database metadata
    * @param selector
    *   Selector for filtering which relations to generate
    * @param scriptsPath
    *   Path to common SQL scripts
    * @param ds
    *   Data source for SQL file parsing
    * @param externalTools
    *   External tools for code formatting
    * @param buildDir
    *   Build directory root
    * @param logger
    *   Logger for output
    * @param ec
    *   Execution context
    */
  def generate(
      dbName: String,
      metadb: MetaDb,
      selector: Selector,
      scriptsPath: Path,
      ds: TypoDataSource,
      externalTools: ExternalTools,
      buildDir: Path,
      logger: Logger
  )(implicit ec: ExecutionContext): Unit = {
    val typoLogger = TypoLogger.Console

    logger.warn(s"Generating showcase for $dbName...")
    logger.warn(s"  Found ${metadb.relations.size} relations")

    val sqlScripts = Await.result(SqlFileReader(typoLogger, scriptsPath, ds, externalTools), Duration.Inf)

    variants.foreach { case (lang, dbLib, langName) =>
      val options = Options(
        pkg = "showcase",
        lang = lang,
        dbLib = Some(dbLib),
        jsonLibs = Nil,
        generateMockRepos = Selector.All,
        enablePrimaryKeyType = Selector.All,
        enableTestInserts = Selector.All,
        enableDsl = true
      )
      val targetSources = buildDir.resolve(s"showcase-generated/$dbName/$langName")

      val newFiles: Generated =
        typr.internal.generate.orThrow(options, metadb, ProjectGraph(name = "", targetSources, None, selector, sqlScripts, Nil), Map.empty).head

      val changedFiles = newFiles
        .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
        .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }

      if (changedFiles.nonEmpty) {
        changedFiles.foreach { case (path, synced) =>
          logger.withContext("path", path).info(synced.toString)
        }
        logger.warn(s"  Generated ${changedFiles.size} files for $dbName/$langName")
      }
    }

    val _ = cli(
      "add showcase files to git",
      buildDir,
      List("git", "add", "-f", s"showcase-generated/$dbName"),
      logger = logger,
      cli.Out.Raw
    )
  }

  /** Legacy method - load DuckDB showcase schema from SQL files.
    *
    * @deprecated
    *   Use loadShowcaseSchema(ds, DbType.DuckDB) instead
    */
  def loadDuckDbShowcaseSchema(ds: TypoDataSource, buildDir: Path)(implicit ec: ExecutionContext): Unit = {
    val schemaFiles = List("01-tables.sql", "02-extensions.sql", "04-views.sql")

    Await.result(
      ds.run { conn =>
        schemaFiles.foreach { file =>
          val path = buildDir.resolve(s"sql-init/showcase/duckdb/$file")
          if (Files.exists(path)) {
            val sql = Files.readString(path)
            val stmt = conn.createStatement()
            stmt.execute(sql)
            stmt.close()
          }
        }
      },
      Duration.Inf
    )
  }
}
