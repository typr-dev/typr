package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
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
 * Tests for foreign key relationships in DuckDB. Tests the ordering system: customers -> orders ->
 * order_items <- products.
 */
public class ForeignKeyTest {
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final OrderItemsRepoImpl orderItemsRepo = new OrderItemsRepoImpl();

  @Test
  public void testCustomerInsert() {
    DuckDbTestHelper.run(
        c -> {
          var customer =
              new CustomersRow(
                  new CustomersId(100),
                  "John Doe",
                  Optional.of(new Email("john@example.com")),
                  LocalDateTime.now(),
                  Optional.of(Priority.high));

          var inserted = customersRepo.insert(customer, c);

          assertNotNull(inserted);
          assertEquals("John Doe", inserted.name());
          assertEquals(Optional.of(new Email("john@example.com")), inserted.email());
          assertEquals(Optional.of(Priority.high), inserted.priority());
        });
  }

  @Test
  public void testProductInsert() {
    DuckDbTestHelper.run(
        c -> {
          var product =
              new ProductsRow(
                  new ProductsId(100),
                  "PROD-100",
                  "Test Product",
                  new BigDecimal("29.99"),
                  Optional.empty());

          var inserted = productsRepo.insert(product, c);

          assertNotNull(inserted);
          assertEquals("PROD-100", inserted.sku());
          assertEquals("Test Product", inserted.name());
          assertEquals(new BigDecimal("29.99"), inserted.price());
        });
  }

  @Test
  public void testOrderWithCustomerFK() {
    DuckDbTestHelper.run(
        c -> {
          // Create customer first
          var customer =
              new CustomersRow(
                  new CustomersId(101),
                  "Jane Smith",
                  Optional.of(new Email("jane@example.com")),
                  LocalDateTime.now(),
                  Optional.empty());
          var insertedCustomer = customersRepo.insert(customer, c);

          // Create order referencing customer
          var order =
              new OrdersRow(
                  new OrdersId(101),
                  insertedCustomer.customerId().value(),
                  LocalDate.now(),
                  Optional.of(new BigDecimal("99.99")),
                  Optional.of("pending"));

          var insertedOrder = ordersRepo.insert(order, c);

          assertNotNull(insertedOrder);
          assertEquals(insertedCustomer.customerId().value(), insertedOrder.customerId());
        });
  }

  @Test
  public void testOrderItemsWithCompositePK() {
    DuckDbTestHelper.run(
        c -> {
          // Create customer
          var customer =
              new CustomersRow(
                  new CustomersId(102),
                  "Test Customer",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty());
          customersRepo.insert(customer, c);

          // Create product
          var product =
              new ProductsRow(
                  new ProductsId(102),
                  "PROD-102",
                  "Widget",
                  new BigDecimal("49.99"),
                  Optional.empty());
          var insertedProduct = productsRepo.insert(product, c);

          // Create order
          var order =
              new OrdersRow(
                  new OrdersId(102),
                  102,
                  LocalDate.now(),
                  Optional.of(new BigDecimal("149.97")),
                  Optional.empty());
          var insertedOrder = ordersRepo.insert(order, c);

          // Create order item with composite FK
          var orderItem =
              new OrderItemsRow(
                  insertedOrder.orderId().value(),
                  insertedProduct.productId().value(),
                  3,
                  new BigDecimal("49.99"));

          var insertedItem = orderItemsRepo.insert(orderItem, c);

          assertNotNull(insertedItem);
          assertEquals(Integer.valueOf(3), insertedItem.quantity());
          assertEquals(new BigDecimal("49.99"), insertedItem.unitPrice());
        });
  }

  @Test
  public void testOrderItemSelectByCompositeId() {
    DuckDbTestHelper.run(
        c -> {
          // Setup: customer, product, order
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(103),
                  "Select Test",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          productsRepo.insert(
              new ProductsRow(
                  new ProductsId(103),
                  "PROD-103",
                  "Select Widget",
                  new BigDecimal("19.99"),
                  Optional.empty()),
              c);

          ordersRepo.insert(
              new OrdersRow(
                  new OrdersId(103), 103, LocalDate.now(), Optional.empty(), Optional.empty()),
              c);

          // Insert order item
          var orderItem = new OrderItemsRow(103, 103, 2, new BigDecimal("19.99"));
          orderItemsRepo.insert(orderItem, c);

          // Select by composite ID
          var id = new OrderItemsId(103, 103);
          var found = orderItemsRepo.selectById(id, c);

          assertTrue(found.isPresent());
          assertEquals(Integer.valueOf(2), found.get().quantity());
        });
  }

