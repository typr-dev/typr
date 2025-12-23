package adventureworks.humanresources.department

import adventureworks.customtypes.{Defaulted, TypoLocalDateTime}
import adventureworks.public.Name
import adventureworks.withConnection
import doobie.free.connection.delay
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import scala.annotation.nowarn

class DepartmentTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val departmentRepo = new DepartmentRepoImpl

  test("works") {
    withConnection {
      // setup
      val unsaved = DepartmentRowUnsaved(
        name = Name("foo"),
        groupname = Name("bar"),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )
      for {
        // insert and round trip check
        saved1 <- departmentRepo.insert(unsaved)
        saved2 = unsaved.toRow(departmentidDefault = saved1.departmentid, modifieddateDefault = saved1.modifieddate)
        _ <- delay(assert(saved1 === saved2))
        // check field values
        updatedOpt <- departmentRepo.update(saved1.copy(name = Name("baz")))
        _ <- delay {
          assert(updatedOpt.isDefined): @nowarn
          assert(updatedOpt.get.name == Name("baz"))
        }
        saved3 <- departmentRepo.selectAll.compile.lastOrError
        _ <- delay(assert(saved3.name == Name("baz")))
        // delete
        _ <- departmentRepo.deleteById(saved1.departmentid)
        _ <- departmentRepo.selectAll.compile.toList.map(x => assert(x === List()))
      } yield succeed
    }
  }

  test("upserts works") {
    withConnection {
      // setup
      val unsaved = DepartmentRowUnsaved(
        name = Name("foo"),
        groupname = Name("bar"),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )
      for {
        // insert and round trip check
        saved1 <- departmentRepo.insert(unsaved)
        newName = Name("baz")
        saved2 <- departmentRepo.upsert(saved1.copy(name = newName))
        _ <- delay(assert(saved2.name === newName))
      } yield succeed
    }
  }
}
