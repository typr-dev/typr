package testdb;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;
import testdb.userdefined.Email;

/**
 * Tests for the TestInsert helper class that generates random test data. Tests seeded randomness,
 * customization, and foreign key handling.
 */
public class TestInsertTest {
  private final TestInsert testInsert = new TestInsert(new Random(42));

  @Test
  public void testCustomersInsert() {
    SqlServerTestHelper.run(
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
    SqlServerTestHelper.run(
        c -> {
          var inserter = testInsert.Customers();

          var row = inserter.with(r -> r.withName("Custom Name")).insert(c);

          assertNotNull(row);
          assertEquals("Custom Name", row.name());
        });
  }

  @Test
  public void testProductsInsert() {
    SqlServerTestHelper.run(
        c -> {
          var inserter = testInsert.Products();
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.productId());
          assertNotNull(row.name());
          assertNotNull(row.price());
        });
  }

  @Test
  public void testAllScalarTypesInsert() {
    SqlServerTestHelper.run(
        c -> {
          var row = testInsert.AllScalarTypes().insert(c);

          assertNotNull(row);
          assertNotNull(row.id());
          assertNotNull(row.colRowversion());
        });
  }

  @Test
  public void testOrdersWithCustomerFK() {
    SqlServerTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          var order = testInsert.Orders(customer.customerId()).insert(c);

          assertNotNull(order);
          assertEquals(customer.customerId(), order.customerId());
        });
  }

  @Test
  public void testOrderItemsWithFKs() {
    SqlServerTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var product = testInsert.Products().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          var orderItem = testInsert.OrderItems(order.orderId(), product.productId()).insert(c);

          assertNotNull(orderItem);
          assertEquals(order.orderId(), orderItem.orderId());
          assertEquals(product.productId(), orderItem.productId());
        });
  }

  @Test
  public void testMultipleInserts() {
    SqlServerTestHelper.run(
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
  public void testChainedCustomization() {
    SqlServerTestHelper.run(
        c -> {
          var row =
              testInsert
                  .Customers()
                  .with(r -> r.withName("First"))
                  .with(r -> r.withEmail(new Email("first@test.com")))
                  .insert(c);

          assertEquals("First", row.name());
          assertEquals(new Email("first@test.com"), row.email());
        });
  }
}
