package adventureworks.humanresources.department

import adventureworks.{DbNow, WithConnection}
import adventureworks.customtypes.Defaulted
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test

class DepartmentTest {
  private val departmentRepo = new DepartmentRepoImpl

  @Test
  def works(): Unit = {
    WithConnection {
      val unsaved = DepartmentRowUnsaved(name = Name("foo"), groupname = Name("bar"))
        .copy(modifieddate = Defaulted.Provided(DbNow.localDateTime()))

      val saved1 = departmentRepo.insert(unsaved)
      val saved2 = unsaved.toRow(saved1.departmentid, saved1.modifieddate)
      assertEquals(saved1, saved2)

      val _ = departmentRepo.update(saved1.copy(name = Name("baz")))
      val all = departmentRepo.selectAll
      assertEquals(1, all.size)
      assertEquals(Name("baz"), all.get(0).name)

      val _ = departmentRepo.deleteById(saved1.departmentid)
      val afterDelete = departmentRepo.selectAll
      assertTrue(afterDelete.isEmpty)
    }
  }

  @Test
  def upsertsWorks(): Unit = {
    WithConnection {
      val unsaved = DepartmentRowUnsaved(name = Name("foo"), groupname = Name("bar"))
        .copy(modifieddate = Defaulted.Provided(DbNow.localDateTime()))

      val saved1 = departmentRepo.insert(unsaved)
      val newName = Name("baz")
      val saved2 = departmentRepo.upsert(saved1.copy(name = newName))
      assertEquals(newName, saved2.name)

      val all = departmentRepo.selectAll
      assertEquals(1, all.size)
      assertEquals(newName, all.get(0).name)
    }
  }
}
