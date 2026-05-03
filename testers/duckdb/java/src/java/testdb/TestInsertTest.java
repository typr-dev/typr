package testdb;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

/** Tests for TestInsert functionality - automatic random data generation for testing. */
public class TestInsertTest {
  private final TestInsert testInsert = new TestInsert(new Random(1172222373));

  @Test
  public void testCustomersInsert() {
    DuckDbTestHelper.run(
        c -> {
          // TestInsert generates random data for required fields
          var inserter = testInsert.Customers();

          // Insert and verify
          var row = inserter.insert(c);
          assertNotNull(row);
          assertNotNull(row.customerId());
          assertNotNull(row.name());
        });
  }

  @Test
  public void testCustomersWithCustomization() {
    DuckDbTestHelper.run(
        c -> {
          var inserter = testInsert.Customers();

          // Customize the row before insert
          var row = inserter.with(r -> r.withName("Custom Name")).insert(c);

          assertNotNull(row);
          assertEquals("Custom Name", row.name());
        });
  }

  @Test
  public void testDepartmentsInsert() {
    DuckDbTestHelper.run(
        c -> {
          var inserter = testInsert.Departments();
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.deptCode());
          assertNotNull(row.deptRegion());
          assertNotNull(row.deptName());
        });
  }

  @Test
  public void testProductsInsert() {
    DuckDbTestHelper.run(
        c -> {
          var inserter = testInsert.Products();
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.productId());
          assertNotNull(row.sku());
          assertNotNull(row.name());
          assertNotNull(row.price());
        });
  }

  @Test
  public void testAllScalarTypesInsert() {
    DuckDbTestHelper.run(
        c -> {
          var inserter = testInsert.AllScalarTypes();
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.id());
          assertNotNull(row.colNotNull()); // Only required field
        });
  }

  @Test
  public void testEmployeesWithDepartmentFK() {
    DuckDbTestHelper.run(
        c -> {
          // First create a department (FK constraint)
          var dept = testInsert.Departments().insert(c);

          // Now create employee referencing department
          var emp =
              testInsert
                  .Employees()
                  .with(e -> e.withDeptCode(dept.deptCode()).withDeptRegion(dept.deptRegion()))
                  .insert(c);

          assertNotNull(emp);
          assertEquals(dept.deptCode(), emp.deptCode());
          assertEquals(dept.deptRegion(), emp.deptRegion());
        });
  }

  @Test
  public void testOrdersWithCustomerFK() {
    DuckDbTestHelper.run(
        c -> {
          // Create customer first
          var customer = testInsert.Customers().insert(c);

          // Create order referencing customer
          var order =
              testInsert
                  .Orders()
                  .with(r -> r.withCustomerId(customer.customerId().value()))
                  .insert(c);

          assertNotNull(order);
          assertEquals(customer.customerId().value(), order.customerId());
        });
  }

  @Test
  public void testMultipleInserts() {
    DuckDbTestHelper.run(
        c -> {
          // Insert multiple rows using same TestInsert instance
          var row1 = testInsert.Customers().insert(c);
          var row2 = testInsert.Customers().insert(c);
          var row3 = testInsert.Customers().insert(c);

          // Each should have unique ID
          assertNotEquals(row1.customerId(), row2.customerId());
          assertNotEquals(row2.customerId(), row3.customerId());
          assertNotEquals(row1.customerId(), row3.customerId());
        });
  }

  @Test
  public void testInsertWithSeededRandom() {
    // Test that seeded randoms produce reproducible data
    // We verify by creating rows in separate transactions to avoid PK conflicts
    var name1 =
        DuckDbTestHelper.apply(
            c -> {
              var testInsert1 = new TestInsert(new Random(123));
              return testInsert1.Customers().insert(c).name();
            });
    var name2 =
        DuckDbTestHelper.apply(
            c -> {
              var testInsert2 = new TestInsert(new Random(123));
              return testInsert2.Customers().insert(c).name();
            });

    assertEquals(name1, name2);
  }

  @Test
  public void testDepartmentsGeneratesValidData() {
    DuckDbTestHelper.run(
        c -> {
          // Verify generated row has valid data
          var row = testInsert.Departments().insert(c);

          assertNotNull(row);
          assertNotNull(row.deptCode());
          assertNotNull(row.deptRegion());
          assertNotNull(row.deptName());
        });
  }
}
