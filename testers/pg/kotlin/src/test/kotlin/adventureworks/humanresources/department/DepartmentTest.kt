package adventureworks.humanresources.department

import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class DepartmentTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val departmentRepo = DepartmentRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // insert using TestInsert with defaults
            val saved1 = testInsert.humanresourcesDepartment(
                name = Name("foo"),
                groupname = Name("bar"),
                c = c
            )

            // check field values
            val updated = departmentRepo.update(saved1.copy(name = Name("baz")), c)
            assertTrue(updated)

            val allDepartments = departmentRepo.selectAll(c)
            assertEquals(1, allDepartments.size)
            val saved3 = allDepartments[0]
            assertEquals(Name("baz"), saved3.name)

            // delete
            departmentRepo.deleteById(saved1.departmentid, c)
            val emptyList = departmentRepo.selectAll(c)
            assertTrue(emptyList.isEmpty())
        }
    }

    @Test
    fun upsertsWorks() {
        WithConnection.run { c ->
            // insert using TestInsert
            val saved1 = testInsert.humanresourcesDepartment(
                name = Name("foo"),
                groupname = Name("bar"),
                c = c
            )

            // upsert with new name
            val newName = Name("baz")
            val saved2 = departmentRepo.upsert(saved1.copy(name = newName), c)
            assertEquals(newName, saved2.name)
        }
    }
}
