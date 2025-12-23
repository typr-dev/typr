package adventureworks

import adventureworks.customtypes.*
import adventureworks.public.identity_test.*
import doobie.free.connection.delay
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class IdentityTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val repo = new IdentityTestRepoImpl()

  test("works") {
    withConnection {
      val unsaved = IdentityTestRowUnsaved(IdentityTestId("a"), Defaulted.UseDefault())
      for {
        inserted <- repo.insert(unsaved)
        upserted <- repo.upsert(inserted)
        _ <- delay(assert(inserted === upserted))
        rows <- repo.select.orderBy(_.name.asc).toList
        _ <- delay(rows === List(IdentityTestRow(1, 1, IdentityTestId("a"))))
      } yield ()
    }
  }
}
