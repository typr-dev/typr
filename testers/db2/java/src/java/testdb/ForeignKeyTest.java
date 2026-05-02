package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customers.*;
import testdb.order_items.*;
import testdb.orders.*;

/**
 * Tests for foreign key relationships between tables. Tests orders -> customers and order_items ->
 * orders relationships.
 */
public class ForeignKeyTest {
  private final TestInsert testInsert = new TestInsert(new Random(744282577));
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final OrderItemsRepoImpl orderItemsRepo = new OrderItemsRepoImpl();

  @Test
  public void testOrderReferencesCustomer() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order =
              testInsert
                  .Orders(customer.customerId())
                  .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("99.99"))))
                  .insert(c);

          assertNotNull(order);
          assertEquals(customer.customerId(), order.customerId());

          var foundCustomer = customersRepo.selectById(order.customerId(), c);
          assertTrue(foundCustomer.isPresent());
          assertEquals(customer.name(), foundCustomer.get().name());
        });
  }

  @Test
  public void testOrderItemsReferencesOrder() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          var orderItem =
              testInsert
                  .OrderItems(order.orderId())
                  .with(r -> r.withQuantity(5).withUnitPrice(new BigDecimal("19.99")))
                  .insert(c);

          assertNotNull(orderItem);
          assertEquals(order.orderId(), orderItem.orderId());

          var foundOrder = ordersRepo.selectById(orderItem.orderId(), c);
          assertTrue(foundOrder.isPresent());
        });
  }

  @Test
  public void testMultipleOrdersForCustomer() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          var order1 =
              testInsert
                  .Orders(customer.customerId())
                  .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("100.00"))))
                  .insert(c);
          var order2 =
              testInsert
                  .Orders(customer.customerId())
                  .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("200.00"))))
                  .insert(c);
          var order3 =
              testInsert
                  .Orders(customer.customerId())
                  .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("300.00"))))
                  .insert(c);

          assertEquals(customer.customerId(), order1.customerId());
          assertEquals(customer.customerId(), order2.customerId());
          assertEquals(customer.customerId(), order3.customerId());

          assertNotEquals(order1.orderId(), order2.orderId());
          assertNotEquals(order2.orderId(), order3.orderId());
        });
  }

  @Test
  public void testMultipleItemsForOrder() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);

          var item1 = testInsert.OrderItems(order.orderId()).with(r -> r.withQuantity(2)).insert(c);
          var item2 = testInsert.OrderItems(order.orderId()).with(r -> r.withQuantity(3)).insert(c);

          assertEquals(order.orderId(), item1.orderId());
          assertEquals(order.orderId(), item2.orderId());
        });
  }

  @Test
  public void testCascadingForeignKeys() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order = testInsert.Orders(customer.customerId()).insert(c);
          var orderItem = testInsert.OrderItems(order.orderId()).insert(c);

          var foundItem = orderItemsRepo.selectById(orderItem.id(), c);
          assertTrue(foundItem.isPresent());

          var foundOrder = ordersRepo.selectById(foundItem.get().orderId(), c);
          assertTrue(foundOrder.isPresent());

          var foundCustomer = customersRepo.selectById(foundOrder.get().customerId(), c);
          assertTrue(foundCustomer.isPresent());

          assertEquals(customer.customerId(), foundCustomer.get().customerId());
        });
  }
}
