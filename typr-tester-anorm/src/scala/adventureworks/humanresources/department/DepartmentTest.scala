package adventureworks.humanresources.department

import adventureworks.customtypes.{Defaulted, TypoLocalDateTime}
import adventureworks.public.Name
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn

class DepartmentTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val departmentRepoI = new DepartmentRepoImpl

  test("works") {
    withConnection { implicit c =>
      // setup
      val unsaved = DepartmentRowUnsaved(
        name = Name("foo"),
        groupname = Name("bar"),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )

      // insert and round trip check
      val saved1 = departmentRepoI.insert(unsaved)
      val saved2 = unsaved.toRow(departmentidDefault = saved1.departmentid, modifieddateDefault = saved1.modifieddate)
      assert(saved1 === saved2): @nowarn

      // check field values
      val updatedOpt = departmentRepoI.update(saved1.copy(name = Name("baz")))
      assert(updatedOpt.isDefined): @nowarn
      assert(updatedOpt.get.name == Name("baz")): @nowarn

      val List(saved3) = departmentRepoI.selectAll: @unchecked
      assert(saved3.name == Name("baz")): @nowarn

      // delete
      departmentRepoI.deleteById(saved1.departmentid): @nowarn
      val List() = departmentRepoI.selectAll: @unchecked

      // done
      succeed
    }
  }

  test("upserts works") {
    withConnection { implicit c =>
      // setup
      val unsaved = DepartmentRowUnsaved(
        name = Name("foo"),
        groupname = Name("bar"),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )

      // insert and round trip check
      val saved1 = departmentRepoI.insert(unsaved)
      val newName = Name("baz")
      val saved2 = departmentRepoI.upsert(saved1.copy(name = newName))
      assert(saved2.name === newName): @nowarn

      // done
      succeed
    }
  }
}
