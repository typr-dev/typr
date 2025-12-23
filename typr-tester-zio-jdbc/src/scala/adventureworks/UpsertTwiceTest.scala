package adventureworks

import adventureworks.public.only_pk_columns.{OnlyPkColumnsRepoImpl, OnlyPkColumnsRow}
import org.scalatest.funsuite.AnyFunSuite

class UpsertTwiceTest extends AnyFunSuite {
  val onlyPkColumnsRepo = new OnlyPkColumnsRepoImpl

  test("second upsert should not error") {
    val row = OnlyPkColumnsRow("the answer is", 42)
    val (first, second) = withConnection {
      for {
        first <- onlyPkColumnsRepo.upsert(row)
        second <- onlyPkColumnsRepo.upsert(row)
      } yield (first, second)
    }
    assert(first == second)
  }
}
