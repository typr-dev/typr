package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.departments.*
import testdb.employees.*
import java.math.BigDecimal
import java.time.LocalDate

class CompositeKeyTest {
    private val departments = DepartmentsRepoImpl()
    private val employees = EmployeesRepoImpl()

    @Test
    fun departmentsInsert() {
        SqliteTestHelper.run { c ->
            val d = DepartmentsRow("ENG", "EU-NORTH", "Engineering EU", BigDecimal("750000"))
            val inserted = departments.insert(d, c)
            assertEquals("Engineering EU", inserted.deptName)
        }
    }

    @Test
    fun departmentsSelectBySeedId() {
        SqliteTestHelper.run { c ->
            val found = departments.selectById(DepartmentsId("IT", "US-WEST"), c)
            assertNotNull(found)
            assertEquals("Information Technology", found!!.deptName)
        }
    }

    @Test
    fun departmentsCompositeIdFromRow() {
        SqliteTestHelper.run { c ->
            val inserted = departments.insert(
                DepartmentsRow("SALES", "APAC", "Sales APAC", BigDecimal("500000")), c
            )
            val id = inserted.compositeId()
            assertEquals("SALES", id.deptCode)
            assertEquals(inserted, departments.selectById(id, c))
        }
    }

    @Test
    fun departmentsUpdate() {
        SqliteTestHelper.run { c ->
            val inserted = departments.insert(
                DepartmentsRow("FINANCE", "US-CENTRAL", "Finance", BigDecimal("800000")), c
            )
            assertTrue(departments.update(inserted.copy(budget = BigDecimal("900000")), c))
            assertEquals(
                0,
                BigDecimal("900000").compareTo(departments.selectById(inserted.compositeId(), c)!!.budget!!)
            )
        }
    }

    @Test
    fun departmentsDelete() {
        SqliteTestHelper.run { c ->
            val inserted = departments.insert(DepartmentsRow("TEMP", "TEMP-REGION", "Temporary", null), c)
            assertTrue(departments.deleteById(inserted.compositeId(), c))
            assertNull(departments.selectById(inserted.compositeId(), c))
        }
    }

    @Test
    fun departmentsMultipleSameCode() {
        SqliteTestHelper.run { c ->
            departments.insert(DepartmentsRow("ENG2", "US", "Eng US", null), c)
            departments.insert(DepartmentsRow("ENG2", "EU", "Eng EU", null), c)
            departments.insert(DepartmentsRow("ENG2", "APAC", "Eng APAC", null), c)
            assertNotNull(departments.selectById(DepartmentsId("ENG2", "US"), c))
            assertNotNull(departments.selectById(DepartmentsId("ENG2", "EU"), c))
            assertNotNull(departments.selectById(DepartmentsId("ENG2", "APAC"), c))
        }
    }

    @Test
    fun employeesInsert() {
        SqliteTestHelper.run { c ->
            val emp = EmployeesRow(2001L, "A", "IT", "US-WEST", "Alice", BigDecimal("95000"), LocalDate.of(2025, 1, 15))
            val inserted = employees.insert(emp, c)
            assertEquals(2001L, inserted.empNumber)
        }
    }

    @Test
    fun employeesSelectBySeedId() {
        SqliteTestHelper.run { c ->
            val found = employees.selectById(EmployeesId(1001L, "A"), c)
            assertNotNull(found)
            assertEquals("Alice Johnson", found!!.empName)
        }
    }

    @Test
    fun employeesCompositeIdFromRow() {
        SqliteTestHelper.run { c ->
            val inserted = employees.insert(
                EmployeesRow(3001L, "C", "HR", "US-EAST", "Carol", null, LocalDate.of(2025, 3, 1)), c
            )
            assertEquals(3001L, inserted.compositeId().empNumber)
            assertEquals("C", inserted.compositeId().empSuffix)
        }
    }

    @Test
    fun employeesUpdate() {
        SqliteTestHelper.run { c ->
            val inserted = employees.insert(
                EmployeesRow(4001L, "D", "IT", "US-WEST", "David", BigDecimal("120000"), LocalDate.of(2025, 4, 1)), c
            )
            employees.update(inserted.copy(salary = BigDecimal("130000")), c)
            assertEquals(
                0,
                BigDecimal("130000").compareTo(employees.selectById(inserted.compositeId(), c)!!.salary!!)
            )
        }
    }

    @Test
    fun employeesDelete() {
        SqliteTestHelper.run { c ->
            val inserted = employees.insert(
                EmployeesRow(5001L, "X", "IT", "US-WEST", "Disposable", null, LocalDate.of(2025, 5, 1)), c
            )
            assertTrue(employees.deleteById(inserted.compositeId(), c))
        }
    }

    @Test
    fun employeesMultipleSameNumber() {
        SqliteTestHelper.run { c ->
            employees.insert(EmployeesRow(9999L, "A", "IT", "US-WEST", "A", null, LocalDate.of(2025, 1, 1)), c)
            employees.insert(EmployeesRow(9999L, "B", "IT", "US-WEST", "B", null, LocalDate.of(2025, 1, 1)), c)
            employees.insert(EmployeesRow(9999L, "C", "IT", "US-WEST", "C", null, LocalDate.of(2025, 1, 1)), c)
            assertNotNull(employees.selectById(EmployeesId(9999L, "A"), c))
            assertNotNull(employees.selectById(EmployeesId(9999L, "B"), c))
            assertNotNull(employees.selectById(EmployeesId(9999L, "C"), c))
        }
    }

    @Test
    fun employeeDepartmentForeignKey() {
        SqliteTestHelper.run { c ->
            departments.insert(DepartmentsRow("FK_TEST", "FK_REGION", "FK Dept", BigDecimal("500000")), c)
            val emp = employees.insert(
                EmployeesRow(6001L, "F", "FK_TEST", "FK_REGION", "FK Emp", BigDecimal("60000"), LocalDate.of(2025, 6, 1)), c
            )
            assertEquals("FK_TEST", emp.deptCode)
            assertNotNull(departments.selectById(DepartmentsId(emp.deptCode, emp.deptRegion), c))
        }
    }
}
