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

  private val binaryVersion = "0.1.0"

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

  /** Closed-beta acceptance flag. One-time: writes the acceptance file. Available on every subcommand that performs real work (generate / watch / check / tui / init); a no-op on `terms` which is
    * informational but accepted anyway so users can read + accept in one step.
    */
  private val acceptFlag: Opts[Boolean] =
    Opts.flag("accept", help = "Accept the closed-beta terms (one-time; persists in ~/.config/typr)").orFalse

  /** What kind of beta gating to apply. CLI commands refuse without acceptance; TUI/init flows carry the user through an in-app modal so they only need the expiry check pre-launch.
    */
  private enum GateLevel {
    case CliStrict // expiry + acceptance required
    case TuiOrInit // expiry only; acceptance enforced inside the TUI
    case Informational // no gate (used by `typr terms`)
  }

  private def gated(level: GateLevel, accept: Boolean, cmd: IO[ExitCode]): IO[ExitCode] =
    IO.blocking {
      // 1. Expired binaries refuse universally (except `terms`, which stays informational so
      //    users can still read the wall they hit).
      if (level != GateLevel.Informational && typr.cli.beta.BetaGate.isExpired()) {
        System.err.println(typr.cli.beta.BetaGate.expiredMessage)
        Left(ExitCode(78))
        // 2. `--accept` writes the file (if needed) and then continues.
      } else if (accept && !typr.cli.beta.BetaGate.isCurrent) {
        try {
          typr.cli.beta.BetaGate.markAccepted(binaryVersion)
          System.out.println(s"✓ Beta terms accepted. (Saved to ${typr.cli.beta.BetaGate.configPath})")
        } catch {
          case e: Throwable =>
            // Don't refuse on a write failure — the user accepted explicitly via the flag; printing
            // a warning lets them know they'll see the prompt again next run.
            System.err.println(s"warning: could not persist acceptance: ${e.getMessage}")
        }
        Right(())
        // 3. CLI commands require acceptance. TUI flows allow the modal to handle it.
      } else if (level == GateLevel.CliStrict && !typr.cli.beta.BetaGate.isCurrent) {
        System.err.println(typr.cli.beta.BetaGate.notAcceptedMessage)
        Left(ExitCode(78))
      } else Right(())
    }.flatMap {
      case Left(code) => IO.pure(code)
      case Right(_)   => cmd
    }

  /** The only path from `Opts.subcommand` to a runnable `commands.*` body in this object. Every CLI verb declares itself through [[cmd]] and so cannot bypass the closed-beta gate — adding a new
    * unguarded command requires either editing this helper (visible in review) or reaching outside it (which would stand out as an `Opts.subcommand` call in plain sight).
    */
  private def cmd(name: String, help: String, level: GateLevel)(inner: Opts[IO[ExitCode]]): Opts[IO[ExitCode]] =
    Opts.subcommand(name, help = help)(
      (inner, acceptFlag).mapN { (run, accept) => gated(level, accept, run) }
    )

  private val generateCmd: Opts[IO[ExitCode]] =
    cmd("generate", "Generate code from config", GateLevel.CliStrict)(
      (configOpt, sourceOpt, quietFlag, debugFlag).mapN(commands.Generate.run)
    )

  private val watchCmd: Opts[IO[ExitCode]] =
    cmd("watch", "Watch SQL files and regenerate on changes", GateLevel.CliStrict)(
      (configOpt, sourceOpt).mapN(commands.Watch.run)
    )

  private val checkCmd: Opts[IO[ExitCode]] =
    cmd("check", "Validate Bridge type alignment across sources", GateLevel.CliStrict)(
      (configOpt, quietFlag, debugFlag).mapN(commands.Check.run)
    )

  private val tuiCmd: Opts[IO[ExitCode]] =
    cmd("tui", "Interactive config editor", GateLevel.TuiOrInit)(
      configOpt.map(path => app.App.run(Paths.get(path)))
    )

  private val initCmd: Opts[IO[ExitCode]] =
    cmd("init", "Initialize a new typr.yaml in the current directory", GateLevel.TuiOrInit)(
      configOpt.map(path => app.App.init(Paths.get(path)))
    )

  /** `typr terms` — print the current closed-beta terms to stdout and exit. Declared at [[GateLevel.Informational]] so the expiry / acceptance refusals don't fire; users can still read what they're
    * agreeing to even after the wall.
    */
  private val termsCmd: Opts[IO[ExitCode]] =
    cmd("terms", "Print the current closed-beta terms", GateLevel.Informational)(
      Opts(IO.blocking {
        System.out.println(typr.cli.beta.BetaTerms.displayText)
        if (typr.cli.beta.BetaGate.isInWarningWindow())
          System.out.println(typr.cli.beta.BetaGate.warningLine())
        ExitCode.Success
      })
    )

  override def main: Opts[IO[ExitCode]] =
    generateCmd orElse checkCmd orElse tuiCmd orElse watchCmd orElse initCmd orElse termsCmd
}
