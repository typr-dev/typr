package typr

import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.SqlFileReader

import java.nio.file.Path
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/** Main entry-point for generating code from a database.
  */
object generateFromDb {

  /** Allows you to generate code into *one* folder
    */
  def apply(
      dataSource: TypoDataSource,
      options: Options,
      targetFolder: Path,
      testTargetFolder: Option[Path],
      selector: Selector = Selector.ExcludePostgresInternal,
      scriptsPaths: List[Path] = Nil
  ): List[Generated] =
    apply(dataSource, options, ProjectGraph(name = "", targetFolder, testTargetFolder, selector, scriptsPaths, Nil))

  /** Allows you to generate code into multiple folders
    */
  def apply(
      dataSource: TypoDataSource,
      options: Options,
      graph: ProjectGraph[Selector, List[Path]]
  ): List[Generated] = {
    Banner.maybePrint(options)
    implicit val ec: ExecutionContext = options.executionContext

    // Initialize external tools for sqlglot analysis (needed for both MetaDb and SqlFileReader)
    val externalTools = ExternalTools.init(options.logger, ExternalToolsConfig.default)

    val viewSelector = graph.toList.map(_.value).foldLeft(Selector.None)(_.or(_))
    val eventualMetaDb = MetaDb.fromDb(options.logger, dataSource, viewSelector, options.schemaMode, externalTools)

    val eventualScripts = graph.mapScripts(paths => Future.sequence(paths.map(p => SqlFileReader(options.logger, p, dataSource, externalTools))).map(_.flatten))

    val combined = for {
      metaDb <- eventualMetaDb
      eventualOpenEnums = OpenEnum.find(dataSource, options.logger, viewSelector, openEnumSelector = options.openEnums, metaDb = metaDb)
      openEnums <- eventualOpenEnums
      scripts <- eventualScripts
    } yield internal.generate(options, metaDb, scripts, openEnums)

    Await.result(combined, Duration.Inf)
  }
}
