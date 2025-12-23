package adventureworks

import adventureworks.public.only_pk_columns.*
import org.junit.Assert.*
import org.junit.Test

class UpsertTwiceTest {
  private val onlyPkColumnsRepo = new OnlyPkColumnsRepoImpl

  @Test
  def secondUpsertShouldNotError(): Unit = {
    val row = OnlyPkColumnsRow("the answer is", 42)
    WithConnection {
      assertEquals(onlyPkColumnsRepo.upsert(row), onlyPkColumnsRepo.upsert(row))
    }
  }
}
