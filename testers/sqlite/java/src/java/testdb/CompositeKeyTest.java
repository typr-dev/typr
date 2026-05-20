package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.Test;
import testdb.departments.*;
import testdb.employees.*;

/**
 * Composite primary keys: departments (VARCHAR, VARCHAR) and employees (INTEGER, VARCHAR), plus the
 * composite foreign key from employees → departments.
 *
 * <p>Schema seeds two departments (IT/US-WEST, HR/US-EAST) and two employees (1001/A, 1002/B); each
 * test runs inside a per-test rollback so it sees those rows but its own inserts vanish on exit.
 */
public class CompositeKeyTest {
  private final DepartmentsRepoImpl departmentsRepo = new DepartmentsRepoImpl();
  private final EmployeesRepoImpl employeesRepo = new EmployeesRepoImpl();

  // ==================== Departments (String, String) Composite Key ====================

  @Test
  public void testDepartmentsInsert() {
    SqliteTestHelper.run(
        c -> {
          var dept =
              new DepartmentsRow(
                  "ENG", "EU-NORTH", "Engineering Europe", Optional.of(new BigDecimal("750000")));
          var inserted = departmentsRepo.insert(dept, c);

          assertNotNull(inserted);
          assertEquals("ENG", inserted.deptCode());
          assertEquals("EU-NORTH", inserted.deptRegion());
          assertEquals("Engineering Europe", inserted.deptName());
          assertEquals(0, new BigDecimal("750000").compareTo(inserted.budget().orElseThrow()));
        });
  }

  @Test
  public void testDepartmentsSelectByCompositeId() {
    SqliteTestHelper.run(
        c -> {
          var id = new DepartmentsId("IT", "US-WEST"); // from seed
          var found = departmentsRepo.selectById(id, c);

          assertTrue(found.isPresent());
          assertEquals("IT", found.get().deptCode());
          assertEquals("US-WEST", found.get().deptRegion());
          assertEquals("Information Technology", found.get().deptName());
        });
  }

  @Test
  public void testDepartmentsCompositeIdFromRow() {
    SqliteTestHelper.run(
        c -> {
          var dept =
              new DepartmentsRow(
                  "SALES", "APAC", "Sales APAC", Optional.of(new BigDecimal("500000")));
          var inserted = departmentsRepo.insert(dept, c);

          var compositeId = inserted.compositeId();
          assertEquals("SALES", compositeId.deptCode());
          assertEquals("APAC", compositeId.deptRegion());

          var found = departmentsRepo.selectById(compositeId, c);
          assertTrue(found.isPresent());
          assertEquals(inserted, found.get());
        });
  }

  @Test
  public void testDepartmentsUpdate() {
    SqliteTestHelper.run(
        c -> {
          var dept =
              new DepartmentsRow(
                  "FINANCE", "US-CENTRAL", "Finance", Optional.of(new BigDecimal("800000")));
          var inserted = departmentsRepo.insert(dept, c);

          var updated = inserted.withBudget(Optional.of(new BigDecimal("900000")));
          boolean wasUpdated = departmentsRepo.update(updated, c);
          assertTrue(wasUpdated);

          var found = departmentsRepo.selectById(inserted.compositeId(), c).orElseThrow();
          assertEquals(0, new BigDecimal("900000").compareTo(found.budget().orElseThrow()));
        });
  }

