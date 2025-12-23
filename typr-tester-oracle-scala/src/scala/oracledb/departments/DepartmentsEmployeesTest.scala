package oracledb.departments

import oracledb.MoneyT
import oracledb.customtypes.Defaulted
import oracledb.employees.{EmployeesId, EmployeesRepoImpl, EmployeesRow, EmployeesRowUnsaved}
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite
import scala.jdk.CollectionConverters._

/** Tests for departments and employees tables with composite primary keys.
  *
  * Schema:
  *   - DEPARTMENTS has composite PK: (DEPT_CODE, DEPT_REGION)
  *   - EMPLOYEES has composite PK: (EMP_NUMBER, EMP_SUFFIX)
  *   - EMPLOYEES has FK to DEPARTMENTS: (DEPT_CODE, DEPT_REGION)
  */
class DepartmentsEmployeesTest extends AnyFunSuite {
  val departmentsRepo: DepartmentsRepoImpl = new DepartmentsRepoImpl
  val employeesRepo: EmployeesRepoImpl = new EmployeesRepoImpl

  test("insert department with composite PK") {
    withConnection { c =>
      given java.sql.Connection = c
      val budget = new MoneyT(new java.math.BigDecimal("1000000.01"), "USD")
      val dept = new DepartmentsRow("IT", "WEST", "Information Technology", java.util.Optional.of(budget))

      val insertedId = departmentsRepo.insert(dept)

      val _ = assert(insertedId != null)
      val inserted = departmentsRepo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted != null)
      val _ = assert(inserted.deptCode == "IT")
      val _ = assert(inserted.deptRegion == "WEST")
      val _ = assert(inserted.deptName == "Information Technology")
      val _ = assert(inserted.budget.isPresent())
      val _ = assert(inserted.budget.orElseThrow().amount == new java.math.BigDecimal("1000000.01"))
      val _ = assert(inserted.budget.orElseThrow().currency == "USD")
    }
  }

  test("select department by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("HR", "EAST", "Human Resources", java.util.Optional.empty[MoneyT]())
      val _ = departmentsRepo.insert(dept)

      val id = new DepartmentsId("HR", "EAST")
      val found = departmentsRepo.selectById(id)

      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().deptCode == "HR")
      val _ = assert(found.orElseThrow().deptRegion == "EAST")
      val _ = assert(found.orElseThrow().deptName == "Human Resources")
    }
  }

  test("select departments by composite ids") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("SALES", "NORTH", "Sales North", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("SALES", "SOUTH", "Sales South", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("FINANCE", "CENTRAL", "Finance", java.util.Optional.empty[MoneyT]()))

      val ids = Array(
        new DepartmentsId("SALES", "NORTH"),
        new DepartmentsId("SALES", "SOUTH"),
        new DepartmentsId("NONEXISTENT", "NONE")
      )
      val found = departmentsRepo.selectByIds(ids)

      val _ = assert(found.size() == 2)
      val _ = assert(found.asScala.exists(d => d.deptCode == "SALES" && d.deptRegion == "NORTH"))
      val _ = assert(found.asScala.exists(d => d.deptCode == "SALES" && d.deptRegion == "SOUTH"))
    }
  }

  test("select departments by composite ids tracked") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("DEV", "WEST", "Development West", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEV", "EAST", "Development East", java.util.Optional.empty[MoneyT]()))

      val ids = Array(
        new DepartmentsId("DEV", "WEST"),
        new DepartmentsId("DEV", "EAST"),
        new DepartmentsId("MISSING", "NONE")
      )
      val tracked = departmentsRepo.selectByIdsTracked(ids)

      val _ = assert(tracked.size() == 2)
      val _ = assert(tracked.containsKey(new DepartmentsId("DEV", "WEST")))
      val _ = assert(tracked.containsKey(new DepartmentsId("DEV", "EAST")))
      val _ = assert(!tracked.containsKey(new DepartmentsId("MISSING", "NONE")))
    }
  }

  test("update department") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow(
        "OPS",
        "CENTRAL",
        "Operations",
        java.util.Optional.of(new MoneyT(new java.math.BigDecimal("500000.00"), "USD"))
      )
      val insertedId = departmentsRepo.insert(dept)
      val inserted = departmentsRepo.selectById(insertedId).orElseThrow()

      val updated = inserted.copy(budget = java.util.Optional.of(new MoneyT(new java.math.BigDecimal("750000.01"), "USD")))
      val wasUpdated = departmentsRepo.update(updated)

      val _ = assert(wasUpdated)

      val found = departmentsRepo.selectById(new DepartmentsId("OPS", "CENTRAL"))
      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().budget.isPresent())
      val _ = assert(found.orElseThrow().budget.orElseThrow().amount == new java.math.BigDecimal("750000.01"))
    }
  }

  test("delete department by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("TEMP", "REGION1", "Temporary Dept", java.util.Optional.empty[MoneyT]())
      val _ = departmentsRepo.insert(dept)

      val id = new DepartmentsId("TEMP", "REGION1")
      val deleted = departmentsRepo.deleteById(id)

      val _ = assert(deleted)

      val found = departmentsRepo.selectById(id)
      val _ = assert(found.isEmpty())
    }
  }

  test("delete departments by composite ids") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL1", "R1", "Delete 1", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL1", "R2", "Delete 2", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL2", "R1", "Delete 3", java.util.Optional.empty[MoneyT]()))

      val ids = Array(new DepartmentsId("DEL1", "R1"), new DepartmentsId("DEL1", "R2"))
      val deletedCount = departmentsRepo.deleteByIds(ids)

      val _ = assert(deletedCount == 2)

      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL1", "R1")).isEmpty())
      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL1", "R2")).isEmpty())
      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL2", "R1")).isPresent())
    }
  }

  test("insert employee with foreign key to department") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow(
        "ENG",
        "WEST",
        "Engineering West",
        java.util.Optional.of(new MoneyT(new java.math.BigDecimal("2000000.00"), "USD"))
      )
      val _ = departmentsRepo.insert(dept)

      val empUnsaved = new EmployeesRowUnsaved(
        new java.math.BigDecimal("12345"),
        "JR",
        "ENG",
        "WEST",
        "John Smith",
        java.util.Optional.of(new MoneyT(new java.math.BigDecimal("75000.00"), "USD")),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = employeesRepo.insert(empUnsaved)

      val _ = assert(insertedId != null)
      val inserted = employeesRepo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted != null)
      val _ = assert(inserted.empNumber == new java.math.BigDecimal("12345"))
      val _ = assert(inserted.empSuffix == "JR")
      val _ = assert(inserted.deptCode == "ENG")
      val _ = assert(inserted.deptRegion == "WEST")
      val _ = assert(inserted.empName == "John Smith")
      val _ = assert(inserted.hireDate != null)
    }
  }

  test("select employee by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("ACCT", "SOUTH", "Accounting", java.util.Optional.empty[MoneyT]()))

      val empUnsaved = new EmployeesRowUnsaved(
        new java.math.BigDecimal("98765"),
        "SR",
        "ACCT",
        "SOUTH",
        "Jane Doe",
        java.util.Optional.of(new MoneyT(new java.math.BigDecimal("95000.00"), "USD")),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )
      val _ = employeesRepo.insert(empUnsaved)

      val empId = new EmployeesId(new java.math.BigDecimal("98765"), "SR")
      val found = employeesRepo.selectById(empId)

      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().empNumber == new java.math.BigDecimal("98765"))
      val _ = assert(found.orElseThrow().empSuffix == "SR")
      val _ = assert(found.orElseThrow().deptCode == "ACCT")
      val _ = assert(found.orElseThrow().deptRegion == "SOUTH")
    }
  }

  test("multiple employees in same department") {
    withConnection { c =>
      given java.sql.Connection = c
      val ts = System.currentTimeMillis() % 100000000
      val empNum1 = new java.math.BigDecimal(ts)
      val empNum2 = new java.math.BigDecimal(ts + 1)
      val empNum3 = new java.math.BigDecimal(ts + 2)
      val deptCode = s"D${ts % 10000}"
      val deptRegion = s"R${ts % 1000}"

      val _ = departmentsRepo.insert(new DepartmentsRow(deptCode, deptRegion, "Production", java.util.Optional.empty[MoneyT]()))

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          empNum1,
          "A",
          deptCode,
          deptRegion,
          "Employee A",
          java.util.Optional.of(new MoneyT(new java.math.BigDecimal("60000.01"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          empNum2,
          "B",
          deptCode,
          deptRegion,
          "Employee B",
          java.util.Optional.of(new MoneyT(new java.math.BigDecimal("65000.01"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          empNum3,
          "C",
          deptCode,
          deptRegion,
          "Employee C",
          java.util.Optional.of(new MoneyT(new java.math.BigDecimal("70000.01"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val emp1 = employeesRepo.selectById(new EmployeesId(empNum1, "A")).orElseThrow()
      val emp2 = employeesRepo.selectById(new EmployeesId(empNum2, "B")).orElseThrow()
      val emp3 = employeesRepo.selectById(new EmployeesId(empNum3, "C")).orElseThrow()

      val _ = assert(emp1.deptCode == deptCode)
      val _ = assert(emp1.deptRegion == deptRegion)
      val _ = assert(emp2.deptCode == deptCode)
      val _ = assert(emp2.deptRegion == deptRegion)
      val _ = assert(emp3.deptCode == deptCode)
      val _ = assert(emp3.deptRegion == deptRegion)
    }
  }

  test("update employee department") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("DEPT1", "LOC1", "Department 1", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEPT2", "LOC2", "Department 2", java.util.Optional.empty[MoneyT]()))

      val empId = employeesRepo.insert(
        new EmployeesRowUnsaved(
          new java.math.BigDecimal("5000"),
          "X",
          "DEPT1",
          "LOC1",
          "Transferable Employee",
          java.util.Optional.of(new MoneyT(new java.math.BigDecimal("80000.00"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val emp = employeesRepo.selectById(empId).orElseThrow()

      val transferred = emp.copy(deptCode = "DEPT2", deptRegion = "LOC2")
      val updated = employeesRepo.update(transferred)

      val _ = assert(updated)

      val found = employeesRepo.selectById(new EmployeesId(new java.math.BigDecimal("5000"), "X"))
      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().deptCode == "DEPT2")
      val _ = assert(found.orElseThrow().deptRegion == "LOC2")
    }
  }

  test("delete employee by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("TEST", "TEST", "Test Dept", java.util.Optional.empty[MoneyT]()))
      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          new java.math.BigDecimal("9999"),
          "Z",
          "TEST",
          "TEST",
          "Delete Me",
          java.util.Optional.empty[MoneyT](),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val empId = new EmployeesId(new java.math.BigDecimal("9999"), "Z")
      val deleted = employeesRepo.deleteById(empId)

      val _ = assert(deleted)

      val found = employeesRepo.selectById(empId)
      val _ = assert(found.isEmpty())
    }
  }

  test("select all departments") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL1", "A", "All Test 1", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL2", "B", "All Test 2", java.util.Optional.empty[MoneyT]()))
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL3", "C", "All Test 3", java.util.Optional.empty[MoneyT]()))

      val all = departmentsRepo.selectAll

      val _ = assert(all.size() >= 3)
      val _ = assert(all.asScala.exists(d => d.deptCode == "ALL1" && d.deptRegion == "A"))
      val _ = assert(all.asScala.exists(d => d.deptCode == "ALL2" && d.deptRegion == "B"))
      val _ = assert(all.asScala.exists(d => d.deptCode == "ALL3" && d.deptRegion == "C"))
    }
  }

  test("select all employees") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("ALLEMP", "REGION", "All Employees Test", java.util.Optional.empty[MoneyT]()))

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          new java.math.BigDecimal("101"),
          "A",
          "ALLEMP",
          "REGION",
          "Emp 1",
          java.util.Optional.empty[MoneyT](),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          new java.math.BigDecimal("102"),
          "B",
          "ALLEMP",
          "REGION",
          "Emp 2",
          java.util.Optional.empty[MoneyT](),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val all = employeesRepo.selectAll

      val _ = assert(all.size() >= 2)
      val _ = assert(all.asScala.exists(e => e.empNumber == new java.math.BigDecimal("101") && e.empSuffix == "A"))
      val _ = assert(all.asScala.exists(e => e.empNumber == new java.math.BigDecimal("102") && e.empSuffix == "B"))
    }
  }

  test("department with null budget") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("NOBUD", "REGION", "No Budget Dept", java.util.Optional.empty[MoneyT]())
      val insertedId = departmentsRepo.insert(dept)
      val inserted = departmentsRepo.selectById(insertedId).orElseThrow()

      val _ = assert(inserted.budget.isEmpty())

      val found = departmentsRepo.selectById(new DepartmentsId("NOBUD", "REGION"))
      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().budget.isEmpty())
    }
  }

  test("employee with null salary") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("NOSAL", "REGION", "No Salary Dept", java.util.Optional.empty[MoneyT]()))

      val empId = employeesRepo.insert(
        new EmployeesRowUnsaved(
          new java.math.BigDecimal("777"),
          "N",
          "NOSAL",
          "REGION",
          "No Salary Employee",
          java.util.Optional.empty[MoneyT](),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val emp = employeesRepo.selectById(empId).orElseThrow()

      val _ = assert(emp.salary.isEmpty())

      val found = employeesRepo.selectById(new EmployeesId(new java.math.BigDecimal("777"), "N"))
      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().salary.isEmpty())
    }
  }
}
