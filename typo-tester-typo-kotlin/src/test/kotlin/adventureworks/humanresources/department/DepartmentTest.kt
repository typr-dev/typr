package adventureworks.humanresources.department

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import java.time.LocalDateTime
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test

class DepartmentTest {
    private val departmentRepoI = DepartmentRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // setup
            val unsaved = DepartmentRowUnsaved(
                name = Name("foo"),
                groupname = Name("bar"),
                modifieddate = Defaulted.Provided(DbNow.localDateTime())
            )

            // insert and round trip check
            val saved1 = departmentRepoI.insert(unsaved, c)
            val saved2 = unsaved.toRow(
                departmentidDefault = { saved1.departmentid },
                modifieddateDefault = { saved1.modifieddate }
            )
            assertEquals(saved1, saved2)

            // check field values
            val updated = departmentRepoI.update(saved1.copy(name = Name("baz")), c)
            assertTrue(updated)

            val allDepartments = departmentRepoI.selectAll(c)
            assertEquals(1, allDepartments.size)
            val saved3 = allDepartments[0]
            assertEquals(Name("baz"), saved3.name)

            // delete
            departmentRepoI.deleteById(saved1.departmentid, c)
            val emptyList = departmentRepoI.selectAll(c)
            assertTrue(emptyList.isEmpty())
        }
    }

    @Test
    fun upsertsWorks() {
        WithConnection.run { c ->
            // setup
            val unsaved = DepartmentRowUnsaved(
                name = Name("foo"),
                groupname = Name("bar"),
                modifieddate = Defaulted.Provided(DbNow.localDateTime())
            )

            // insert and round trip check
            val saved1 = departmentRepoI.insert(unsaved, c)
            val newName = Name("baz")
            val saved2 = departmentRepoI.upsert(saved1.copy(name = newName), c)
            assertEquals(newName, saved2.name)
        }
    }
}
