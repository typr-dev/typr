package adventureworks.humanresources.department

import adventureworks.customtypes.{Defaulted, TypoLocalDateTime}
import adventureworks.public.Name
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import zio.ZIO
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
        _ <- ZIO.succeed(assert(saved1 === saved2))
        // check field values
        updatedOpt <- departmentRepo.update(saved1.copy(name = Name("baz")))
        _ <- ZIO.succeed {
          assert(updatedOpt.isDefined): @nowarn
          assert(updatedOpt.get.name == Name("baz"))
        }
        saved3 <- departmentRepo.selectAll.runLast
        _ <- ZIO.succeed(assert(saved3.map(_.name).contains(Name("baz"))))
        // delete
        _ <- departmentRepo.selectAll.runCollect.map(x => assert(x.size === 1))
        _ <- departmentRepo.deleteById(saved1.departmentid)
        _ <- departmentRepo.selectAll.runCollect.map(x => assert(x.isEmpty))
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
        inserted2 <- departmentRepo.upsert(saved1.copy(name = newName))
        _ <- ZIO.succeed(assert(inserted2.rowsUpdated === 1L))
        saved2 = inserted2.updatedKeys.head
        _ <- ZIO.succeed(assert(saved2.name === newName))
      } yield succeed
    }
  }
}
