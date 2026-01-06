package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customer_orders_summary.*;
import testdb.customers.*;
import testdb.find_customers_by_email.*;
import testdb.orders.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/sqlserver/.
 */
public class SqlScriptTest {
  private final TestInsert testInsert = new TestInsert(new Random(42));
  private final CustomerOrdersSummarySqlRepoImpl ordersSummaryRepo =
      new CustomerOrdersSummarySqlRepoImpl();
  private final FindCustomersByEmailSqlRepoImpl findByEmailRepo =
      new FindCustomersByEmailSqlRepoImpl();

  @Test
  public void testCustomerOrdersSummaryNoFilters() {
    SqlServerTestHelper.run(
        c -> {
          var customer = testInsert.Customers().with(r -> r.withName("Summary Test")).insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("150.00")))
              .insert(c);

          var results = ordersSummaryRepo.apply(Optional.empty(), Optional.empty(), c);

          assertTrue(results.size() >= 1);
          var customerSummary =
              results.stream()
                  .filter(r -> r.customerId().equals(customer.customerId()))
                  .findFirst();
          assertTrue(customerSummary.isPresent());
          assertEquals(
              1, customerSummary.get().orderCount().map(OrdersId::value).orElse(0).intValue());
        });
  }

  @Test
  public void testCustomerOrdersSummaryWithNamePattern() {
    SqlServerTestHelper.run(
        c -> {
          var customer =
              testInsert.Customers().with(r -> r.withName("PatternMatch Customer")).insert(c);
          testInsert.Orders(customer.customerId()).insert(c);

          var results = ordersSummaryRepo.apply(Optional.of("PatternMatch%"), Optional.empty(), c);

          assertTrue(results.size() >= 1);
          assertTrue(
              results.stream().anyMatch(r -> r.customerName().equals("PatternMatch Customer")));
        });
  }

  @Test
  public void testCustomerOrdersSummaryWithMinTotal() {
    SqlServerTestHelper.run(
        c -> {
          var bigSpender = testInsert.Customers().with(r -> r.withName("Big Spender")).insert(c);
          testInsert
              .Orders(bigSpender.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("1000.00")))
              .insert(c);

          var smallSpender =
              testInsert.Customers().with(r -> r.withName("Small Spender")).insert(c);
          testInsert
              .Orders(smallSpender.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("50.00")))
              .insert(c);

          var results =
              ordersSummaryRepo.apply(Optional.empty(), Optional.of(new BigDecimal("500.00")), c);

          assertTrue(
              results.stream().anyMatch(r -> r.customerId().equals(bigSpender.customerId())));
          assertFalse(
              results.stream().anyMatch(r -> r.customerId().equals(smallSpender.customerId())));
        });
  }

  @Test
  public void testFindCustomersByEmail() {
    SqlServerTestHelper.run(
        c -> {
          var customer =
              testInsert
                  .Customers()
                  .with(r -> r.withEmail(new Email("unique-sqlserver-test@example.com")))
                  .insert(c);

          var results = findByEmailRepo.apply("%unique-sqlserver-test%", c);

          assertEquals(1, results.size());
          assertEquals(customer.customerId(), results.getFirst().customerId());
        });
  }

  @Test
  public void testFindCustomersByEmailNoMatch() {
    SqlServerTestHelper.run(
        c -> {
          testInsert.Customers().insert(c);

          var results = findByEmailRepo.apply("%nonexistent-email-pattern%", c);

          assertEquals(0, results.size());
        });
  }

  @Test
  public void testOrderSummaryMultipleOrders() {
    SqlServerTestHelper.run(
        c -> {
          var customer =
              testInsert.Customers().with(r -> r.withName("Multi Order Customer")).insert(c);

          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("100.00")))
              .insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("200.00")))
              .insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("300.00")))
              .insert(c);

          var results =
              ordersSummaryRepo.apply(Optional.of("Multi Order Customer"), Optional.empty(), c);

          assertEquals(1, results.size());
          var summary = results.getFirst();
          assertEquals(3, summary.orderCount().map(OrdersId::value).orElse(0).intValue());
          assertEquals(
              0, new BigDecimal("600.00").compareTo(summary.totalSpent().orElse(BigDecimal.ZERO)));
        });
  }
}
