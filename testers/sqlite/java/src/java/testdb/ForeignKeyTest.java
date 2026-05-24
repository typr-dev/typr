package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.customers.*;
import testdb.order_items.*;
import testdb.orders.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Foreign-key relationships: customers → orders → order_items ← products. SQLite enforces FKs only
 * when `PRAGMA foreign_keys = ON`; SqliteTestHelper sets it via the SqliteConfig builder, so
 * violations should throw.
 */
public class ForeignKeyTest {
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final OrderItemsRepoImpl orderItemsRepo = new OrderItemsRepoImpl();

  @Test
  public void testCustomerInsert() {
    SqliteTestHelper.run(
        c -> {
          var customer =
              new CustomersRow(
                  new CustomersId(100L),
                  "John Doe",
                  Optional.of(new Email("john@example.com")),
                  LocalDateTime.of(2025, 1, 1, 12, 0));
          var inserted = customersRepo.insert(customer, c);

          assertNotNull(inserted);
          assertEquals("John Doe", inserted.name());
          assertEquals(Optional.of(new Email("john@example.com")), inserted.email());
        });
  }

  @Test
  public void testProductInsert() {
    SqliteTestHelper.run(
        c -> {
          var product =
              new ProductsRow(
                  new ProductsId(100L),
                  "PROD-100",
                  "Test Product",
                  new BigDecimal("29.99"),
                  Optional.empty());
          var inserted = productsRepo.insert(product, c);

          assertNotNull(inserted);
          assertEquals("PROD-100", inserted.sku());
          assertEquals(0, new BigDecimal("29.99").compareTo(inserted.price()));
        });
  }

  @Test
  public void testOrderWithCustomerFK() {
    SqliteTestHelper.run(
        c -> {
          var customer =
              new CustomersRow(
                  new CustomersId(101L),
                  "Jane Smith",
                  Optional.of(new Email("jane@example.com")),
                  LocalDateTime.of(2025, 1, 2, 12, 0));
          var insertedCustomer = customersRepo.insert(customer, c);

          var order =
              new OrdersRow(
                  new OrdersId(101L),
                  insertedCustomer.customerId(),
                  LocalDate.of(2025, 1, 15),
                  Optional.of(new BigDecimal("99.99")),
                  "pending");

          var insertedOrder = ordersRepo.insert(order, c);

          assertNotNull(insertedOrder);
          assertEquals(insertedCustomer.customerId(), insertedOrder.customerId());
        });
  }

  @Test
  public void testOrderItemsWithCompositePK() {
    SqliteTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(102L),
                  "Test Customer",
                  Optional.empty(),
                  LocalDateTime.of(2025, 1, 1, 0, 0)),
              c);
          var product =
              productsRepo.insert(
                  new ProductsRow(
                      new ProductsId(102L),
                      "PROD-102",
                      "Widget",
                      new BigDecimal("49.99"),
                      Optional.empty()),
                  c);
          var order =
              ordersRepo.insert(
                  new OrdersRow(
                      new OrdersId(102L),
                      new CustomersId(102L),
                      LocalDate.of(2025, 1, 16),
                      Optional.of(new BigDecimal("149.97")),
                      "pending"),
                  c);

          var item =
              new OrderItemsRow(order.orderId(), product.productId(), 3L, new BigDecimal("49.99"));

          var insertedItem = orderItemsRepo.insert(item, c);

          assertEquals(Long.valueOf(3L), insertedItem.quantity());
          assertEquals(0, new BigDecimal("49.99").compareTo(insertedItem.unitPrice()));
        });
  }

  @Test
  public void testForeignKeyEnforcedOnInsert() {
    // Insert an order pointing at a non-existent customer — SQLite (with FK ON) should reject it.
    boolean threw = false;
    try {
      SqliteTestHelper.run(
          c -> {
            ordersRepo.insert(
                new OrdersRow(
                    new OrdersId(9999L),
                    new CustomersId(99999L), // not in DB
                    LocalDate.of(2025, 1, 1),
                    Optional.empty(),
                    "pending"),
                c);
          });
    } catch (RuntimeException e) {
      // SQLite throws SQLITE_CONSTRAINT_FOREIGNKEY → wrapped by foundations
      threw =
          e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key")
              || (e.getCause() instanceof SQLException)
              || e.getMessage() != null && e.getMessage().contains("FOREIGN KEY");
    }
    assertTrue("Expected FK violation, got nothing", threw);
  }

  @Test
  public void testTypeSafeIds() {
    SqliteTestHelper.run(
        c -> {
          var customerId = new CustomersId(200L);
          var productId = new ProductsId(200L);
          var orderId = new OrdersId(200L);

          // These three IDs are distinct types — the compiler refuses to mix them.
          customersRepo.insert(
              new CustomersRow(
                  customerId, "Type Safe", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
              c);
          productsRepo.insert(
              new ProductsRow(
                  productId,
                  "SKU-200",
                  "Type Safe Product",
                  new BigDecimal("1.00"),
                  Optional.empty()),
              c);
          ordersRepo.insert(
              new OrdersRow(
                  orderId, customerId, LocalDate.of(2025, 1, 1), Optional.empty(), "pending"),
              c);

          assertTrue(customersRepo.selectById(customerId, c).isPresent());
          assertTrue(productsRepo.selectById(productId, c).isPresent());
          assertTrue(ordersRepo.selectById(orderId, c).isPresent());
        });
  }
}
