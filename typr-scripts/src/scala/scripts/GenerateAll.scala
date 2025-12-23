package scripts

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object GenerateAll {
  def main(args: Array[String]): Unit = {
    println("Running all generation tasks in parallel...")

    val tasks = List(
      Future {
        println("Starting GeneratedSources...")
        GeneratedSources.main(Array.empty)
        println("GeneratedSources completed")
      },
      Future {
        println("Starting GeneratedAdventureWorks...")
        GeneratedAdventureWorks.main(Array.empty)
        println("GeneratedAdventureWorks completed")
      },
      Future {
        println("Starting GeneratedMariaDb...")
        GeneratedMariaDb.main(Array.empty)
        println("GeneratedMariaDb completed")
      }
    )

    val _ = Await.result(Future.sequence(tasks), Duration.Inf)
    println("All generation tasks completed successfully!")
  }
}
