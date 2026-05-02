package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customer_orders.*;
import testdb.customer_summary.*;
import testdb.orders_by_customer.*;

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/db2/.
 */
public class SqlScriptTest {
  private final TestInsert testInsert = new TestInsert(new Random(1526571155));
  private final CustomerOrdersSqlRepoImpl customerOrdersRepo = new CustomerOrdersSqlRepoImpl();
  private final CustomerSummarySqlRepoImpl customerSummaryRepo = new CustomerSummarySqlRepoImpl();
  private final OrdersByCustomerSqlRepoImpl ordersByCustomerRepo =
      new OrdersByCustomerSqlRepoImpl();

  @Test
  public void testCustomerOrders() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order =
              testInsert
                  .Orders(customer.customerId())
                  .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("150.00"))))
                  .insert(c);

          var results = customerOrdersRepo.apply(customer.customerId().value(), c);

          assertTrue(results.size() >= 1);
          assertEquals(customer.customerId().value(), results.get(0).customerId());
        });
  }

  @Test
  public void testCustomerOrdersMultiple() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          testInsert.Orders(customer.customerId()).insert(c);
          testInsert.Orders(customer.customerId()).insert(c);
          testInsert.Orders(customer.customerId()).insert(c);

          var results = customerOrdersRepo.apply(customer.customerId().value(), c);

          assertEquals(3, results.size());
        });
  }

  @Test
  public void testCustomerSummary() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("100.00"))))
              .insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("200.00"))))
              .insert(c);

          var results = customerSummaryRepo.apply(c);

          assertTrue(results.size() >= 1);
          var summary =
              results.stream()
                  .filter(r -> r.customerId().equals(customer.customerId().value()))
                  .findFirst();
          assertTrue(summary.isPresent());
          assertEquals(Long.valueOf(2L), Long.valueOf(summary.get().orderCount().orElse(0)));
        });
  }

  @Test
  public void testCustomerSummaryNoOrders() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          var results = customerSummaryRepo.apply(c);

          assertTrue(results.size() >= 1);
          var summary =
              results.stream()
                  .filter(r -> r.customerId().equals(customer.customerId().value()))
                  .findFirst();
          assertTrue(summary.isPresent());
          assertEquals(Long.valueOf(0L), Long.valueOf(summary.get().orderCount().orElse(0)));
          assertEquals(
              0, BigDecimal.ZERO.compareTo(summary.get().totalSpent().orElse(BigDecimal.ZERO)));
        });
  }

  @Test
  public void testOrdersByCustomer() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order1 = testInsert.Orders(customer.customerId()).insert(c);
          var order2 = testInsert.Orders(customer.customerId()).insert(c);
          // SQL uses INNER JOIN with order_items, so we need to create order items
          testInsert.OrderItems(order1.orderId()).insert(c);
          testInsert.OrderItems(order2.orderId()).insert(c);

          var results = ordersByCustomerRepo.apply(customer.customerId().value(), c);

          assertEquals(2, results.size());
        });
  }
}