  @Test
  public void testDepartmentsDelete() {
    SqliteTestHelper.run(
        c -> {
          var dept = new DepartmentsRow("TEMP", "TEMP-REGION", "Temporary", Optional.empty());
          var inserted = departmentsRepo.insert(dept, c);

          boolean deleted = departmentsRepo.deleteById(inserted.compositeId(), c);
          assertTrue(deleted);

          var found = departmentsRepo.selectById(inserted.compositeId(), c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testDepartmentsMultipleSameCode() {
    SqliteTestHelper.run(
        c -> {
          var d1 = new DepartmentsRow("ENG2", "US", "Engineering US", Optional.empty());
          var d2 = new DepartmentsRow("ENG2", "EU", "Engineering EU", Optional.empty());
          var d3 = new DepartmentsRow("ENG2", "APAC", "Engineering APAC", Optional.empty());

          departmentsRepo.insert(d1, c);
          departmentsRepo.insert(d2, c);
          departmentsRepo.insert(d3, c);

          assertTrue(departmentsRepo.selectById(new DepartmentsId("ENG2", "US"), c).isPresent());
          assertTrue(departmentsRepo.selectById(new DepartmentsId("ENG2", "EU"), c).isPresent());
          assertTrue(departmentsRepo.selectById(new DepartmentsId("ENG2", "APAC"), c).isPresent());
        });
  }

  // ==================== Employees (Long, String) Composite Key ====================

  @Test
  public void testEmployeesInsert() {
    SqliteTestHelper.run(
        c -> {
          // Schema already has IT/US-WEST as a department, reuse it for FK
          var emp =
              new EmployeesRow(
                  2001L,
                  "A",
                  "IT",
                  "US-WEST",
                  "Alice Johnson",
                  Optional.of(new BigDecimal("95000")),
                  LocalDate.of(2025, 1, 15));
          var inserted = employeesRepo.insert(emp, c);

          assertNotNull(inserted);
          assertEquals(Long.valueOf(2001L), inserted.empNumber());
          assertEquals("A", inserted.empSuffix());
          assertEquals("Alice Johnson", inserted.empName());
        });
  }

  @Test
  public void testEmployeesSelectByCompositeId() {
    SqliteTestHelper.run(
        c -> {
          var id = new EmployeesId(1001L, "A"); // from seed
          var found = employeesRepo.selectById(id, c);

          assertTrue(found.isPresent());
          assertEquals(Long.valueOf(1001L), found.get().empNumber());
          assertEquals("A", found.get().empSuffix());
          assertEquals("Alice Johnson", found.get().empName());
        });
  }

  @Test
  public void testEmployeesCompositeIdFromRow() {
    SqliteTestHelper.run(
        c -> {
          var emp =
              new EmployeesRow(
                  3001L,
                  "C",
                  "HR",
                  "US-EAST",
                  "Carol White",
                  Optional.empty(),
                  LocalDate.of(2025, 3, 1));
          var inserted = employeesRepo.insert(emp, c);

          var compositeId = inserted.compositeId();
          assertEquals(Long.valueOf(3001L), compositeId.empNumber());
          assertEquals("C", compositeId.empSuffix());

          var found = employeesRepo.selectById(compositeId, c);
          assertTrue(found.isPresent());
        });
  }

  @Test
  public void testEmployeesUpdate() {
    SqliteTestHelper.run(
        c -> {
          var emp =
              new EmployeesRow(
                  4001L,
                  "D",
                  "IT",
                  "US-WEST",
                  "David Brown",
                  Optional.of(new BigDecimal("120000")),
                  LocalDate.of(2025, 4, 1));
          var inserted = employeesRepo.insert(emp, c);

          var updated = inserted.withSalary(Optional.of(new BigDecimal("130000")));
          employeesRepo.update(updated, c);

          var found = employeesRepo.selectById(inserted.compositeId(), c).orElseThrow();
          assertEquals(0, new BigDecimal("130000").compareTo(found.salary().orElseThrow()));
        });
  }

  @Test
  public void testEmployeesDelete() {
    SqliteTestHelper.run(
        c -> {
          var emp =
              new EmployeesRow(
                  5001L,
                  "X",
                  "IT",
                  "US-WEST",
                  "To Be Deleted",
                  Optional.empty(),
                  LocalDate.of(2025, 5, 1));
          var inserted = employeesRepo.insert(emp, c);

          boolean deleted = employeesRepo.deleteById(inserted.compositeId(), c);
          assertTrue(deleted);

          var found = employeesRepo.selectById(inserted.compositeId(), c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testEmployeesMultipleSameNumber() {
    SqliteTestHelper.run(
        c -> {
          var e1 =
              new EmployeesRow(
                  9999L,
                  "A",
                  "IT",
                  "US-WEST",
                  "Employee A",
                  Optional.empty(),
                  LocalDate.of(2025, 1, 1));
          var e2 =
              new EmployeesRow(
                  9999L,
                  "B",
                  "IT",
                  "US-WEST",
                  "Employee B",
                  Optional.empty(),
                  LocalDate.of(2025, 1, 1));
          var e3 =
              new EmployeesRow(
                  9999L,
                  "C",
                  "IT",
                  "US-WEST",
                  "Employee C",
                  Optional.empty(),
                  LocalDate.of(2025, 1, 1));

          employeesRepo.insert(e1, c);
          employeesRepo.insert(e2, c);
          employeesRepo.insert(e3, c);

          assertTrue(employeesRepo.selectById(new EmployeesId(9999L, "A"), c).isPresent());
          assertTrue(employeesRepo.selectById(new EmployeesId(9999L, "B"), c).isPresent());
          assertTrue(employeesRepo.selectById(new EmployeesId(9999L, "C"), c).isPresent());
        });
  }

  // ==================== Composite FK Relationship ====================

  @Test
  public void testEmployeeDepartmentForeignKey() {
    SqliteTestHelper.run(
        c -> {
          var dept =
              new DepartmentsRow(
                  "FK_TEST", "FK_REGION", "FK Test Dept", Optional.of(new BigDecimal("500000")));
          departmentsRepo.insert(dept, c);

          var emp =
              new EmployeesRow(
                  6001L,
                  "F",
                  "FK_TEST",
                  "FK_REGION",
                  "FK Employee",
                  Optional.of(new BigDecimal("60000")),
                  LocalDate.of(2025, 6, 1));
          var insertedEmp = employeesRepo.insert(emp, c);

          assertEquals(dept.deptCode(), insertedEmp.deptCode());
          assertEquals(dept.deptRegion(), insertedEmp.deptRegion());

          var foundDept =
              departmentsRepo.selectById(
                  new DepartmentsId(insertedEmp.deptCode(), insertedEmp.deptRegion()), c);
          assertTrue(foundDept.isPresent());
        });
  }
}
