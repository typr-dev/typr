package scripts

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object GenerateAll {
  def main(args: Array[String]): Unit = {
    println("Running all generation tasks in parallel...")

    val tasks = List(
      Future {
        println("Starting GeneratedAdventureWorks...")
        GeneratedAdventureWorks.main(Array.empty)
        println("GeneratedAdventureWorks completed")
      },
      Future {
        println("Starting GeneratedMariaDb...")
        GeneratedMariaDb.main(Array.empty)
        println("GeneratedMariaDb completed")
      },
      Future {
        println("Starting GeneratedOracle...")
        GeneratedOracle.main(Array.empty)
        println("GeneratedOracle completed")
      },
      Future {
        println("Starting GeneratedDuckDb...")
        GeneratedDuckDb.main(Array.empty)
        println("GeneratedDuckDb completed")
      },
      Future {
        println("Starting GeneratedSqlServer...")
        GeneratedSqlServer.main(Array.empty)
        println("GeneratedSqlServer completed")
      }
    )

    val _ = Await.result(Future.sequence(tasks), Duration.Inf)
    println("All generation tasks completed successfully!")
  }
}
