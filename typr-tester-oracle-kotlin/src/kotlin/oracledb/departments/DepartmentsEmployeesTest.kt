package oracledb.departments

import oracledb.MoneyT
import oracledb.OracleTestHelper
import oracledb.customtypes.Defaulted
import oracledb.employees.EmployeesId
import oracledb.employees.EmployeesRepoImpl
import oracledb.employees.EmployeesRowUnsaved
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DepartmentsEmployeesTest {
    private val departmentsRepo = DepartmentsRepoImpl()
    private val employeesRepo = EmployeesRepoImpl()

    @Test
    fun testInsertDepartmentWithCompositePK() {
        OracleTestHelper.run { c ->
            val budget = MoneyT(BigDecimal("1000000.01"), "USD")
            val dept = DepartmentsRow("IT", "WEST", "Information Technology", budget)

            val insertedId = departmentsRepo.insert(dept, c)

            assertNotNull(insertedId)
            val inserted = departmentsRepo.selectById(insertedId, c)!!
            assertEquals("IT", inserted.deptCode)
            assertEquals("WEST", inserted.deptRegion)
            assertEquals("Information Technology", inserted.deptName)
            assertNotNull(inserted.budget)
            assertEquals(BigDecimal("1000000.01"), inserted.budget!!.amount)
            assertEquals("USD", inserted.budget!!.currency)
        }
    }

    @Test
    fun testSelectDepartmentByCompositeId() {
        OracleTestHelper.run { c ->
            val dept = DepartmentsRow("HR", "EAST", "Human Resources", null)
            departmentsRepo.insert(dept, c)

            val id = DepartmentsId("HR", "EAST")
            val found = departmentsRepo.selectById(id, c)

            assertNotNull(found)
            assertEquals("HR", found!!.deptCode)
            assertEquals("EAST", found.deptRegion)
            assertEquals("Human Resources", found.deptName)
        }
    }

    @Test
    fun testSelectDepartmentsByCompositeIds() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("SALES", "NORTH", "Sales North", null), c)
            departmentsRepo.insert(DepartmentsRow("SALES", "SOUTH", "Sales South", null), c)
            departmentsRepo.insert(DepartmentsRow("FINANCE", "CENTRAL", "Finance", null), c)

            val ids = arrayOf(
                DepartmentsId("SALES", "NORTH"),
                DepartmentsId("SALES", "SOUTH"),
                DepartmentsId("NONEXISTENT", "NONE")
            )
            val found = departmentsRepo.selectByIds(ids, c)

            assertEquals(2, found.size)
            assertTrue(found.any { it.deptCode == "SALES" && it.deptRegion == "NORTH" })
            assertTrue(found.any { it.deptCode == "SALES" && it.deptRegion == "SOUTH" })
        }
    }

    @Test
    fun testSelectDepartmentsByCompositeIdsTracked() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("DEV", "WEST", "Development West", null), c)
            departmentsRepo.insert(DepartmentsRow("DEV", "EAST", "Development East", null), c)

            val ids = arrayOf(
                DepartmentsId("DEV", "WEST"),
                DepartmentsId("DEV", "EAST"),
                DepartmentsId("MISSING", "NONE")
            )
            val tracked = departmentsRepo.selectByIdsTracked(ids, c)

            assertEquals(2, tracked.size)
            assertTrue(tracked.containsKey(DepartmentsId("DEV", "WEST")))
            assertTrue(tracked.containsKey(DepartmentsId("DEV", "EAST")))
            assertFalse(tracked.containsKey(DepartmentsId("MISSING", "NONE")))
        }
    }

    @Test
    fun testUpdateDepartment() {
        OracleTestHelper.run { c ->
            val dept = DepartmentsRow("OPS", "CENTRAL", "Operations", MoneyT(BigDecimal("500000.00"), "USD"))
            val insertedId = departmentsRepo.insert(dept, c)
            val inserted = departmentsRepo.selectById(insertedId, c)!!

            val updated = inserted.copy(budget = MoneyT(BigDecimal("750000.01"), "USD"))
            val wasUpdated = departmentsRepo.update(updated, c)

            assertTrue(wasUpdated)

            val found = departmentsRepo.selectById(DepartmentsId("OPS", "CENTRAL"), c)
            assertNotNull(found)
            assertNotNull(found!!.budget)
            assertEquals(BigDecimal("750000.01"), found.budget!!.amount)
        }
    }

    @Test
    fun testDeleteDepartmentByCompositeId() {
        OracleTestHelper.run { c ->
            val dept = DepartmentsRow("TEMP", "REGION1", "Temporary Dept", null)
            departmentsRepo.insert(dept, c)

            val id = DepartmentsId("TEMP", "REGION1")
            val deleted = departmentsRepo.deleteById(id, c)

            assertTrue(deleted)

            val found = departmentsRepo.selectById(id, c)
            assertNull(found)
        }
    }

    @Test
    fun testDeleteDepartmentsByCompositeIds() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("DEL1", "R1", "Delete 1", null), c)
            departmentsRepo.insert(DepartmentsRow("DEL1", "R2", "Delete 2", null), c)
            departmentsRepo.insert(DepartmentsRow("DEL2", "R1", "Delete 3", null), c)

            val ids = arrayOf(DepartmentsId("DEL1", "R1"), DepartmentsId("DEL1", "R2"))
            val deletedCount = departmentsRepo.deleteByIds(ids, c)

            assertEquals(2, deletedCount)

            assertNull(departmentsRepo.selectById(DepartmentsId("DEL1", "R1"), c))
            assertNull(departmentsRepo.selectById(DepartmentsId("DEL1", "R2"), c))
            assertNotNull(departmentsRepo.selectById(DepartmentsId("DEL2", "R1"), c))
        }
    }

    @Test
    fun testInsertEmployeeWithForeignKeyToDepartment() {
        OracleTestHelper.run { c ->
            val dept = DepartmentsRow("ENG", "WEST", "Engineering West", MoneyT(BigDecimal("2000000.00"), "USD"))
            departmentsRepo.insert(dept, c)

            val empUnsaved = EmployeesRowUnsaved(
                BigDecimal("12345"),
                "JR",
                "ENG",
                "WEST",
                "John Smith",
                MoneyT(BigDecimal("75000.00"), "USD"),
                Defaulted.UseDefault()
            )

            val insertedId = employeesRepo.insert(empUnsaved, c)

            assertNotNull(insertedId)
            val inserted = employeesRepo.selectById(insertedId, c)!!
            assertEquals(BigDecimal("12345"), inserted.empNumber)
            assertEquals("JR", inserted.empSuffix)
            assertEquals("ENG", inserted.deptCode)
            assertEquals("WEST", inserted.deptRegion)
            assertEquals("John Smith", inserted.empName)
            assertNotNull(inserted.hireDate)
        }
    }

    @Test
    fun testSelectEmployeeByCompositeId() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("ACCT", "SOUTH", "Accounting", null), c)

            val empUnsaved = EmployeesRowUnsaved(
                BigDecimal("98765"),
                "SR",
                "ACCT",
                "SOUTH",
                "Jane Doe",
                MoneyT(BigDecimal("95000.00"), "USD"),
                Defaulted.UseDefault()
            )
            employeesRepo.insert(empUnsaved, c)

            val empId = EmployeesId(BigDecimal("98765"), "SR")
            val found = employeesRepo.selectById(empId, c)

            assertNotNull(found)
            assertEquals(BigDecimal("98765"), found!!.empNumber)
            assertEquals("SR", found.empSuffix)
            assertEquals("ACCT", found.deptCode)
            assertEquals("SOUTH", found.deptRegion)
        }
    }

    @Test
    fun testMultipleEmployeesInSameDepartment() {
        OracleTestHelper.run { c ->
            val ts = System.currentTimeMillis() % 100000000
            val empNum1 = BigDecimal(ts)
            val empNum2 = BigDecimal(ts + 1)
            val empNum3 = BigDecimal(ts + 2)
            val deptCode = "D${ts % 10000}"
            val deptRegion = "R${ts % 1000}"

            departmentsRepo.insert(DepartmentsRow(deptCode, deptRegion, "Production", null), c)

            employeesRepo.insert(EmployeesRowUnsaved(empNum1, "A", deptCode, deptRegion, "Employee A", MoneyT(BigDecimal("60000.01"), "USD"), Defaulted.UseDefault()), c)
            employeesRepo.insert(EmployeesRowUnsaved(empNum2, "B", deptCode, deptRegion, "Employee B", MoneyT(BigDecimal("65000.01"), "USD"), Defaulted.UseDefault()), c)
            employeesRepo.insert(EmployeesRowUnsaved(empNum3, "C", deptCode, deptRegion, "Employee C", MoneyT(BigDecimal("70000.01"), "USD"), Defaulted.UseDefault()), c)

            val emp1 = employeesRepo.selectById(EmployeesId(empNum1, "A"), c)!!
            val emp2 = employeesRepo.selectById(EmployeesId(empNum2, "B"), c)!!
            val emp3 = employeesRepo.selectById(EmployeesId(empNum3, "C"), c)!!

            assertEquals(deptCode, emp1.deptCode)
            assertEquals(deptRegion, emp1.deptRegion)
            assertEquals(deptCode, emp2.deptCode)
            assertEquals(deptRegion, emp2.deptRegion)
            assertEquals(deptCode, emp3.deptCode)
            assertEquals(deptRegion, emp3.deptRegion)
        }
    }

    @Test
    fun testUpdateEmployeeDepartment() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("DEPT1", "LOC1", "Department 1", null), c)
            departmentsRepo.insert(DepartmentsRow("DEPT2", "LOC2", "Department 2", null), c)

            val empId = employeesRepo.insert(EmployeesRowUnsaved(BigDecimal("5000"), "X", "DEPT1", "LOC1", "Transferable Employee", MoneyT(BigDecimal("80000.00"), "USD"), Defaulted.UseDefault()), c)
            val emp = employeesRepo.selectById(empId, c)!!

            val transferred = emp.copy(deptCode = "DEPT2", deptRegion = "LOC2")
            val updated = employeesRepo.update(transferred, c)

            assertTrue(updated)

            val found = employeesRepo.selectById(EmployeesId(BigDecimal("5000"), "X"), c)
            assertNotNull(found)
            assertEquals("DEPT2", found!!.deptCode)
            assertEquals("LOC2", found.deptRegion)
        }
    }

    @Test
    fun testDeleteEmployeeByCompositeId() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("TEST", "TEST", "Test Dept", null), c)
            employeesRepo.insert(EmployeesRowUnsaved(BigDecimal("9999"), "Z", "TEST", "TEST", "Delete Me", null, Defaulted.UseDefault()), c)

            val empId = EmployeesId(BigDecimal("9999"), "Z")
            val deleted = employeesRepo.deleteById(empId, c)

            assertTrue(deleted)

            val found = employeesRepo.selectById(empId, c)
            assertNull(found)
        }
    }

    @Test
    fun testSelectAllDepartments() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("ALL1", "A", "All Test 1", null), c)
            departmentsRepo.insert(DepartmentsRow("ALL2", "B", "All Test 2", null), c)
            departmentsRepo.insert(DepartmentsRow("ALL3", "C", "All Test 3", null), c)

            val all = departmentsRepo.selectAll(c)

            assertTrue(all.size >= 3)
            assertTrue(all.any { it.deptCode == "ALL1" && it.deptRegion == "A" })
            assertTrue(all.any { it.deptCode == "ALL2" && it.deptRegion == "B" })
            assertTrue(all.any { it.deptCode == "ALL3" && it.deptRegion == "C" })
        }
    }

    @Test
    fun testSelectAllEmployees() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("ALLEMP", "REGION", "All Employees Test", null), c)

            employeesRepo.insert(EmployeesRowUnsaved(BigDecimal("101"), "A", "ALLEMP", "REGION", "Emp 1", null, Defaulted.UseDefault()), c)
            employeesRepo.insert(EmployeesRowUnsaved(BigDecimal("102"), "B", "ALLEMP", "REGION", "Emp 2", null, Defaulted.UseDefault()), c)

            val all = employeesRepo.selectAll(c)

            assertTrue(all.size >= 2)
            assertTrue(all.any { it.empNumber == BigDecimal("101") && it.empSuffix == "A" })
            assertTrue(all.any { it.empNumber == BigDecimal("102") && it.empSuffix == "B" })
        }
    }

    @Test
    fun testDepartmentWithNullBudget() {
        OracleTestHelper.run { c ->
            val dept = DepartmentsRow("NOBUD", "REGION", "No Budget Dept", null)
            val insertedId = departmentsRepo.insert(dept, c)
            val inserted = departmentsRepo.selectById(insertedId, c)!!

            assertNull(inserted.budget)

            val found = departmentsRepo.selectById(DepartmentsId("NOBUD", "REGION"), c)
            assertNotNull(found)
            assertNull(found!!.budget)
        }
    }

    @Test
    fun testEmployeeWithNullSalary() {
        OracleTestHelper.run { c ->
            departmentsRepo.insert(DepartmentsRow("NOSAL", "REGION", "No Salary Dept", null), c)

            val empId = employeesRepo.insert(EmployeesRowUnsaved(BigDecimal("777"), "N", "NOSAL", "REGION", "No Salary Employee", null, Defaulted.UseDefault()), c)
            val emp = employeesRepo.selectById(empId, c)!!

            assertNull(emp.salary)

            val found = employeesRepo.selectById(EmployeesId(BigDecimal("777"), "N"), c)
            assertNotNull(found)
            assertNull(found!!.salary)
        }
    }
}