  @Test
  public void testMultipleOrderItemsForOrder() {
    DuckDbTestHelper.run(
        c -> {
          // Setup customer
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(104),
                  "Multi Items",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          // Setup multiple products
          var product1 =
              productsRepo.insert(
                  new ProductsRow(
                      new ProductsId(1041),
                      "SKU-1041",
                      "Product A",
                      new BigDecimal("10.00"),
                      Optional.empty()),
                  c);
          var product2 =
              productsRepo.insert(
                  new ProductsRow(
                      new ProductsId(1042),
                      "SKU-1042",
                      "Product B",
                      new BigDecimal("20.00"),
                      Optional.empty()),
                  c);
          var product3 =
              productsRepo.insert(
                  new ProductsRow(
                      new ProductsId(1043),
                      "SKU-1043",
                      "Product C",
                      new BigDecimal("30.00"),
                      Optional.empty()),
                  c);

          // Create order
          var order =
              ordersRepo.insert(
                  new OrdersRow(
                      new OrdersId(104), 104, LocalDate.now(), Optional.empty(), Optional.empty()),
                  c);

          // Add multiple items to order
          orderItemsRepo.insert(new OrderItemsRow(104, 1041, 1, new BigDecimal("10.00")), c);
          orderItemsRepo.insert(new OrderItemsRow(104, 1042, 2, new BigDecimal("20.00")), c);
          orderItemsRepo.insert(new OrderItemsRow(104, 1043, 3, new BigDecimal("30.00")), c);

          // Verify each can be retrieved
          assertTrue(orderItemsRepo.selectById(new OrderItemsId(104, 1041), c).isPresent());
          assertTrue(orderItemsRepo.selectById(new OrderItemsId(104, 1042), c).isPresent());
          assertTrue(orderItemsRepo.selectById(new OrderItemsId(104, 1043), c).isPresent());
        });
  }

  @Test
  public void testOrderItemUpdate() {
    DuckDbTestHelper.run(
        c -> {
          // Setup
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(105),
                  "Update Test",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          productsRepo.insert(
              new ProductsRow(
                  new ProductsId(105),
                  "SKU-105",
                  "Update Product",
                  new BigDecimal("25.00"),
                  Optional.empty()),
              c);
          ordersRepo.insert(
              new OrdersRow(
                  new OrdersId(105), 105, LocalDate.now(), Optional.empty(), Optional.empty()),
              c);

          var item =
              orderItemsRepo.insert(new OrderItemsRow(105, 105, 1, new BigDecimal("25.00")), c);

          // Update quantity
          var updated = item.withQuantity(5);
          orderItemsRepo.update(updated, c);

          var found = orderItemsRepo.selectById(new OrderItemsId(105, 105), c).orElseThrow();
          assertEquals(Integer.valueOf(5), found.quantity());
        });
  }

  @Test
  public void testOrderItemDelete() {
    DuckDbTestHelper.run(
        c -> {
          // Setup
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(106),
                  "Delete Test",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          productsRepo.insert(
              new ProductsRow(
                  new ProductsId(106),
                  "SKU-106",
                  "Delete Product",
                  new BigDecimal("15.00"),
                  Optional.empty()),
              c);
          ordersRepo.insert(
              new OrdersRow(
                  new OrdersId(106), 106, LocalDate.now(), Optional.empty(), Optional.empty()),
              c);

          orderItemsRepo.insert(new OrderItemsRow(106, 106, 1, new BigDecimal("15.00")), c);

          // Delete
          boolean deleted = orderItemsRepo.deleteById(new OrderItemsId(106, 106), c);
          assertTrue(deleted);

          assertFalse(orderItemsRepo.selectById(new OrderItemsId(106, 106), c).isPresent());
        });
  }

  @Test
  public void testTypeSafeIds() {
    DuckDbTestHelper.run(
        c -> {
          // Demonstrate that IDs are type-safe
          var customerId = new CustomersId(200);
          var productId = new ProductsId(200);
          var orderId = new OrdersId(200);

          // These are all different types with value 200 but cannot be confused
          // at compile time:
          // customersRepo.selectById(orderId, c)  // Would be compile error!
          // ordersRepo.selectById(customerId, c)  // Would be compile error!

          // Correct usage
          customersRepo.insert(
              new CustomersRow(
                  customerId, "Type Safe", Optional.empty(), LocalDateTime.now(), Optional.empty()),
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
                  orderId, customerId.value(), LocalDate.now(), Optional.empty(), Optional.empty()),
              c);

          assertTrue(customersRepo.selectById(customerId, c).isPresent());
          assertTrue(productsRepo.selectById(productId, c).isPresent());
          assertTrue(ordersRepo.selectById(orderId, c).isPresent());
        });
  }
}
