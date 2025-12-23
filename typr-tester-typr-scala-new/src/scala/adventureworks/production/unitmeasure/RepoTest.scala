package adventureworks.production.unitmeasure

import adventureworks.{DbNow, WithConnection}
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test

class RepoTest {

  private def upsertStreaming(unitmeasureRepo: UnitmeasureRepo): Unit = {
    WithConnection {
      val um1 = UnitmeasureRow(UnitmeasureId("kg1"), Name("name1"), DbNow.localDateTime())
      val um2 = UnitmeasureRow(UnitmeasureId("kg2"), Name("name2"), DbNow.localDateTime())
      val _ = unitmeasureRepo.upsertStreaming(List(um1, um2).iterator, 1000)

      val all1 = unitmeasureRepo.selectAll.sortBy(_.name.value)
      assertEquals(List(um1, um2), all1)

      val um1a = um1.copy(name = Name("name1a"))
      val um2a = um2.copy(name = Name("name2a"))
      val _ = unitmeasureRepo.upsertStreaming(List(um1a, um2a).iterator, 1000)

      val all2 = unitmeasureRepo.selectAll.sortBy(_.name.value)
      assertEquals(List(um1a, um2a), all2)
    }
  }

  private def upsertBatch(unitmeasureRepo: UnitmeasureRepo): Unit = {
    WithConnection {
      val um1 = UnitmeasureRow(UnitmeasureId("kg1"), Name("name1"), DbNow.localDateTime())
      val um2 = UnitmeasureRow(UnitmeasureId("kg2"), Name("name2"), DbNow.localDateTime())
      val initial = unitmeasureRepo.upsertBatch(List(um1, um2).iterator).sortBy(_.name.value)
      assertEquals(List(um1, um2), initial)

      val um1a = um1.copy(name = Name("name1a"))
      val um2a = um2.copy(name = Name("name2a"))
      val returned = unitmeasureRepo.upsertBatch(List(um1a, um2a).iterator).sortBy(_.name.value)
      assertEquals(List(um1a, um2a), returned)

      val all = unitmeasureRepo.selectAll.sortBy(_.name.value)
      assertEquals(List(um1a, um2a), all)
    }
  }

  @Test
  def upsertStreamingInMemory(): Unit = {
    upsertStreaming(new UnitmeasureRepoMock(unsaved => unsaved.toRow(DbNow.localDateTime())))
  }

  @Test
  def upsertStreamingPg(): Unit = {
    upsertStreaming(new UnitmeasureRepoImpl)
  }

  @Test
  def upsertBatchInMemory(): Unit = {
    upsertBatch(new UnitmeasureRepoMock(unsaved => unsaved.toRow(DbNow.localDateTime())))
  }

  @Test
  def upsertBatchPg(): Unit = {
    upsertBatch(new UnitmeasureRepoImpl)
  }
}
