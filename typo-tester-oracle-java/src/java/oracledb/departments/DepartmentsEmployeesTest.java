package oracledb.departments;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import oracledb.MoneyT;
import oracledb.OracleTestHelper;
import oracledb.customtypes.Defaulted;
import oracledb.employees.EmployeesId;
import oracledb.employees.EmployeesRepoImpl;
import oracledb.employees.EmployeesRow;
import oracledb.employees.EmployeesRowUnsaved;
import org.junit.Test;

/**
 * Tests for departments and employees tables with composite primary keys.
 *
 * <p>Schema: - DEPARTMENTS has composite PK: (DEPT_CODE, DEPT_REGION) - EMPLOYEES has composite PK:
 * (EMP_NUMBER, EMP_SUFFIX) - EMPLOYEES has FK to DEPARTMENTS: (DEPT_CODE, DEPT_REGION)
 */
public class DepartmentsEmployeesTest {
  private final DepartmentsRepoImpl departmentsRepo = new DepartmentsRepoImpl();
  private final EmployeesRepoImpl employeesRepo = new EmployeesRepoImpl();

  @Test
  public void testInsertDepartmentWithCompositePK() {
    OracleTestHelper.run(
        c -> {
          MoneyT budget = new MoneyT(new BigDecimal("1000000.01"), "USD");
          DepartmentsRow dept =
              new DepartmentsRow("IT", "WEST", "Information Technology", Optional.of(budget));

          DepartmentsId insertedId = departmentsRepo.insert(dept, c);

          assertNotNull(insertedId);
          DepartmentsRow inserted = departmentsRepo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted);
          assertEquals("IT", inserted.deptCode());
          assertEquals("WEST", inserted.deptRegion());
          assertEquals("Information Technology", inserted.deptName());
          assertTrue(inserted.budget().isPresent());
          assertEquals(new BigDecimal("1000000.01"), inserted.budget().get().amount());
          assertEquals("USD", inserted.budget().get().currency());
        });
  }

  @Test
  public void testSelectDepartmentByCompositeId() {
    OracleTestHelper.run(
        c -> {
          // Insert department
          DepartmentsRow dept =
              new DepartmentsRow("HR", "EAST", "Human Resources", Optional.empty());
          departmentsRepo.insert(dept, c);

          // Select by composite ID
          DepartmentsId id = new DepartmentsId("HR", "EAST");
          Optional<DepartmentsRow> found = departmentsRepo.selectById(id, c);

          assertTrue(found.isPresent());
          assertEquals("HR", found.get().deptCode());
          assertEquals("EAST", found.get().deptRegion());
          assertEquals("Human Resources", found.get().deptName());
        });
  }

  @Test
  public void testSelectDepartmentsByCompositeIds() {
    OracleTestHelper.run(
        c -> {
          // Insert multiple departments
          departmentsRepo.insert(
              new DepartmentsRow("SALES", "NORTH", "Sales North", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("SALES", "SOUTH", "Sales South", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("FINANCE", "CENTRAL", "Finance", Optional.empty()), c);

          // Select multiple by composite IDs
          DepartmentsId[] ids = {
            new DepartmentsId("SALES", "NORTH"),
            new DepartmentsId("SALES", "SOUTH"),
            new DepartmentsId("NONEXISTENT", "NONE")
          };
          List<DepartmentsRow> found = departmentsRepo.selectByIds(ids, c);

          assertEquals(2, found.size());
          assertTrue(
              found.stream()
                  .anyMatch(d -> d.deptCode().equals("SALES") && d.deptRegion().equals("NORTH")));
          assertTrue(
              found.stream()
                  .anyMatch(d -> d.deptCode().equals("SALES") && d.deptRegion().equals("SOUTH")));
        });
  }

  @Test
  public void testSelectDepartmentsByCompositeIdsTracked() {
    OracleTestHelper.run(
        c -> {
          // Insert multiple departments
          departmentsRepo.insert(
              new DepartmentsRow("DEV", "WEST", "Development West", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("DEV", "EAST", "Development East", Optional.empty()), c);

          // Select multiple by composite IDs with tracking
          DepartmentsId[] ids = {
            new DepartmentsId("DEV", "WEST"),
            new DepartmentsId("DEV", "EAST"),
            new DepartmentsId("MISSING", "NONE")
          };
          Map<DepartmentsId, DepartmentsRow> tracked = departmentsRepo.selectByIdsTracked(ids, c);

          assertEquals(2, tracked.size());
          assertTrue(tracked.containsKey(new DepartmentsId("DEV", "WEST")));
          assertTrue(tracked.containsKey(new DepartmentsId("DEV", "EAST")));
          assertFalse(tracked.containsKey(new DepartmentsId("MISSING", "NONE")));
        });
  }

  @Test
  public void testUpdateDepartment() {
    OracleTestHelper.run(
        c -> {
          // Insert department
          DepartmentsRow dept =
              new DepartmentsRow(
                  "OPS",
                  "CENTRAL",
                  "Operations",
                  Optional.of(new MoneyT(new BigDecimal("500000.00"), "USD")));
          DepartmentsId insertedId = departmentsRepo.insert(dept, c);
          DepartmentsRow inserted = departmentsRepo.selectById(insertedId, c).orElseThrow();

          // Update budget
          DepartmentsRow updated =
              inserted.withBudget(Optional.of(new MoneyT(new BigDecimal("750000.01"), "USD")));
          Boolean wasUpdated = departmentsRepo.update(updated, c);

          assertTrue(wasUpdated);

          // Verify update
          Optional<DepartmentsRow> found =
              departmentsRepo.selectById(new DepartmentsId("OPS", "CENTRAL"), c);
          assertTrue(found.isPresent());
          assertTrue(found.get().budget().isPresent());
          assertEquals(new BigDecimal("750000.01"), found.get().budget().get().amount());
        });
  }

  @Test
  public void testDeleteDepartmentByCompositeId() {
    OracleTestHelper.run(
        c -> {
          // Insert department
          DepartmentsRow dept =
              new DepartmentsRow("TEMP", "REGION1", "Temporary Dept", Optional.empty());
          departmentsRepo.insert(dept, c);

          // Delete by composite ID
          DepartmentsId id = new DepartmentsId("TEMP", "REGION1");
          Boolean deleted = departmentsRepo.deleteById(id, c);

          assertTrue(deleted);

          // Verify deletion
          Optional<DepartmentsRow> found = departmentsRepo.selectById(id, c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testDeleteDepartmentsByCompositeIds() {
    OracleTestHelper.run(
        c -> {
          // Insert multiple departments
          departmentsRepo.insert(new DepartmentsRow("DEL1", "R1", "Delete 1", Optional.empty()), c);
          departmentsRepo.insert(new DepartmentsRow("DEL1", "R2", "Delete 2", Optional.empty()), c);
          departmentsRepo.insert(new DepartmentsRow("DEL2", "R1", "Delete 3", Optional.empty()), c);

          // Delete multiple by composite IDs
          DepartmentsId[] ids = {new DepartmentsId("DEL1", "R1"), new DepartmentsId("DEL1", "R2")};
          Integer deletedCount = departmentsRepo.deleteByIds(ids, c);

          assertEquals(Integer.valueOf(2), deletedCount);

          // Verify deletions
          assertFalse(departmentsRepo.selectById(new DepartmentsId("DEL1", "R1"), c).isPresent());
          assertFalse(departmentsRepo.selectById(new DepartmentsId("DEL1", "R2"), c).isPresent());
          assertTrue(departmentsRepo.selectById(new DepartmentsId("DEL2", "R1"), c).isPresent());
        });
  }

  @Test
  public void testInsertEmployeeWithForeignKeyToDepartment() {
    OracleTestHelper.run(
        c -> {
          // First, insert the department (parent)
          DepartmentsRow dept =
              new DepartmentsRow(
                  "ENG",
                  "WEST",
                  "Engineering West",
                  Optional.of(new MoneyT(new BigDecimal("2000000.00"), "USD")));
          departmentsRepo.insert(dept, c);

          // Now insert an employee referencing this department
          EmployeesRowUnsaved empUnsaved =
              new EmployeesRowUnsaved(
                  new BigDecimal("12345"),
                  "JR",
                  "ENG", // FK to department
                  "WEST", // FK to department
                  "John Smith",
                  Optional.of(new MoneyT(new BigDecimal("75000.00"), "USD")),
                  new Defaulted.UseDefault<>() // Let HIRE_DATE use SYSDATE
                  );

          EmployeesId insertedId = employeesRepo.insert(empUnsaved, c);

          assertNotNull(insertedId);
          EmployeesRow inserted = employeesRepo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted);
          assertEquals(new BigDecimal("12345"), inserted.empNumber());
          assertEquals("JR", inserted.empSuffix());
          assertEquals("ENG", inserted.deptCode());
          assertEquals("WEST", inserted.deptRegion());
          assertEquals("John Smith", inserted.empName());
          assertNotNull(inserted.hireDate()); // Should be set by database default
        });
  }

  @Test
  public void testSelectEmployeeByCompositeId() {
    OracleTestHelper.run(
        c -> {
          // Insert department
          departmentsRepo.insert(
              new DepartmentsRow("ACCT", "SOUTH", "Accounting", Optional.empty()), c);

          // Insert employee
          EmployeesRowUnsaved empUnsaved =
              new EmployeesRowUnsaved(
                  new BigDecimal("98765"),
                  "SR",
                  "ACCT",
                  "SOUTH",
                  "Jane Doe",
                  Optional.of(new MoneyT(new BigDecimal("95000.00"), "USD")),
                  new Defaulted.UseDefault<>());
          employeesRepo.insert(empUnsaved, c);

          // Select by composite ID
          EmployeesId empId = new EmployeesId(new BigDecimal("98765"), "SR");
          Optional<EmployeesRow> found = employeesRepo.selectById(empId, c);

          assertTrue(found.isPresent());
          assertEquals(new BigDecimal("98765"), found.get().empNumber());
          assertEquals("SR", found.get().empSuffix());
          assertEquals("ACCT", found.get().deptCode());
          assertEquals("SOUTH", found.get().deptRegion());
        });
  }

  @Test
  public void testMultipleEmployeesInSameDepartment() {
    OracleTestHelper.run(
        c -> {
          // Use unique IDs based on timestamp to avoid conflicts
          long ts = System.currentTimeMillis() % 100000000;
          BigDecimal empNum1 = new BigDecimal(ts);
          BigDecimal empNum2 = new BigDecimal(ts + 1);
          BigDecimal empNum3 = new BigDecimal(ts + 2);
          String deptCode = "D" + (ts % 10000);
          String deptRegion = "R" + (ts % 1000);

          // Insert department
          departmentsRepo.insert(
              new DepartmentsRow(deptCode, deptRegion, "Production", Optional.empty()), c);

          // Insert multiple employees in the same department
          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  empNum1,
                  "A",
                  deptCode,
                  deptRegion,
                  "Employee A",
                  Optional.of(new MoneyT(new BigDecimal("60000.01"), "USD")),
                  new Defaulted.UseDefault<>()),
              c);

          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  empNum2,
                  "B",
                  deptCode,
                  deptRegion,
                  "Employee B",
                  Optional.of(new MoneyT(new BigDecimal("65000.01"), "USD")),
                  new Defaulted.UseDefault<>()),
              c);

          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  empNum3,
                  "C",
                  deptCode,
                  deptRegion,
                  "Employee C",
                  Optional.of(new MoneyT(new BigDecimal("70000.01"), "USD")),
                  new Defaulted.UseDefault<>()),
              c);

          // Verify all employees are in the same department
          EmployeesRow emp1 = employeesRepo.selectById(new EmployeesId(empNum1, "A"), c).get();
          EmployeesRow emp2 = employeesRepo.selectById(new EmployeesId(empNum2, "B"), c).get();
          EmployeesRow emp3 = employeesRepo.selectById(new EmployeesId(empNum3, "C"), c).get();

          assertEquals(deptCode, emp1.deptCode());
          assertEquals(deptRegion, emp1.deptRegion());
          assertEquals(deptCode, emp2.deptCode());
          assertEquals(deptRegion, emp2.deptRegion());
          assertEquals(deptCode, emp3.deptCode());
          assertEquals(deptRegion, emp3.deptRegion());
        });
  }

  @Test
  public void testUpdateEmployeeDepartment() {
    OracleTestHelper.run(
        c -> {
          // Insert two departments
          departmentsRepo.insert(
              new DepartmentsRow("DEPT1", "LOC1", "Department 1", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("DEPT2", "LOC2", "Department 2", Optional.empty()), c);

          // Insert employee in first department
          EmployeesId empId =
              employeesRepo.insert(
                  new EmployeesRowUnsaved(
                      new BigDecimal("5000"),
                      "X",
                      "DEPT1",
                      "LOC1",
                      "Transferable Employee",
                      Optional.of(new MoneyT(new BigDecimal("80000.00"), "USD")),
                      new Defaulted.UseDefault<>()),
                  c);
          EmployeesRow emp = employeesRepo.selectById(empId, c).orElseThrow();

          // Transfer employee to second department
          EmployeesRow transferred = emp.withDeptCode("DEPT2").withDeptRegion("LOC2");
          Boolean updated = employeesRepo.update(transferred, c);

          assertTrue(updated);

          // Verify transfer
          Optional<EmployeesRow> found =
              employeesRepo.selectById(new EmployeesId(new BigDecimal("5000"), "X"), c);
          assertTrue(found.isPresent());
          assertEquals("DEPT2", found.get().deptCode());
          assertEquals("LOC2", found.get().deptRegion());
        });
  }

  @Test
  public void testDeleteEmployeeByCompositeId() {
    OracleTestHelper.run(
        c -> {
          // Insert department and employee
          departmentsRepo.insert(
              new DepartmentsRow("TEST", "TEST", "Test Dept", Optional.empty()), c);
          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  new BigDecimal("9999"),
                  "Z",
                  "TEST",
                  "TEST",
                  "Delete Me",
                  Optional.empty(),
                  new Defaulted.UseDefault<>()),
              c);

          // Delete employee
          EmployeesId empId = new EmployeesId(new BigDecimal("9999"), "Z");
          Boolean deleted = employeesRepo.deleteById(empId, c);

          assertTrue(deleted);

          // Verify deletion
          Optional<EmployeesRow> found = employeesRepo.selectById(empId, c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testSelectAllDepartments() {
    OracleTestHelper.run(
        c -> {
          // Insert multiple departments
          departmentsRepo.insert(
              new DepartmentsRow("ALL1", "A", "All Test 1", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("ALL2", "B", "All Test 2", Optional.empty()), c);
          departmentsRepo.insert(
              new DepartmentsRow("ALL3", "C", "All Test 3", Optional.empty()), c);

          List<DepartmentsRow> all = departmentsRepo.selectAll(c);

          assertTrue(all.size() >= 3);
          assertTrue(
              all.stream()
                  .anyMatch(d -> d.deptCode().equals("ALL1") && d.deptRegion().equals("A")));
          assertTrue(
              all.stream()
                  .anyMatch(d -> d.deptCode().equals("ALL2") && d.deptRegion().equals("B")));
          assertTrue(
              all.stream()
                  .anyMatch(d -> d.deptCode().equals("ALL3") && d.deptRegion().equals("C")));
        });
  }

  @Test
  public void testSelectAllEmployees() {
    OracleTestHelper.run(
        c -> {
          // Insert department
          departmentsRepo.insert(
              new DepartmentsRow("ALLEMP", "REGION", "All Employees Test", Optional.empty()), c);

          // Insert multiple employees
          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  new BigDecimal("101"),
                  "A",
                  "ALLEMP",
                  "REGION",
                  "Emp 1",
                  Optional.empty(),
                  new Defaulted.UseDefault<>()),
              c);
          employeesRepo.insert(
              new EmployeesRowUnsaved(
                  new BigDecimal("102"),
                  "B",
                  "ALLEMP",
                  "REGION",
                  "Emp 2",
                  Optional.empty(),
                  new Defaulted.UseDefault<>()),
              c);

          List<EmployeesRow> all = employeesRepo.selectAll(c);

          assertTrue(all.size() >= 2);
          assertTrue(
              all.stream()
                  .anyMatch(
                      e ->
                          e.empNumber().equals(new BigDecimal("101"))
                              && e.empSuffix().equals("A")));
          assertTrue(
              all.stream()
                  .anyMatch(
                      e ->
                          e.empNumber().equals(new BigDecimal("102"))
                              && e.empSuffix().equals("B")));
        });
  }

  @Test
  public void testDepartmentWithNullBudget() {
    OracleTestHelper.run(
        c -> {
          DepartmentsRow dept =
              new DepartmentsRow("NOBUD", "REGION", "No Budget Dept", Optional.empty());
          DepartmentsId insertedId = departmentsRepo.insert(dept, c);
          DepartmentsRow inserted = departmentsRepo.selectById(insertedId, c).orElseThrow();

          assertFalse(inserted.budget().isPresent());

          Optional<DepartmentsRow> found =
              departmentsRepo.selectById(new DepartmentsId("NOBUD", "REGION"), c);
          assertTrue(found.isPresent());
          assertFalse(found.get().budget().isPresent());
        });
  }

  @Test
  public void testEmployeeWithNullSalary() {
    OracleTestHelper.run(
        c -> {
          departmentsRepo.insert(
              new DepartmentsRow("NOSAL", "REGION", "No Salary Dept", Optional.empty()), c);

          EmployeesId empId =
              employeesRepo.insert(
                  new EmployeesRowUnsaved(
                      new BigDecimal("777"),
                      "N",
                      "NOSAL",
                      "REGION",
                      "No Salary Employee",
                      Optional.empty(),
                      new Defaulted.UseDefault<>()),
                  c);
          EmployeesRow emp = employeesRepo.selectById(empId, c).orElseThrow();

          assertFalse(emp.salary().isPresent());

          Optional<EmployeesRow> found =
              employeesRepo.selectById(new EmployeesId(new BigDecimal("777"), "N"), c);
          assertTrue(found.isPresent());
          assertFalse(found.get().salary().isPresent());
        });
  }
}
