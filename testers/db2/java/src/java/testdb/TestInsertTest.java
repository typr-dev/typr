package testdb;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

/**
 * Tests for the TestInsert helper class that generates random test data. Tests seeded randomness,
 * customization, and foreign key handling.
 */
public class TestInsertTest {
  private final TestInsert testInsert = new TestInsert(new Random(505111434));

  @Test
  public void testCustomersInsert() {
    Db2TestHelper.run(
        c -> {
          var inserter = testInsert.Customers();

          var row = inserter.insert(c);
          assertNotNull(row);
          assertNotNull(row.customerId());
          assertNotNull(row.name());
        });
  }

  @Test
  public void testCustomersWithCustomization() {
    Db2TestHelper.run(
        c -> {
          var inserter = testInsert.Customers();

          var row = inserter.with(r -> r.withName("Custom Name")).insert(c);

          assertNotNull(row);
          assertEquals("Custom Name", row.name());
        });
  }

  @Test
  public void testOrdersWithCustomerFK() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          var order = testInsert.Orders(customer.customerId()).insert(c);

          assertNotNull(order);
          assertEquals(customer.customerId(), order.customerId());
        });
  }

  @Test
  public void testOrderItemsWithFK() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          var orderItem = testInsert.OrderItems(order.orderId()).insert(c);

          assertNotNull(orderItem);
          assertEquals(order.orderId(), orderItem.orderId());
        });
  }

  @Test
  public void testMultipleInserts() {
    Db2TestHelper.run(
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
    Db2TestHelper.run(
        c -> {
          var testInsert1 = new TestInsert(new Random(123));
          var testInsert2 = new TestInsert(new Random(123));

          var row1 = testInsert1.Customers().insert(c);
          // Use different email to avoid UNIQUE constraint violation (same seed = same email)
          var row2 = testInsert2.Customers().with(r -> r.withEmail("unique2@test.com")).insert(c);

          assertEquals(row1.name(), row2.name());
        });
  }

  @Test
  public void testChainedCustomization() {
    Db2TestHelper.run(
        c -> {
          var row =
              testInsert
                  .Customers()
                  .with(r -> r.withName("First"))
                  .with(r -> r.withEmail("first@test.com"))
                  .insert(c);

          assertEquals("First", row.name());
          assertEquals("first@test.com", row.email());
        });
  }

  @Test
  public void testIdentityAlwaysInsert() {
    Db2TestHelper.run(
        c -> {
          var row = testInsert.Db2testIdentityAlways().insert(c);

          assertNotNull(row);
          assertNotNull(row.id());
        });
  }

  @Test
  public void testIdentityDefaultInsert() {
    Db2TestHelper.run(
        c -> {
          var row = testInsert.Db2testIdentityDefault().insert(c);

          assertNotNull(row);
          assertNotNull(row.id());
        });
  }

  @Test
  public void testDistinctTypeInsert() {
    Db2TestHelper.run(
        c -> {
          var email = new EmailAddress("test@example.com");
          var row = testInsert.DistinctTypeTest(email).insert(c);

          assertNotNull(row);
          assertEquals(email, row.email());
        });
  }

  @Test
  public void testNullabilityTestInsert() {
    Db2TestHelper.run(
        c -> {
          var row = testInsert.NullabilityTest().insert(c);

          assertNotNull(row);
          assertNotNull(row.requiredCol());
        });
  }
}
