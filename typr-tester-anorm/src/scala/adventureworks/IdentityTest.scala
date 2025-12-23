package adventureworks

import adventureworks.customtypes.*
import adventureworks.public.identity_test.*
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn

class IdentityTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val repo = new IdentityTestRepoImpl()

  test("works") {
    withConnection { implicit c =>
      val unsaved = IdentityTestRowUnsaved(IdentityTestId("a"), Defaulted.UseDefault())
      val inserted = repo.insert(unsaved)
      val upserted = repo.upsert(inserted)
      assert(inserted === upserted): @nowarn
      repo.select.orderBy(_.name.asc).toList === List(
        IdentityTestRow(1, 1, IdentityTestId("a"))
      )
    }
  }
}
