package adventureworks.production.product

import adventureworks.customtypes.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.Name
import adventureworks.{SnapshotTest, withConnection}
import org.scalatest.Assertion
import zio.Chunk
import zio.stream.ZStream

class RepoTest extends SnapshotTest {
  def runTest(unitmeasureRepo: UnitmeasureRepo): Assertion =
    withConnection {
      val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), TypoLocalDateTime.now)
      val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), TypoLocalDateTime.now)
      for {
        _ <- unitmeasureRepo.upsertStreaming(ZStream(um1, um2))
        _ <- unitmeasureRepo.selectAll.runCollect.map(all => assert(List(um1, um2) == all.sortBy(_.name.value)))
        um1a = um1.copy(name = Name("name1a"))
        um2a = um2.copy(name = Name("name2a"))
        _ <- unitmeasureRepo.upsertStreaming(ZStream(um1a, um2a))
        all <- unitmeasureRepo.selectAll.runCollect
      } yield assert(Chunk(um1a, um2a) == all.sortBy(_.name.value))
    }

  test("in-memory") {
    runTest(new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now)))
  }

  test("pg") {
    runTest(new UnitmeasureRepoImpl)
  }
}
