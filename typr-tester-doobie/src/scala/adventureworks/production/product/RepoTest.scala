package adventureworks.production.product

import adventureworks.customtypes.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.Name
import adventureworks.{SnapshotTest, withConnection}
import org.scalatest.Assertion
import doobie.free.connection.delay

class RepoTest extends SnapshotTest {
  def upsertStreaming(unitmeasureRepo: UnitmeasureRepo): Assertion =
    withConnection {
      val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), TypoLocalDateTime.now)
      val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), TypoLocalDateTime.now)
      for {
        _ <- unitmeasureRepo.upsertStreaming(fs2.Stream(um1, um2))
        _ <- unitmeasureRepo.selectAll.compile.toList.map(all => assert(List(um1, um2) == all.sortBy(_.name.value)))
        um1a = um1.copy(name = Name("name1a"))
        um2a = um2.copy(name = Name("name2a"))
        _ <- unitmeasureRepo.upsertStreaming(fs2.Stream(um1a, um2a))
        all <- unitmeasureRepo.selectAll.compile.toList
      } yield assert(List(um1a, um2a) == all.sortBy(_.name.value))
    }

  def upsertBatch(unitmeasureRepo: UnitmeasureRepo): Assertion =
    withConnection {
      val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), TypoLocalDateTime.now)
      val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), TypoLocalDateTime.now)
      for {
        initial <- unitmeasureRepo.upsertBatch(List(um1, um2)).compile.toList
        _ <- delay(assert(List(um1, um2) == initial.sortBy(_.name.value)))
        um1a = um1.copy(name = Name("name1a"))
        um2a = um2.copy(name = Name("name2a"))
        returned <- unitmeasureRepo.upsertBatch(List(um1a, um2a)).compile.toList
        _ <- delay(assert(List(um1a, um2a) == returned.sortBy(_.name.value)))
        all <- unitmeasureRepo.selectAll.compile.toList
      } yield assert(List(um1a, um2a) == all.sortBy(_.name.value))
    }

  test("upsertStreaming in-memory")(upsertStreaming(new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now))))
  test("upsertStreaming pg")(upsertStreaming(new UnitmeasureRepoImpl))

  test("upsertBatch in-memory")(upsertBatch(new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now))))
  test("upsertBatch pg")(upsertBatch(new UnitmeasureRepoImpl))
}
