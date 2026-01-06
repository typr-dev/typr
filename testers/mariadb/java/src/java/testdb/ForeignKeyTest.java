package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customer_status.*;
import testdb.customers.*;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.orders.*;
import testdb.userdefined.Email;
import testdb.userdefined.FirstName;
import testdb.userdefined.LastName;

/**
 * Tests for foreign key relationships in the MariaDB ordering system. Tests type-safe FK
 * references.
 */
public class ForeignKeyTest {
  private final CustomerStatusRepoImpl customerStatusRepo = new CustomerStatusRepoImpl();
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final TestInsert testInsert = new TestInsert(new Random(42));

  @Test
  public void testCustomerStatusInsert() {
    MariaDbTestHelper.run(
        c -> {
          var status =
              new CustomerStatusRowUnsaved(
                  new CustomerStatusId("active_test"), "Active Test Status", new UseDefault());
          var inserted = customerStatusRepo.insert(status, c);

          assertNotNull(inserted);
          assertEquals(new CustomerStatusId("active_test"), inserted.statusCode());
          assertEquals("Active Test Status", inserted.description());
        });
  }

  @Test
  public void testCustomerWithForeignKeyToStatus() {
    MariaDbTestHelper.run(
        c -> {
          // First create a customer status
          var status =
              customerStatusRepo.insert(
                  new CustomerStatusRowUnsaved(
                      new CustomerStatusId("verified"), "Verified Customer", new UseDefault()),
                  c);

          // Create a customer with FK to the status - use short constructor
          var customer =
              new CustomersRowUnsaved(
                  new Email("test@example.com"),
                  new byte[] {1, 2, 3, 4},
                  new FirstName("John"),
                  new LastName("Doe"));

          var insertedCustomer = customersRepo.insert(customer, c);

          assertNotNull(insertedCustomer);
          assertNotNull(insertedCustomer.customerId());
          assertEquals(new Email("test@example.com"), insertedCustomer.email());
          assertEquals(new FirstName("John"), insertedCustomer.firstName());
          assertEquals(new LastName("Doe"), insertedCustomer.lastName());
        });
  }

  @Test
  public void testOrderWithForeignKeyToCustomer() {
    MariaDbTestHelper.run(
        c -> {
          // Create customer status first
          var status = testInsert.CustomerStatus().insert(c);

          // Create a customer
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Create an order with FK to customer - use short constructor
          var order =
              new OrdersRowUnsaved(
                  "ORD-001",
                  customer.customerId(), // FK to customer
                  new BigDecimal("89.99"),
                  new BigDecimal("99.99"));

          var insertedOrder = ordersRepo.insert(order, c);

          assertNotNull(insertedOrder);
          assertNotNull(insertedOrder.orderId());
          assertEquals("ORD-001", insertedOrder.orderNumber());
          assertEquals(customer.customerId(), insertedOrder.customerId());
        });
  }

  @Test
  public void testOrderLookupByCustomer() {
    MariaDbTestHelper.run(
        c -> {
          // Create dependencies
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Create multiple orders for the same customer
          var order1 = testInsert.Orders(customer.customerId()).insert(c);
          var order2 = testInsert.Orders(customer.customerId()).insert(c);

          // Verify we can find orders
          var foundOrder1 = ordersRepo.selectById(order1.orderId(), c);
          var foundOrder2 = ordersRepo.selectById(order2.orderId(), c);

          assertTrue(foundOrder1.isPresent());
          assertTrue(foundOrder2.isPresent());
          assertEquals(customer.customerId(), foundOrder1.get().customerId());
          assertEquals(customer.customerId(), foundOrder2.get().customerId());
        });
  }

  @Test
  public void testTypeSafeForeignKeyIds() {
    MariaDbTestHelper.run(
        c -> {
          // Demonstrate that IDs are type-safe
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          // These are all different types and cannot be confused
          CustomerStatusId statusId = status.statusCode();
          CustomersId customerId = customer.customerId();
          OrdersId orderId = order.orderId();

          // Can't accidentally pass wrong ID type (would be compile error):
          // ordersRepo.selectById(customerId, c);  // Compile error!
          // customersRepo.selectById(orderId, c);  // Compile error!

          // Correct usage
          var foundOrder = ordersRepo.selectById(orderId, c);
          assertTrue(foundOrder.isPresent());
        });
  }

  @Test
  public void testCustomerUpdateStatus() {
    MariaDbTestHelper.run(
        c -> {
          // Create initial customer
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Create a new status
          var newStatus =
              customerStatusRepo.insert(
                  new CustomerStatusRowUnsaved(
                      new CustomerStatusId("premium"), "Premium Customer", new UseDefault()),
                  c);

          // Update customer to use new status
          var updated = customer.withStatus(newStatus.statusCode());
          customersRepo.update(updated, c);

          // Verify update
          var found = customersRepo.selectById(customer.customerId(), c).orElseThrow();
          assertEquals(newStatus.statusCode(), found.status());
        });
  }

  @Test
  public void testOptionalFieldsOnCustomer() {
    MariaDbTestHelper.run(
        c -> {
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Optional fields should be empty by default
          assertTrue(customer.phone().isEmpty());
          assertTrue(customer.preferences().isEmpty());
          assertTrue(customer.notes().isEmpty());
          assertTrue(customer.lastLoginAt().isEmpty());

          // Update with values
          var updated =
              customer
                  .withPhone(Optional.of("+1-555-1234"))
                  .withPreferences(Optional.of(new Json("{\"theme\": \"dark\"}")))
                  .withNotes(Optional.of("VIP customer"));

          customersRepo.update(updated, c);

          var found = customersRepo.selectById(customer.customerId(), c).orElseThrow();
          assertEquals(Optional.of("+1-555-1234"), found.phone());
          assertEquals(Optional.of(new Json("{\"theme\": \"dark\"}")), found.preferences());
          assertEquals(Optional.of("VIP customer"), found.notes());
        });
  }
}
