package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.all_scalar_types_search.AllScalarTypesSearchSqlRepoImpl;
import testdb.customer_search.CustomerSearchSqlRepoImpl;
import testdb.delete_old_orders.DeleteOldOrdersSqlRepoImpl;
import testdb.order_summary_by_customer.OrderSummaryByCustomerSqlRepoImpl;
import testdb.product_details_with_sales.ProductDetailsWithSalesSqlRepoImpl;

/**
 * End-to-end exercise of every generated SQL-script repo. Verifies parameter encoding, result
 * decoding, and the typr ↔ sqlglot type-lineage hand-off (e.g. customer_id returning a typed
 * CustomersId rather than raw Long).
 */
public class SqlScriptTest {
  private final CustomerSearchSqlRepoImpl customerSearch = new CustomerSearchSqlRepoImpl();
  private final OrderSummaryByCustomerSqlRepoImpl orderSummary =
      new OrderSummaryByCustomerSqlRepoImpl();
  private final ProductDetailsWithSalesSqlRepoImpl productDetails =
      new ProductDetailsWithSalesSqlRepoImpl();
  private final AllScalarTypesSearchSqlRepoImpl scalarSearch =
      new AllScalarTypesSearchSqlRepoImpl();
  private final DeleteOldOrdersSqlRepoImpl deleteOld = new DeleteOldOrdersSqlRepoImpl();

  // ============== customer_search.sql ==============

  @Test
  public void testCustomerSearchAll() {
    SqliteTestHelper.run(
        c -> {
          var rows =
              customerSearch.apply(Optional.empty(), Optional.empty(), Optional.empty(), 100L, c);
          assertEquals(2, rows.size()); // seeded John Doe + Jane Smith
        });
  }

  @Test
  public void testCustomerSearchByNamePattern() {
    SqliteTestHelper.run(
        c -> {
          var rows =
              customerSearch.apply(
                  Optional.of("John%"), Optional.empty(), Optional.empty(), 100L, c);
          assertEquals(1, rows.size());
          assertEquals("John Doe", rows.get(0).name());
        });
  }

  @Test
  public void testCustomerSearchByEmailPattern() {
    SqliteTestHelper.run(
        c -> {
          var rows =
              customerSearch.apply(
                  Optional.empty(), Optional.of("%jane%"), Optional.empty(), 100L, c);
          assertEquals(1, rows.size());
          assertEquals("Jane Smith", rows.get(0).name());
        });
  }

  @Test
  public void testCustomerSearchLimit() {
    SqliteTestHelper.run(
        c -> {
          var rows =
              customerSearch.apply(Optional.empty(), Optional.empty(), Optional.empty(), 1L, c);
          assertEquals(1, rows.size());
        });
  }

  @Test
  public void testCustomerSearchByCreatedAfter() {
    SqliteTestHelper.run(
        c -> {
          var future = LocalDateTime.of(2099, 1, 1, 0, 0);
          var rows =
              customerSearch.apply(
                  Optional.empty(), Optional.empty(), Optional.of(future), 100L, c);
          assertEquals(0, rows.size());
        });
  }

  // ============== order_summary_by_customer.sql ==============

  @Test
  public void testOrderSummaryByCustomerInRange() {
    SqliteTestHelper.run(
        c -> {
          var rows = orderSummary.apply(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), c);
          // Two seeded customers, both with orders in range
          assertEquals(2, rows.size());
          // Sum across both customers' orders should match seed totals (79.98 + 29.99)
          var totalRevenue =
              rows.stream()
                  .map(r -> r.totalRevenue().orElse(BigDecimal.ZERO))
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          assertEquals(0, new BigDecimal("109.97").compareTo(totalRevenue));
        });
  }

  @Test
  public void testOrderSummaryEmptyDateRange() {
    SqliteTestHelper.run(
        c -> {
          var rows = orderSummary.apply(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31), c);
          // Both seeded customers have orders, but neither is in the 2099 range; the WHERE clause
          // filters out every row (the `IS NULL` branch only matches customers with no order at
          // all).
          assertEquals(0, rows.size());
        });
  }

  // ============== product_details_with_sales.sql ==============

  @Test
  public void testProductDetailsAll() {
    SqliteTestHelper.run(
        c -> {
          var rows = productDetails.apply(Optional.empty(), c);
          assertEquals(2, rows.size());
        });
  }

  @Test
  public void testProductDetailsByMinPrice() {
    SqliteTestHelper.run(
        c -> {
          var rows = productDetails.apply(Optional.of(new BigDecimal("40.00")), c);
          // Only Widget B (49.99) clears the bar
          assertEquals(1, rows.size());
          assertEquals("Widget B", rows.get(0).productName());
        });
  }

  // ============== all_scalar_types_search.sql ==============

  @Test
  public void testAllScalarSearchAll() {
    SqliteTestHelper.run(
        c -> {
          var rows = scalarSearch.apply(Optional.empty(), Optional.empty(), Optional.empty(), c);
          assertEquals(1, rows.size()); // seed has one row
        });
  }

  @Test
  public void testAllScalarSearchByMinId() {
    SqliteTestHelper.run(
        c -> {
          var rows = scalarSearch.apply(Optional.of(100L), Optional.empty(), Optional.empty(), c);
          assertEquals(0, rows.size());
        });
  }

  @Test
  public void testAllScalarSearchByTextPattern() {
    SqliteTestHelper.run(
        c -> {
          var rows = scalarSearch.apply(Optional.empty(), Optional.of("hel%"), Optional.empty(), c);
          assertEquals(1, rows.size());
        });
  }

  // ============== delete_old_orders.sql ==============

  @Test
  public void testDeleteOldOrders() {
    SqliteTestHelper.run(
        c -> {
          // Insert a completed order with no order_items (so the FK to order_items doesn't block
          // delete),
          // then delete it through the script.
          var ordersRepo = new testdb.orders.OrdersRepoImpl();
          ordersRepo.insert(
              new testdb.orders.OrdersRow(
                  new testdb.orders.OrdersId(9001L),
                  new testdb.customers.CustomersId(1L),
                  LocalDate.of(2020, 1, 1),
                  Optional.of(new BigDecimal("10.00")),
                  "completed"),
              c);

          int deleted = deleteOld.apply(LocalDate.of(2025, 1, 1), c);
          assertEquals(1, deleted);
        });
  }

  @Test
  public void testDeleteOldOrdersNoneMatch() {
    SqliteTestHelper.run(
        c -> {
          int deleted = deleteOld.apply(LocalDate.of(1900, 1, 1), c);
          assertEquals(0, deleted);
        });
  }
}
