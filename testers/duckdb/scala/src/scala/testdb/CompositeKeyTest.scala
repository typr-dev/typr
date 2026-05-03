package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.departments._
import testdb.employees._

import java.time.LocalDate

/** Tests for composite primary keys in DuckDB. Tests the departments (2-column String,String) and employees (2-column Integer,String) tables with composite PKs.
  */
class CompositeKeyTest {
  private val departmentsRepo = DepartmentsRepoImpl()
  private val employeesRepo = EmployeesRepoImpl()

  // ==================== Departments (String, String) Composite Key ====================

  @Test
  def testDepartmentsInsert(): Unit = withConnection {
    val unique = System.nanoTime().toString.takeRight(6)
    val dept = DepartmentsRow(s"IT$unique", "US-WEST", "Information Technology", Some(BigDecimal("1000000")))

    val inserted = departmentsRepo.insert(dept)

    assertNotNull(inserted)
    assertEquals(s"IT$unique", inserted.deptCode)
    assertEquals("US-WEST", inserted.deptRegion)
    assertEquals("Information Technology", inserted.deptName)
    assertTrue(inserted.budget.isDefined)
    assertEquals(0, inserted.budget.get.compareTo(BigDecimal("1000000")))
  }

  @Test
  def testDepartmentsSelectByCompositeId(): Unit = withConnection {
    val dept = DepartmentsRow("HR", "EU-EAST", "Human Resources", None)
    val _ = departmentsRepo.insert(dept)

    val id = DepartmentsId("HR", "EU-EAST")
    val found = departmentsRepo.selectById(id)

    assertTrue(found.isDefined)
    assertEquals("HR", found.get.deptCode)
    assertEquals("EU-EAST", found.get.deptRegion)
  }

  @Test
  def testDepartmentsCompositeIdFromRow(): Unit = withConnection {
    val dept = DepartmentsRow("SALES", "APAC", "Sales APAC", Some(BigDecimal("500000")))
    val inserted = departmentsRepo.insert(dept)

    val compositeId = inserted.compositeId
    assertEquals("SALES", compositeId.deptCode)
    assertEquals("APAC", compositeId.deptRegion)

    val found = departmentsRepo.selectById(compositeId)
    assertTrue(found.isDefined)
    assertEquals(inserted, found.get)
  }

  @Test
  def testDepartmentsUpdate(): Unit = withConnection {
    val dept = DepartmentsRow("FINANCE", "US-CENTRAL", "Finance", Some(BigDecimal("800000")))
    val inserted = departmentsRepo.insert(dept)

    val updated = inserted.copy(budget = Some(BigDecimal("900000")))
    val wasUpdated = departmentsRepo.update(updated)
    assertTrue(wasUpdated)

    val found = departmentsRepo.selectById(inserted.compositeId).get
    assertEquals(Some(BigDecimal("900000")), found.budget)
  }

  @Test
  def testDepartmentsDelete(): Unit = withConnection {
    val dept = DepartmentsRow("TEMP", "TEMP-REGION", "Temporary", None)
    val inserted = departmentsRepo.insert(dept)

    val deleted = departmentsRepo.deleteById(inserted.compositeId)
    assertTrue(deleted)

    val found = departmentsRepo.selectById(inserted.compositeId)
    assertFalse(found.isDefined)
  }

  @Test
  def testDepartmentsMultipleSameCode(): Unit = withConnection {
    val dept1 = DepartmentsRow("ENG", "US", "Engineering US", None)
    val dept2 = DepartmentsRow("ENG", "EU", "Engineering EU", None)
    val dept3 = DepartmentsRow("ENG", "APAC", "Engineering APAC", None)

    val _ = departmentsRepo.insert(dept1)
    val _ = departmentsRepo.insert(dept2)
    val _ = departmentsRepo.insert(dept3)

    assertTrue(departmentsRepo.selectById(DepartmentsId("ENG", "US")).isDefined)
    assertTrue(departmentsRepo.selectById(DepartmentsId("ENG", "EU")).isDefined)
    assertTrue(departmentsRepo.selectById(DepartmentsId("ENG", "APAC")).isDefined)
  }

  // ==================== Employees (Integer, String) Composite Key ====================

  @Test
  def testEmployeesInsert(): Unit = withConnection {
    val unique = System.nanoTime().toString.takeRight(6)
    val empNum = (System.nanoTime() % 1000000).toInt
    val dept = DepartmentsRow(s"DEV$unique", "US", "Development", None)
    val _ = departmentsRepo.insert(dept)

    val emp = EmployeesRow(
      empNum,
      "A",
      s"DEV$unique",
      "US",
      "Alice Johnson",
      Some(BigDecimal("95000")),
      LocalDate.of(2025, 1, 15)
    )

    val inserted = employeesRepo.insert(emp)

    assertNotNull(inserted)
    assertEquals(empNum, inserted.empNumber)
    assertEquals("A", inserted.empSuffix)
    assertEquals("Alice Johnson", inserted.empName)
  }

