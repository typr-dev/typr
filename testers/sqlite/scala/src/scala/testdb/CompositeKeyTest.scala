package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.departments.*
import testdb.employees.*

import java.time.LocalDate

class CompositeKeyTest {
  private val departments = new DepartmentsRepoImpl()
  private val employees = new EmployeesRepoImpl()

  @Test def departmentsInsertAndSelect(): Unit = withConnection {
    val d = DepartmentsRow("ENG", "EU-NORTH", "Engineering EU", Some(BigDecimal("750000")))
    val inserted = departments.insert(d)
    assertEquals("Engineering EU", inserted.deptName)
    assertEquals(0, BigDecimal("750000").compare(inserted.budget.get))
  }

  @Test def departmentsSelectBySeedId(): Unit = withConnection {
    val found = departments.selectById(DepartmentsId("IT", "US-WEST"))
    assertTrue(found.isDefined)
    assertEquals("Information Technology", found.get.deptName)
  }

  @Test def departmentsCompositeIdFromRow(): Unit = withConnection {
    val inserted = departments.insert(DepartmentsRow("SALES", "APAC", "Sales APAC", Some(BigDecimal("500000"))))
    val id = inserted.compositeId
    assertEquals("SALES", id.deptCode)
    assertEquals(inserted, departments.selectById(id).get)
  }

  @Test def departmentsUpdate(): Unit = withConnection {
    val inserted = departments.insert(DepartmentsRow("FINANCE", "US-CENTRAL", "Finance", Some(BigDecimal("800000"))))
    val updated = inserted.copy(budget = Some(BigDecimal("900000")))
    assertTrue(departments.update(updated))
    assertEquals(0, BigDecimal("900000").compare(departments.selectById(inserted.compositeId).get.budget.get))
  }

  @Test def departmentsDelete(): Unit = withConnection {
    val inserted = departments.insert(DepartmentsRow("TEMP", "TEMP-REGION", "Temporary", None))
    assertTrue(departments.deleteById(inserted.compositeId))
    assertTrue(departments.selectById(inserted.compositeId).isEmpty)
  }

  @Test def departmentsMultipleSameCode(): Unit = withConnection {
    departments.insert(DepartmentsRow("ENG2", "US", "Eng US", None))
    departments.insert(DepartmentsRow("ENG2", "EU", "Eng EU", None))
    departments.insert(DepartmentsRow("ENG2", "APAC", "Eng APAC", None))
    assertTrue(departments.selectById(DepartmentsId("ENG2", "US")).isDefined)
    assertTrue(departments.selectById(DepartmentsId("ENG2", "EU")).isDefined)
    assertTrue(departments.selectById(DepartmentsId("ENG2", "APAC")).isDefined)
  }

  @Test def employeesInsert(): Unit = withConnection {
    val emp = EmployeesRow(2001L, "A", "IT", "US-WEST", "Alice", Some(BigDecimal("95000")), LocalDate.of(2025, 1, 15))
    val inserted = employees.insert(emp)
    assertEquals(2001L, inserted.empNumber)
    assertEquals("Alice", inserted.empName)
  }

  @Test def employeesSelectBySeedId(): Unit = withConnection {
    val found = employees.selectById(EmployeesId(1001L, "A"))
    assertTrue(found.isDefined)
    assertEquals("Alice Johnson", found.get.empName)
  }

  @Test def employeesCompositeIdFromRow(): Unit = withConnection {
    val inserted = employees.insert(EmployeesRow(3001L, "C", "HR", "US-EAST", "Carol", None, LocalDate.of(2025, 3, 1)))
    assertEquals(3001L, inserted.compositeId.empNumber)
    assertEquals("C", inserted.compositeId.empSuffix)
  }

  @Test def employeesUpdate(): Unit = withConnection {
    val inserted = employees.insert(EmployeesRow(4001L, "D", "IT", "US-WEST", "David", Some(BigDecimal("120000")), LocalDate.of(2025, 4, 1)))
    employees.update(inserted.copy(salary = Some(BigDecimal("130000"))))
    assertEquals(0, BigDecimal("130000").compare(employees.selectById(inserted.compositeId).get.salary.get))
  }

  @Test def employeesDelete(): Unit = withConnection {
    val inserted = employees.insert(EmployeesRow(5001L, "X", "IT", "US-WEST", "Disposable", None, LocalDate.of(2025, 5, 1)))
    assertTrue(employees.deleteById(inserted.compositeId))
  }

  @Test def employeesMultipleSameNumber(): Unit = withConnection {
    employees.insert(EmployeesRow(9999L, "A", "IT", "US-WEST", "Emp A", None, LocalDate.of(2025, 1, 1)))
    employees.insert(EmployeesRow(9999L, "B", "IT", "US-WEST", "Emp B", None, LocalDate.of(2025, 1, 1)))
    employees.insert(EmployeesRow(9999L, "C", "IT", "US-WEST", "Emp C", None, LocalDate.of(2025, 1, 1)))
    assertTrue(employees.selectById(EmployeesId(9999L, "A")).isDefined)
    assertTrue(employees.selectById(EmployeesId(9999L, "B")).isDefined)
    assertTrue(employees.selectById(EmployeesId(9999L, "C")).isDefined)
  }

  @Test def employeeDepartmentForeignKey(): Unit = withConnection {
    departments.insert(DepartmentsRow("FK_TEST", "FK_REGION", "FK Dept", Some(BigDecimal("500000"))))
    val emp = employees.insert(EmployeesRow(6001L, "F", "FK_TEST", "FK_REGION", "FK Emp", Some(BigDecimal("60000")), LocalDate.of(2025, 6, 1)))
    assertEquals("FK_TEST", emp.deptCode)
    assertTrue(departments.selectById(DepartmentsId(emp.deptCode, emp.deptRegion)).isDefined)
  }
}
