package typr.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import java.nio.file.Paths

object Main
    extends CommandIOApp(
      name = "typr",
      header = "Type-safe database code generator",
      version = "0.1.0"
    ) {

  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = scala.concurrent.duration.Duration.Inf)

  private val configOpt: Opts[String] =
    Opts
      .option[String]("config", help = "Config file path", short = "c")
      .withDefault("typr.yaml")

  private val sourceOpt: Opts[Option[String]] =
    Opts
      .option[String]("source", help = "Generate for specific source only", short = "s")
      .orNone

  private val quietFlag: Opts[Boolean] =
    Opts.flag("quiet", help = "Minimal output", short = "q").orFalse

  private val debugFlag: Opts[Boolean] =
    Opts.flag("debug", help = "Verbose output").orFalse

  private val generateCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("generate", help = "Generate code from config")(
      (configOpt, sourceOpt, quietFlag, debugFlag).mapN(commands.Generate.run)
    )

  private val interactiveCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("interactive", help = "Interactive config editor")(
      configOpt.map(path => commands.Interactive.run(Paths.get(path)))
    )

  private val watchCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("watch", help = "Watch SQL files and regenerate on changes")(
      (configOpt, sourceOpt).mapN(commands.Watch.run)
    )

  private val checkCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("check", help = "Validate Bridge type alignment across sources")(
      (configOpt, quietFlag, debugFlag).mapN(commands.Check.run)
    )

  private val initCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("init", help = "Initialize new typr.yaml with interactive wizard")(
      Opts.unit.map(_ => commands.Interactive.init())
    )

  override def main: Opts[IO[ExitCode]] =
    generateCmd orElse checkCmd orElse interactiveCmd orElse watchCmd orElse initCmd
}
