package adventureworks

import adventureworks.customtypes.*
import adventureworks.public.identity_test.*
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import zio.{Chunk, ZIO}

class IdentityTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val repo = new IdentityTestRepoImpl()

  test("works") {
    withConnection {
      val unsaved = IdentityTestRowUnsaved(IdentityTestId("a"), Defaulted.UseDefault())
      for {
        inserted <- repo.insert(unsaved)
        upserted <- repo.upsert(inserted)
        _ <- ZIO.succeed(assert(inserted === upserted.updatedKeys.head))
        rows <- repo.select.orderBy(_.name.asc).toChunk
        _ <- ZIO.succeed(rows === Chunk(IdentityTestRow(1, 1, IdentityTestId("a"))))
      } yield ()
    }
  }
}