  @Test
  def testEmployeesSelectByCompositeId(): Unit = withConnection {
    val _ = departmentsRepo.insert(DepartmentsRow("QA", "EU", "Quality Assurance", None))

    val emp = EmployeesRow(
      2001,
      "B",
      "QA",
      "EU",
      "Bob Smith",
      Some(BigDecimal("75000")),
      LocalDate.of(2024, 6, 1)
    )
    val _ = employeesRepo.insert(emp)

    val id = EmployeesId(2001, "B")
    val found = employeesRepo.selectById(id)

    assertTrue(found.isDefined)
    assertEquals(2001, found.get.empNumber)
    assertEquals("B", found.get.empSuffix)
    assertEquals("Bob Smith", found.get.empName)
  }

  @Test
  def testEmployeesCompositeIdFromRow(): Unit = withConnection {
    val _ = departmentsRepo.insert(DepartmentsRow("SUPPORT", "US", "Support", None))

    val emp = EmployeesRow(
      3001,
      "C",
      "SUPPORT",
      "US",
      "Carol White",
      None,
      LocalDate.now()
    )
    val inserted = employeesRepo.insert(emp)

    val compositeId = inserted.compositeId
    assertEquals(3001, compositeId.empNumber)
    assertEquals("C", compositeId.empSuffix)

    val found = employeesRepo.selectById(compositeId)
    assertTrue(found.isDefined)
  }

  @Test
  def testEmployeesUpdate(): Unit = withConnection {
    val _ = departmentsRepo.insert(DepartmentsRow("MGMT", "US", "Management", None))

    val emp = EmployeesRow(
      4001,
      "D",
      "MGMT",
      "US",
      "David Brown",
      Some(BigDecimal("120000")),
      LocalDate.now()
    )
    val inserted = employeesRepo.insert(emp)

    val updated = inserted.copy(salary = Some(BigDecimal("130000")))
    val _ = employeesRepo.update(updated)

    val found = employeesRepo.selectById(inserted.compositeId).get
    assertEquals(Some(BigDecimal("130000")), found.salary)
  }

  @Test
  def testEmployeesDelete(): Unit = withConnection {
    val _ = departmentsRepo.insert(DepartmentsRow("TEMP", "TEMP", "Temp Dept", None))

    val emp = EmployeesRow(5001, "X", "TEMP", "TEMP", "To Be Deleted", None, LocalDate.now())
    val inserted = employeesRepo.insert(emp)

    val deleted = employeesRepo.deleteById(inserted.compositeId)
    assertTrue(deleted)

    val found = employeesRepo.selectById(inserted.compositeId)
    assertFalse(found.isDefined)
  }

  @Test
  def testEmployeesMultipleSameNumber(): Unit = withConnection {
    val _ = departmentsRepo.insert(DepartmentsRow("SHARED", "US", "Shared Dept", None))

    val emp1 = EmployeesRow(9999, "A", "SHARED", "US", "Employee A", None, LocalDate.now())
    val emp2 = EmployeesRow(9999, "B", "SHARED", "US", "Employee B", None, LocalDate.now())
    val emp3 = EmployeesRow(9999, "C", "SHARED", "US", "Employee C", None, LocalDate.now())

    val _ = employeesRepo.insert(emp1)
    val _ = employeesRepo.insert(emp2)
    val _ = employeesRepo.insert(emp3)

    assertTrue(employeesRepo.selectById(EmployeesId(9999, "A")).isDefined)
    assertTrue(employeesRepo.selectById(EmployeesId(9999, "B")).isDefined)
    assertTrue(employeesRepo.selectById(EmployeesId(9999, "C")).isDefined)
  }

  // ==================== Composite FK Relationship ====================

  @Test
  def testEmployeeDepartmentForeignKey(): Unit = withConnection {
    val dept = DepartmentsRow("FK_TEST", "FK_REGION", "FK Test Dept", Some(BigDecimal("500000")))
    val _ = departmentsRepo.insert(dept)

    val emp = EmployeesRow(
      6001,
      "F",
      "FK_TEST",
      "FK_REGION",
      "FK Employee",
      Some(BigDecimal("60000")),
      LocalDate.now()
    )
    val insertedEmp = employeesRepo.insert(emp)

    assertEquals(dept.deptCode, insertedEmp.deptCode)
    assertEquals(dept.deptRegion, insertedEmp.deptRegion)

    val foundDept = departmentsRepo.selectById(DepartmentsId(insertedEmp.deptCode, insertedEmp.deptRegion))
    assertTrue(foundDept.isDefined)
    assertEquals("FK Test Dept", foundDept.get.deptName)
  }
}
