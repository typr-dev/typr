package adventureworks.humanresources.department

import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.public.Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DepartmentTest {
    private val departmentRepo = DepartmentRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // setup
            val unsaved = DepartmentRowUnsaved(
                name = Name("foo"),
                groupname = Name("bar"),
                modifieddate = Defaulted.Provided(TypoLocalDateTime.now())
            )

            // insert and round trip check
            val saved1 = departmentRepo.insert(unsaved, c)
            val saved2 = unsaved.toRow(
                { saved1.departmentid },
                { saved1.modifieddate }
            )
            assertEquals(saved1, saved2)

            // check field values
            val updatedOpt = departmentRepo.update(saved1.copy(name = Name("baz")), c)
            assertTrue(updatedOpt)

            val all = departmentRepo.selectAll(c)
            assertEquals(1, all.size)
            assertEquals(Name("baz"), all[0].name)

            // delete
            departmentRepo.deleteById(saved1.departmentid, c)
            val afterDelete = departmentRepo.selectAll(c)
            assertTrue(afterDelete.isEmpty())
        }
    }

    @Test
    fun upsertsWorks() {
        WithConnection.run { c ->
            // setup
            val unsaved = DepartmentRowUnsaved(
                name = Name("foo"),
                groupname = Name("bar"),
                modifieddate = Defaulted.Provided(TypoLocalDateTime.now())
            )

            // insert and verify upsert
            val saved1 = departmentRepo.insert(unsaved, c)
            val newName = Name("baz")
            val saved2 = departmentRepo.upsert(saved1.copy(name = newName), c)
            assertEquals(newName, saved2.name)

            // Verify only one row exists
            val all = departmentRepo.selectAll(c)
            assertEquals(1, all.size)
            assertEquals(newName, all[0].name)
        }
    }
}
