package oracledb.departments

import oracledb.MoneyT
import oracledb.customtypes.Defaulted
import oracledb.employees.{EmployeesId, EmployeesRepoImpl, EmployeesRow, EmployeesRowUnsaved}
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

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
      val budget = new MoneyT(BigDecimal("1000000.01"), "USD")
      val dept = new DepartmentsRow("IT", "WEST", "Information Technology", Some(budget))

      val insertedId = departmentsRepo.insert(dept)

      val _ = assert(insertedId != null)
      val inserted = departmentsRepo.selectById(insertedId).get
      val _ = assert(inserted != null)
      val _ = assert(inserted.deptCode == "IT")
      val _ = assert(inserted.deptRegion == "WEST")
      val _ = assert(inserted.deptName == "Information Technology")
      val _ = assert(inserted.budget.isDefined)
      val _ = assert(inserted.budget.get.amount == BigDecimal("1000000.01"))
      val _ = assert(inserted.budget.get.currency == "USD")
    }
  }

  test("select department by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("HR", "EAST", "Human Resources", None)
      val _ = departmentsRepo.insert(dept)

      val id = new DepartmentsId("HR", "EAST")
      val found = departmentsRepo.selectById(id)

      val _ = assert(found.isDefined)
      val _ = assert(found.get.deptCode == "HR")
      val _ = assert(found.get.deptRegion == "EAST")
      val _ = assert(found.get.deptName == "Human Resources")
    }
  }

  test("select departments by composite ids") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("SALES", "NORTH", "Sales North", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("SALES", "SOUTH", "Sales South", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("FINANCE", "CENTRAL", "Finance", None))

      val ids = Array(
        new DepartmentsId("SALES", "NORTH"),
        new DepartmentsId("SALES", "SOUTH"),
        new DepartmentsId("NONEXISTENT", "NONE")
      )
      val found = departmentsRepo.selectByIds(ids)

      val _ = assert(found.size == 2)
      val _ = assert(found.exists(d => d.deptCode == "SALES" && d.deptRegion == "NORTH"))
      val _ = assert(found.exists(d => d.deptCode == "SALES" && d.deptRegion == "SOUTH"))
    }
  }

  test("select departments by composite ids tracked") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("DEV", "WEST", "Development West", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEV", "EAST", "Development East", None))

      val ids = Array(
        new DepartmentsId("DEV", "WEST"),
        new DepartmentsId("DEV", "EAST"),
        new DepartmentsId("MISSING", "NONE")
      )
      val tracked = departmentsRepo.selectByIdsTracked(ids)

      val _ = assert(tracked.size == 2)
      val _ = assert(tracked.contains(new DepartmentsId("DEV", "WEST")))
      val _ = assert(tracked.contains(new DepartmentsId("DEV", "EAST")))
      val _ = assert(!tracked.contains(new DepartmentsId("MISSING", "NONE")))
    }
  }

  test("update department") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow(
        "OPS",
        "CENTRAL",
        "Operations",
        Some(new MoneyT(BigDecimal("500000.00"), "USD"))
      )
      val insertedId = departmentsRepo.insert(dept)
      val inserted = departmentsRepo.selectById(insertedId).get

      val updated = inserted.copy(budget = Some(new MoneyT(BigDecimal("750000.01"), "USD")))
      val wasUpdated = departmentsRepo.update(updated)

      val _ = assert(wasUpdated)

      val found = departmentsRepo.selectById(new DepartmentsId("OPS", "CENTRAL"))
      val _ = assert(found.isDefined)
      val _ = assert(found.get.budget.isDefined)
      val _ = assert(found.get.budget.get.amount == BigDecimal("750000.01"))
    }
  }

  test("delete department by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("TEMP", "REGION1", "Temporary Dept", None)
      val _ = departmentsRepo.insert(dept)

      val id = new DepartmentsId("TEMP", "REGION1")
      val deleted = departmentsRepo.deleteById(id)

      val _ = assert(deleted)

      val found = departmentsRepo.selectById(id)
      val _ = assert(found.isEmpty)
    }
  }

  test("delete departments by composite ids") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL1", "R1", "Delete 1", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL1", "R2", "Delete 2", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEL2", "R1", "Delete 3", None))

      val ids = Array(new DepartmentsId("DEL1", "R1"), new DepartmentsId("DEL1", "R2"))
      val deletedCount = departmentsRepo.deleteByIds(ids)

      val _ = assert(deletedCount == 2)

      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL1", "R1")).isEmpty)
      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL1", "R2")).isEmpty)
      val _ = assert(departmentsRepo.selectById(new DepartmentsId("DEL2", "R1")).isDefined)
    }
  }

  test("insert employee with foreign key to department") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow(
        "ENG",
        "WEST",
        "Engineering West",
        Some(new MoneyT(BigDecimal("2000000.00"), "USD"))
      )
      val _ = departmentsRepo.insert(dept)

      val empUnsaved = new EmployeesRowUnsaved(
        BigDecimal("12345"),
        "JR",
        "ENG",
        "WEST",
        "John Smith",
        Some(new MoneyT(BigDecimal("75000.00"), "USD")),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = employeesRepo.insert(empUnsaved)

      val _ = assert(insertedId != null)
      val inserted = employeesRepo.selectById(insertedId).get
      val _ = assert(inserted != null)
      val _ = assert(inserted.empNumber == BigDecimal("12345"))
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
      val _ = departmentsRepo.insert(new DepartmentsRow("ACCT", "SOUTH", "Accounting", None))

      val empUnsaved = new EmployeesRowUnsaved(
        BigDecimal("98765"),
        "SR",
        "ACCT",
        "SOUTH",
        "Jane Doe",
        Some(new MoneyT(BigDecimal("95000.00"), "USD")),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )
      val _ = employeesRepo.insert(empUnsaved)

      val empId = new EmployeesId(BigDecimal("98765"), "SR")
      val found = employeesRepo.selectById(empId)

      val _ = assert(found.isDefined)
      val _ = assert(found.get.empNumber == BigDecimal("98765"))
      val _ = assert(found.get.empSuffix == "SR")
      val _ = assert(found.get.deptCode == "ACCT")
      val _ = assert(found.get.deptRegion == "SOUTH")
    }
  }

  test("multiple employees in same department") {
    withConnection { c =>
      given java.sql.Connection = c
      val ts = System.currentTimeMillis() % 100000000
      val empNum1 = BigDecimal(ts)
      val empNum2 = BigDecimal(ts + 1)
      val empNum3 = BigDecimal(ts + 2)
      val deptCode = s"D${ts % 10000}"
      val deptRegion = s"R${ts % 1000}"

      val _ = departmentsRepo.insert(new DepartmentsRow(deptCode, deptRegion, "Production", None))

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          empNum1,
          "A",
          deptCode,
          deptRegion,
          "Employee A",
          Some(new MoneyT(BigDecimal("60000.01"), "USD")),
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
          Some(new MoneyT(BigDecimal("65000.01"), "USD")),
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
          Some(new MoneyT(BigDecimal("70000.01"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val emp1 = employeesRepo.selectById(new EmployeesId(empNum1, "A")).get
      val emp2 = employeesRepo.selectById(new EmployeesId(empNum2, "B")).get
      val emp3 = employeesRepo.selectById(new EmployeesId(empNum3, "C")).get

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
      val _ = departmentsRepo.insert(new DepartmentsRow("DEPT1", "LOC1", "Department 1", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("DEPT2", "LOC2", "Department 2", None))

      val empId = employeesRepo.insert(
        new EmployeesRowUnsaved(
          BigDecimal("5000"),
          "X",
          "DEPT1",
          "LOC1",
          "Transferable Employee",
          Some(new MoneyT(BigDecimal("80000.00"), "USD")),
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val emp = employeesRepo.selectById(empId).get

      val transferred = emp.copy(deptCode = "DEPT2", deptRegion = "LOC2")
      val updated = employeesRepo.update(transferred)

      val _ = assert(updated)

      val found = employeesRepo.selectById(new EmployeesId(BigDecimal("5000"), "X"))
      val _ = assert(found.isDefined)
      val _ = assert(found.get.deptCode == "DEPT2")
      val _ = assert(found.get.deptRegion == "LOC2")
    }
  }

  test("delete employee by composite id") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("TEST", "TEST", "Test Dept", None))
      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          BigDecimal("9999"),
          "Z",
          "TEST",
          "TEST",
          "Delete Me",
          None,
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val empId = new EmployeesId(BigDecimal("9999"), "Z")
      val deleted = employeesRepo.deleteById(empId)

      val _ = assert(deleted)

      val found = employeesRepo.selectById(empId)
      val _ = assert(found.isEmpty)
    }
  }

  test("select all departments") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL1", "A", "All Test 1", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL2", "B", "All Test 2", None))
      val _ = departmentsRepo.insert(new DepartmentsRow("ALL3", "C", "All Test 3", None))

      val all = departmentsRepo.selectAll

      val _ = assert(all.size >= 3)
      val _ = assert(all.exists(d => d.deptCode == "ALL1" && d.deptRegion == "A"))
      val _ = assert(all.exists(d => d.deptCode == "ALL2" && d.deptRegion == "B"))
      val _ = assert(all.exists(d => d.deptCode == "ALL3" && d.deptRegion == "C"))
    }
  }

  test("select all employees") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("ALLEMP", "REGION", "All Employees Test", None))

      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          BigDecimal("101"),
          "A",
          "ALLEMP",
          "REGION",
          "Emp 1",
          None,
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val _ = employeesRepo.insert(
        new EmployeesRowUnsaved(
          BigDecimal("102"),
          "B",
          "ALLEMP",
          "REGION",
          "Emp 2",
          None,
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )

      val all = employeesRepo.selectAll

      val _ = assert(all.size >= 2)
      val _ = assert(all.exists(e => e.empNumber == BigDecimal("101") && e.empSuffix == "A"))
      val _ = assert(all.exists(e => e.empNumber == BigDecimal("102") && e.empSuffix == "B"))
    }
  }

  test("department with null budget") {
    withConnection { c =>
      given java.sql.Connection = c
      val dept = new DepartmentsRow("NOBUD", "REGION", "No Budget Dept", None)
      val insertedId = departmentsRepo.insert(dept)
      val inserted = departmentsRepo.selectById(insertedId).get

      val _ = assert(inserted.budget.isEmpty)

      val found = departmentsRepo.selectById(new DepartmentsId("NOBUD", "REGION"))
      val _ = assert(found.isDefined)
      val _ = assert(found.get.budget.isEmpty)
    }
  }

  test("employee with null salary") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = departmentsRepo.insert(new DepartmentsRow("NOSAL", "REGION", "No Salary Dept", None))

      val empId = employeesRepo.insert(
        new EmployeesRowUnsaved(
          BigDecimal("777"),
          "N",
          "NOSAL",
          "REGION",
          "No Salary Employee",
          None,
          Defaulted.UseDefault[java.time.LocalDateTime]()
        )
      )
      val emp = employeesRepo.selectById(empId).get

      val _ = assert(emp.salary.isEmpty)

      val found = employeesRepo.selectById(new EmployeesId(BigDecimal("777"), "N"))
      val _ = assert(found.isDefined)
      val _ = assert(found.get.salary.isEmpty)
    }
  }
}
