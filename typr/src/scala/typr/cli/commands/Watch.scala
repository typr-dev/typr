package typr.cli.commands

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import typr.cli.config.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.DurationInt

object Watch {
  def run(configPath: String, sourceFilter: Option[String]): IO[ExitCode] = {
    for {
      _ <- IO.println(s"Starting watch mode...")
      _ <- IO.println(s"Reading config from: $configPath")

      path <- IO(Paths.get(configPath).toAbsolutePath)
      yamlContent <- IO(Files.readString(path))
      substituted <- IO.fromEither(EnvSubstitution.substitute(yamlContent).left.map(new Exception(_)))
      config <- IO.fromEither(ConfigParser.parse(substituted).left.map(new Exception(_)))

      watchPaths <- collectWatchPaths(config, sourceFilter, path.getParent)

      _ <- IO.println(s"Watching ${watchPaths.size} directories:")
      _ <- watchPaths.toList.traverse_ { case (p, desc) => IO.println(s"  - $p ($desc)") }
      _ <- IO.println("")
      _ <- IO.println("Press Ctrl+C to stop watching.")
      _ <- IO.println("")

      _ <- runInitialGeneration(configPath, sourceFilter)

      _ <- watchLoop(watchPaths, configPath, sourceFilter)

    } yield ExitCode.Success
  }.handleErrorWith { e =>
    IO.println(s"Error: ${e.getMessage}") *> IO.pure(ExitCode.Error)
  }

  private def collectWatchPaths(
      config: TyprConfig,
      sourceFilter: Option[String],
      baseDir: Path
  ): IO[Map[Path, String]] = IO {
    val sources = config.sources.getOrElse(Map.empty)

    val paths = sources.toList.flatMap { case (name, json) =>
      if (sourceFilter.forall(_ == name)) {
        val sqlScripts = json.hcursor.get[String]("sql_scripts").toOption
        val schemaSql = json.hcursor.get[String]("schema_sql").toOption

        val scriptPath = sqlScripts.map { p =>
          val resolved = baseDir.resolve(p)
          resolved -> s"$name SQL scripts"
        }

        val schemaPath = schemaSql.map { p =>
          val resolved = baseDir.resolve(p).getParent
          resolved -> s"$name schema SQL"
        }

        scriptPath.toList ++ schemaPath.toList
      } else {
        Nil
      }
    }

    paths.filter { case (p, _) => Files.exists(p) && Files.isDirectory(p) }.toMap
  }

  private def runInitialGeneration(configPath: String, sourceFilter: Option[String]): IO[Unit] = {
    IO.println("Running initial generation...") *>
      Generate.run(configPath, sourceFilter, quiet = false, debug = false).void
  }

  private def watchLoop(
      watchPaths: Map[Path, String],
      configPath: String,
      sourceFilter: Option[String]
  ): IO[Unit] = {
    if (watchPaths.isEmpty) {
      IO.println("No directories to watch. Exiting.")
    } else {
      IO.blocking {
        val watchService = FileSystems.getDefault.newWatchService()

        val registrations = watchPaths.map { case (path, desc) =>
          val key = path.register(
            watchService,
            ENTRY_CREATE,
            ENTRY_MODIFY,
            ENTRY_DELETE
          )
          key -> (path, desc)
        }.toMap

        try {
          var running = true

          Runtime.getRuntime.addShutdownHook(new Thread(() => {
            running = false
            watchService.close()
          }))

          while (running) {
            val key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS)

            if (key != null) {
              val events = key.pollEvents().asScala
              val (watchedPath, desc) = registrations.getOrElse(key, (Paths.get("."), "unknown"))

              val sqlChanges = events.filter { event =>
                val context = event.context()
                context != null && context.toString.endsWith(".sql")
              }

              if (sqlChanges.nonEmpty) {
                val changedFiles = sqlChanges.map { event =>
                  val filename = event.context().toString
                  val eventType = event.kind() match {
                    case ENTRY_CREATE => "created"
                    case ENTRY_MODIFY => "modified"
                    case ENTRY_DELETE => "deleted"
                    case _            => "changed"
                  }
                  s"$filename ($eventType)"
                }

                println(s"\n[${java.time.LocalTime.now}] Detected changes in $desc:")
                changedFiles.foreach(f => println(s"  - $f"))
                println("Regenerating...")

                try {
                  import scala.concurrent.Await
                  import scala.concurrent.duration.Duration
                  import cats.effect.unsafe.implicits.global

                  Generate.run(configPath, sourceFilter, quiet = true, debug = false).unsafeRunSync()
                  println("Regeneration complete!")
                } catch {
                  case e: Exception =>
                    println(s"Regeneration failed: ${e.getMessage}")
                }
              }

              key.reset()
            }
          }
        } finally {
          watchService.close()
        }
      }
    }
  }
}
