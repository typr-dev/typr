package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.maria.Inet4;
import dev.typr.foundations.data.maria.Inet6;
import java.time.Year;
import java.util.Random;
import org.junit.Test;
import testdb.userdefined.Email;
import testdb.userdefined.FirstName;
import testdb.userdefined.LastName;

/** Tests for TestInsert functionality - automatic random data generation for testing. */
public class TestInsertTest {
  private final TestInsert testInsert = new TestInsert(new Random(42));

  @Test
  public void testMariatestIdentityInsert() {
    MariaDbTestHelper.run(
        c -> {
          // TestInsert generates random data for required fields
          var inserter = testInsert.MariatestIdentity();

          // Insert and verify
          var row = inserter.insert(c);
          assertNotNull(row);
          assertNotNull(row.id());
          assertNotNull(row.name());
        });
  }

  @Test
  public void testMariatestIdentityWithCustomization() {
    MariaDbTestHelper.run(
        c -> {
          var inserter = testInsert.MariatestIdentity();

          // Customize the row before insert
          var row = inserter.with(r -> r.withName("Custom Name")).insert(c);

          assertNotNull(row);
          assertEquals("Custom Name", row.name());
        });
  }

  @Test
  public void testMariatestInsert() {
    MariaDbTestHelper.run(
        c -> {
          // Mariatest requires additional parameters for complex types
          var inserter =
              testInsert.Mariatest(
                  new byte[] {(byte) 0xFF},
                  new byte[] {(byte) 0x01},
                  new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                  new byte[] {1, 2, 3},
                  new byte[] {4, 5, 6},
                  new byte[] {7, 8, 9},
                  new byte[] {10, 11, 12},
                  new byte[] {13, 14, 15},
                  Year.of(2025),
                  new Inet4("192.168.1.1"),
                  new Inet6("::1"));

          var row = inserter.insert(c);
          assertNotNull(row);
          assertNotNull(row.intCol());
        });
  }

  @Test
  public void testCustomerStatusInsert() {
    MariaDbTestHelper.run(
        c -> {
          var inserter = testInsert.CustomerStatus();
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.statusCode());
          assertNotNull(row.description());
        });
  }

  @Test
  public void testCustomersInsertWithForeignKey() {
    MariaDbTestHelper.run(
        c -> {
          // First create the required customer status
          var status = testInsert.CustomerStatus().insert(c);

          // Now create a customer - requires password_hash
          var customer =
              testInsert
                  .Customers(new byte[] {1, 2, 3})
                  .with(
                      r ->
                          r.withEmail(new Email("customer@example.com"))
                              .withFirstName(new FirstName("Test"))
                              .withLastName(new LastName("User")))
                  .insert(c);

          assertNotNull(customer);
          assertNotNull(customer.customerId());
          assertNotNull(customer.email());
        });
  }

  @Test
  public void testOrdersInsertChain() {
    MariaDbTestHelper.run(
        c -> {
          // Create required dependencies
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Create an order
          var order = testInsert.Orders(customer.customerId()).insert(c);

          assertNotNull(order);
          assertNotNull(order.orderId());
          assertEquals(customer.customerId(), order.customerId());
        });
  }

  @Test
  public void testMariatestUniqueInsert() {
    MariaDbTestHelper.run(
        c -> {
          var inserter =
              testInsert.MariatestUnique().with(r -> r.withEmail(new Email("unique@example.com")));
          var row = inserter.insert(c);

          assertNotNull(row);
          assertNotNull(row.id());
          assertNotNull(row.email());
          assertNotNull(row.code());
          assertNotNull(row.category());
        });
  }

  @Test
  public void testMariatestnullInsert() {
    MariaDbTestHelper.run(
        c -> {
          // Mariatestnull has all nullable columns - use short constructor
          var inserter = testInsert.Mariatestnull();
          var row = inserter.insert(c);

          assertNotNull(row);
          // Fields populated by TestInsert should have values
          // (TestInsert generates random data for non-defaulted fields)
        });
  }

  @Test
  public void testMultipleInserts() {
    MariaDbTestHelper.run(
        c -> {
          // Insert multiple rows using same TestInsert instance
          var row1 = testInsert.MariatestIdentity().insert(c);
          var row2 = testInsert.MariatestIdentity().insert(c);
          var row3 = testInsert.MariatestIdentity().insert(c);

          assertNotEquals(row1.id(), row2.id());
          assertNotEquals(row2.id(), row3.id());
          assertNotEquals(row1.id(), row3.id());
        });
  }

  @Test
  public void testInsertWithSeededRandom() {
    MariaDbTestHelper.run(
        c -> {
          // Using seeded random for reproducible tests
          var testInsert1 = new TestInsert(new Random(123));
          var testInsert2 = new TestInsert(new Random(123));

          // Both should generate same random names (testing the random seed behavior)
          var row1 = testInsert1.MariatestIdentity().insert(c);
          var row2 = testInsert2.MariatestIdentity().insert(c);

          assertEquals(row1.name(), row2.name());
        });
  }
}
