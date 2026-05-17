package typr.cli.app

import jatatui.components.router.{Router, Screen as RouterScreen}
import jatatui.react.Components.*
import jatatui.react.Element
import typr.cli.config.{ConfigWriter, TyprConfig}

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent
import scala.jdk.CollectionConverters.*

/** Root of the new TUI. Wraps the screen stack in [[Router]] and provides the [[AppApi]] context so screens can read/mutate config without prop-drilling.
  *
  * On mount, Shell also kicks off a background load for every configured source so [[AppApi.sourceCache]] is populated by the time the user reaches any flow that depends on source data — entity
  * discovery, schema browser, type preview, etc. Loads run on daemon threads; load status is tracked per-source in [[AppApi.loadStatus]] so screens can render progress badges and disambiguate "not
  * configured" from "still loading" from "failed".
  */
object Shell {

  def element(initialConfig: TyprConfig, configPath: Path, quitHost: () => Unit): Element =
    component { ctx =>
      val configState = ctx.useState(() => initialConfig)
      val hostConfigPath = configPath
      val cacheRef = ctx.useRef(() => new ConcurrentHashMap[String, LoadedSource]().asScala: concurrent.Map[String, LoadedSource])
      val statusRef = ctx.useRef(() => new ConcurrentHashMap[String, LoadStatus]().asScala: concurrent.Map[String, LoadStatus])
      val rerender = ctx.requestRerender()

      val api: AppApi = new AppApi {
        override def config: TyprConfig = configState.latest()
        override def configPath: java.nio.file.Path = hostConfigPath
        override def sourceCache: concurrent.Map[String, LoadedSource] = cacheRef.get
        override def loadStatus: concurrent.Map[String, LoadStatus] = statusRef.get

        override def updateConfig(f: TyprConfig => TyprConfig): Either[Throwable, Unit] = {
          val next = f(configState.latest())
          configState.set(next)
          ConfigWriter.write(hostConfigPath, next)
        }

        override def quit(): Unit = quitHost()
      }

      // One-shot startup load. useEffect with empty deps runs once per mount; for the Shell that
      // means once per app run. Fans out one daemon thread per source — independent and
      // typically I/O-bound (Hikari connect, file I/O, protoc exec), so concurrency helps.
      ctx.useEffect(() => kickOffSourceLoads(api, rerender))

      // First screen depends on the beta gate. If the user hasn't accepted (or accepted under an
      // older terms-version), show the gate modal — it writes the file on accept and replaces
      // itself with Splash. Otherwise straight to Splash.
      val initialScreen =
        if (typr.cli.beta.BetaGate.isCurrent)
          RouterScreen.of("splash", screens.Splash.element())
        else
          RouterScreen.of("terms", screens.BetaNotice.element(screens.BetaNotice.Mode.Initial, "0.1.0"))

      provide(
        AppApi.CONTEXT,
        api,
        Router.of(initialScreen)
      )
    }

  private def kickOffSourceLoads(api: AppApi, rerender: Runnable): Unit = {
    val buildDir = api.configPath.toAbsolutePath.getParent
    api.config.sources.getOrElse(Map.empty).foreach { case (name, json) =>
      if (!api.loadStatus.contains(name)) {
        api.loadStatus.update(name, LoadStatus.Loading)
        val t = new Thread(
          () => {
            val result = LoadedSource.load(name, json, buildDir) match {
              case Right(loaded) =>
                api.sourceCache.update(name, loaded)
                LoadStatus.Loaded
              case Left(err) =>
                LoadStatus.Failed(err)
            }
            api.loadStatus.update(name, result)
            rerender.run()
          },
          s"init-load-$name"
        )
        t.setDaemon(true)
        t.start()
      }
    }
  }
}
