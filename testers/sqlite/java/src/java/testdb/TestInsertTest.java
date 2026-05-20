package testdb;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

/**
 * TestInsert generates random data for required columns; this verifies that the SqliteAdapter arm
 * in ComputedTestInserts produces values that actually round-trip through every supported table.
 */
public class TestInsertTest {
  private final TestInsert testInsert = new TestInsert(new Random(1172222373));

  @Test
  public void testCustomersInsert() {
    SqliteTestHelper.run(
        c -> {
          var row = testInsert.Customers().insert(c);
          assertNotNull(row);
          assertNotNull(row.customerId());
          assertNotNull(row.name());
        });
  }

  @Test
  public void testCustomersWithCustomization() {
    SqliteTestHelper.run(
        c -> {
          var row = testInsert.Customers().with(r -> r.withName("Custom Name")).insert(c);
          assertNotNull(row);
          assertEquals("Custom Name", row.name());
        });
  }

  @Test
  public void testDepartmentsInsert() {
    SqliteTestHelper.run(
        c -> {
          var row = testInsert.Departments().insert(c);
          assertNotNull(row);
          assertNotNull(row.deptCode());
          assertNotNull(row.deptRegion());
          assertNotNull(row.deptName());
        });
  }

  @Test
  public void testProductsInsert() {
    SqliteTestHelper.run(
        c -> {
          var row = testInsert.Products().insert(c);
          assertNotNull(row);
          assertNotNull(row.productId());
          assertNotNull(row.sku());
          assertNotNull(row.name());
          assertNotNull(row.price());
        });
  }

  @Test
  public void testAllScalarTypesInsert() {
    SqliteTestHelper.run(
        c -> {
          var row = testInsert.AllScalarTypes().insert(c);
          assertNotNull(row);
          assertNotNull(row.id());
          assertNotNull(row.colNotNull()); // the only required column
        });
  }

  @Test
  public void testEmployeesWithDepartmentFK() {
    SqliteTestHelper.run(
        c -> {
          var dept = testInsert.Departments().insert(c);
          // SQLite's TestInsert API takes the FK id as a required parameter (codegen wires it to
          // the composite-FK constraint on employees → departments).
          var emp = testInsert.Employees(dept.compositeId()).insert(c);

          assertNotNull(emp);
          assertEquals(dept.deptCode(), emp.deptCode());
          assertEquals(dept.deptRegion(), emp.deptRegion());
        });
  }

  @Test
  public void testOrdersWithCustomerFK() {
    SqliteTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          assertNotNull(order);
          assertEquals(customer.customerId(), order.customerId());
        });
  }

  @Test
  public void testMultipleInserts() {
    SqliteTestHelper.run(
        c -> {
          var row1 = testInsert.Customers().insert(c);
          var row2 = testInsert.Customers().insert(c);
          var row3 = testInsert.Customers().insert(c);

          assertNotEquals(row1.customerId(), row2.customerId());
          assertNotEquals(row2.customerId(), row3.customerId());
          assertNotEquals(row1.customerId(), row3.customerId());
        });
  }

  @Test
  public void testInsertWithSeededRandom() {
    // Reproducibility: two TestInsert instances with the same seed produce the same generated name
    // (modulo any sequence-based ID; we compare names, which come from the alphanumeric generator).
    var name1 =
        SqliteTestHelper.apply(c -> new TestInsert(new Random(123)).Customers().insert(c).name());
    var name2 =
        SqliteTestHelper.apply(c -> new TestInsert(new Random(123)).Customers().insert(c).name());
    assertEquals(name1, name2);
  }
}
