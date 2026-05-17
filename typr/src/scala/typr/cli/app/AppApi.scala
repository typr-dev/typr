package typr.cli.app

import jatatui.react.{Context, RenderContext}
import typr.cli.config.TyprConfig

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent

/** Per-source background-load state. The shell kicks off a fetch for every configured source on startup; screens read `loadStatus` to render badges, and read [[AppApi.sourceCache]] once a source
  * flips to `Loaded` for instant access.
  */
sealed trait LoadStatus
object LoadStatus {
  case object NotLoaded extends LoadStatus
  case object Loading extends LoadStatus
  case object Loaded extends LoadStatus
  final case class Failed(error: String) extends LoadStatus
}

/** App-wide capabilities provided by [[Shell]] via Context.
  *
  * `sourceCache` is the single process-wide cache keyed on source name and typed as [[LoadedSource]] — one sealed sum across all source kinds. Populated up-front by [[Shell]]'s background loader on
  * app start, so the domain-type discovery flows have data to work with without any explicit "open the browser first" step. The cache stays populated for the rest of the process — screens never
  * refetch what's already there.
  *
  * `loadStatus` mirrors the cache key set with one of four states (NotLoaded / Loading / Loaded / Failed) so screens can disambiguate "not configured" from "still loading" from "failed".
  */
trait AppApi {
  def config: TyprConfig
  def configPath: java.nio.file.Path
  def updateConfig(f: TyprConfig => TyprConfig): Either[Throwable, Unit]
  def quit(): Unit
  def sourceCache: concurrent.Map[String, LoadedSource]
  def loadStatus: concurrent.Map[String, LoadStatus]
}

object AppApi {

  /** Sensible no-op default for screens rendered outside the provider (e.g. tests). Reads return an empty config; writes are dropped; quit does nothing.
    */
  val NO_OP: AppApi = new AppApi {
    import scala.jdk.CollectionConverters.*
    private val empty = TyprConfig(None, None, None, None, None)
    private val cache: concurrent.Map[String, LoadedSource] = new ConcurrentHashMap[String, LoadedSource]().asScala
    private val status: concurrent.Map[String, LoadStatus] = new ConcurrentHashMap[String, LoadStatus]().asScala
    override def config: TyprConfig = empty
    override def configPath: java.nio.file.Path = java.nio.file.Paths.get("typr.yaml")
    override def updateConfig(f: TyprConfig => TyprConfig): Either[Throwable, Unit] = Right(())
    override def quit(): Unit = ()
    override def sourceCache: concurrent.Map[String, LoadedSource] = cache
    override def loadStatus: concurrent.Map[String, LoadStatus] = status
  }

  val CONTEXT: Context[AppApi] = Context.create(NO_OP)

  def use(ctx: RenderContext): AppApi = ctx.useContext(CONTEXT)
}
