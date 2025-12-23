package adventureworks.production.product

import adventureworks.customtypes.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.Name
import adventureworks.{SnapshotTest, withConnection}
import org.scalatest.Assertion

import scala.annotation.nowarn

class RepoTest extends SnapshotTest {
  def upsertStreaming(unitmeasureRepo: UnitmeasureRepo): Assertion =
    withConnection { implicit c =>
      val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), TypoLocalDateTime.now)
      val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), TypoLocalDateTime.now)
      unitmeasureRepo.upsertStreaming(Iterator(um1, um2)): @nowarn
      assert(List(um1, um2) == unitmeasureRepo.selectAll.sortBy(_.name.value)): @nowarn
      val um1a = um1.copy(name = Name("name1a"))
      val um2a = um2.copy(name = Name("name2a"))
      unitmeasureRepo.upsertStreaming(Iterator(um1a, um2a)): @nowarn
      assert(List(um1a, um2a) == unitmeasureRepo.selectAll.sortBy(_.name.value))
    }

  def upsertBatch(unitmeasureRepo: UnitmeasureRepo): Assertion =
    withConnection { implicit c =>
      val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), TypoLocalDateTime.now)
      val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), TypoLocalDateTime.now)
      val initial = unitmeasureRepo.upsertBatch(List(um1, um2))
      assert(List(um1, um2) == initial.sortBy(_.name.value)): @nowarn
      val um1a = um1.copy(name = Name("name1a"))
      val um2a = um2.copy(name = Name("name2a"))
      val returned = unitmeasureRepo.upsertBatch(List(um1a, um2a))
      assert(List(um1a, um2a) == returned.sortBy(_.name.value)): @nowarn
      val all = unitmeasureRepo.selectAll
      assert(List(um1a, um2a) == all.sortBy(_.name.value))
    }

  test("upsertStreaming in-memory")(upsertStreaming(new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now))))
  test("upsertStreaming pg")(upsertStreaming(new UnitmeasureRepoImpl))

  test("upsertBatch in-memory")(upsertBatch(new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now))))
  test("upsertBatch pg")(upsertBatch(new UnitmeasureRepoImpl))
}
