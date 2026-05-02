package typr
package internal
package external

import coursier.cache.{ArchiveCache, CacheLogger, FileCache}
import coursier.error.CoursierError
import coursier.util.{Artifact, Task}

import java.io.File
import java.nio.file.Path
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

/** Wrapper around coursier for downloading and caching artifacts */
case class TypoCoursier(logger: TypoLogger, downloadsDir: Path, osArch: OsArch) {
  private val coursierLogger = new TypoCoursier.TypoCacheLogger(logger)

  private implicit val ec: ExecutionContext = ExecutionContext.global

  // provides downloads
  val fileCache: FileCache[Task] =
    FileCache[Task]()
      .withLogger(coursierLogger)
      .withLocation(downloadsDir.resolve("cache").toFile)
      .withMaxRedirections(2)

  // provides extracted files
  val artifacts: ArchiveCache[Task] =
    ArchiveCache[Task]()
      .withCache(fileCache)
      .withLocation(downloadsDir.resolve("archives").toFile)

  /** Download and extract an artifact, blocking until complete */
  def fetchArtifact(url: String): Either[String, File] = {
    val task = artifacts.get(Artifact(url))
    try {
      val future = task.future()(ec)
      val result = Await.result(future, Duration.Inf)
      result match {
        case Left(err)   => Left(s"Failed to download $url: $err")
        case Right(file) => Right(file)
      }
    } catch {
      case ex: Exception => Left(s"Failed to download $url: ${ex.getMessage}")
    }
  }
}

object TypoCoursier {

  class TypoCacheLogger(logger: TypoLogger) extends CacheLogger {
    def retrying(err: CoursierError, retriesLeft: Int): Unit =
      logger.info(s"Resolving dependencies failed. Retrying (${retriesLeft} retries left). Error: ${messagesFrom(err).mkString(": ")}")

    override def downloadingArtifact(url: String, artifact: Artifact): Unit =
      logger.info(s"Downloading: $url")

    override def downloadedArtifact(url: String, success: Boolean): Unit =
      if (success) logger.info(s"Downloaded: $url")
      else logger.warn(s"Could not download: $url")
  }

  def messagesFrom(th: Throwable): List[String] = {
    def rec(th: Throwable): List[String] = th.getMessage :: Option(th.getCause).toList.flatMap(rec)
    rec(th).distinct
  }
}
