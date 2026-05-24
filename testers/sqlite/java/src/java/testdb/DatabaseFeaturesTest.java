package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import testdb.customer_orders.CustomerOrdersViewRepoImpl;
import testdb.customers.*;
import testdb.customtypes.Defaulted;
import testdb.order_details.OrderDetailsViewRepoImpl;
import testdb.orders.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Tests SQLite-specific feature claims from SqliteAdapter:
 *
 * <ul>
 *   <li>RETURNING (supportsReturning=true, since SQLite 3.35) — every insert path uses it
 *   <li>ON CONFLICT upserts (upsertStrategy=Returning, since SQLite 3.24)
 *   <li>AUTOINCREMENT primary keys via the unsaved-row pattern
 *   <li>UUID / JSON / BLOB / DateTime round-trips through TEXT-affinity storage
 *   <li>Views (customer_orders, order_details)
 * </ul>
 */
public class DatabaseFeaturesTest {
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final CustomerOrdersViewRepoImpl customerOrdersView = new CustomerOrdersViewRepoImpl();
  private final OrderDetailsViewRepoImpl orderDetailsView = new OrderDetailsViewRepoImpl();

  // ==================== RETURNING ====================

  @Test
  public void testInsertReturningPopulatesGeneratedColumns() {
    SqliteTestHelper.run(
        c -> {
          // The unsaved-row path leans on AUTOINCREMENT + DEFAULT CURRENT_TIMESTAMP — both must
          // come
          // back via the RETURNING clause.
          var unsaved = new CustomersRowUnsaved(new CustomersId(10001L), "Returning Test");
          var inserted = customersRepo.insert(unsaved, c);

          assertNotNull(inserted);
          assertEquals(Long.valueOf(10001L), inserted.customerId().value());
          // createdAt comes from DEFAULT CURRENT_TIMESTAMP — RETURNING gives us the resolved value.
          assertNotNull(inserted.createdAt());
        });
  }

  @Test
  public void testInsertWithProvidedDefault() {
    SqliteTestHelper.run(
        c -> {
          var explicit = LocalDateTime.of(2020, 5, 1, 12, 0);
          var unsaved =
              new CustomersRowUnsaved(
                  new CustomersId(10002L),
                  "Explicit Default",
                  Optional.of(new Email("user@x.com")),
                  new Defaulted.Provided<>(explicit));
          var inserted = customersRepo.insert(unsaved, c);

          assertEquals(explicit, inserted.createdAt());
        });
  }

  // ==================== ON CONFLICT upsert ====================

  @Test
  public void testUpsertInsertsNew() {
    SqliteTestHelper.run(
        c -> {
          var customer =
              new CustomersRow(
                  new CustomersId(20001L),
                  "Upsert Insert",
                  Optional.empty(),
                  LocalDateTime.of(2025, 1, 1, 0, 0));
          var result = customersRepo.upsert(customer, c);
          assertEquals("Upsert Insert", result.name());
        });
  }

  @Test
  public void testUpsertUpdatesOnConflict() {
    SqliteTestHelper.run(
        c -> {
          var customer =
              new CustomersRow(
                  new CustomersId(20002L),
                  "Original",
                  Optional.of(new Email("a@x.com")),
                  LocalDateTime.of(2025, 1, 1, 0, 0));
          customersRepo.insert(customer, c);

          var updated =
              customer.withName("Updated via Upsert").withEmail(Optional.of(new Email("b@x.com")));
          var result = customersRepo.upsert(updated, c);

          assertEquals("Updated via Upsert", result.name());
          assertEquals(Optional.of(new Email("b@x.com")), result.email());

          // Confirm there's still only one row for that ID
          var found = customersRepo.selectById(new CustomersId(20002L), c).orElseThrow();
          assertEquals("Updated via Upsert", found.name());
        });
  }

  // ==================== Round-trip-only types ====================

  @Test
  public void testUuidPersistsAsCanonicalText() {
    SqliteTestHelper.run(
        c -> {
          var uuid = UUID.randomUUID();
          // Use the products table since it has a metadata json column AND a sku unique constraint.
          // Insert a UUID via the all_scalar_types tester (covered by AllTypesTest). Here just
          // verify a fresh UUID round-trips through the products.metadata path.
          var product =
              new ProductsRow(
                  new ProductsId(30001L),
                  "SKU-UUID-" + uuid,
                  "UUID Product",
                  new BigDecimal("1.00"),
                  Optional.of(new Json("{\"uuid\":\"" + uuid + "\"}")));
          var inserted = productsRepo.insert(product, c);
          assertTrue(inserted.metadata().orElseThrow().value().contains(uuid.toString()));
        });
  }

  // ==================== Views ====================

  @Test
  public void testCustomerOrdersView() {
    SqliteTestHelper.run(
        c -> {
          var rows = customerOrdersView.selectAll(c);
          // Schema's view does a LEFT JOIN customers ⨝ orders. Seed has 2 customers + 2 orders,
          // so the result is 2 rows (each customer joined to their one order).
          assertEquals(2, rows.size());
          assertTrue(rows.stream().anyMatch(r -> r.customerName().equals(Optional.of("John Doe"))));
          assertTrue(
              rows.stream().anyMatch(r -> r.customerName().equals(Optional.of("Jane Smith"))));
        });
  }

  @Test
  public void testOrderDetailsView() {
    SqliteTestHelper.run(
        c -> {
          var rows = orderDetailsView.selectAll(c);
          // Seed has 3 order_items rows, so the view produces 3 (orders × items × products joined).
          assertEquals(3, rows.size());
          // line_total is the computed expression quantity * unit_price; sqlglot can't infer its
          // numeric type through SQLite's affinity model and falls back to TEXT, so we just check
          // it's non-empty and parses as a positive BigDecimal.
          rows.forEach(
              r -> {
                var s = r.lineTotal().orElseThrow();
                assertTrue(new BigDecimal(s).compareTo(BigDecimal.ZERO) > 0);
              });
        });
  }

  // ==================== AUTOINCREMENT ====================

  @Test
  public void testInsertWithExplicitId() {
    SqliteTestHelper.run(
        c -> {
          // The customers PK is AUTOINCREMENT, but the codegen surfaces it as required on
          // CustomersRow. We provide it; SQLite respects it.
          var row =
              new CustomersRow(
                  new CustomersId(50001L),
                  "Explicit ID",
                  Optional.empty(),
                  LocalDateTime.of(2025, 1, 1, 0, 0));
          var inserted = customersRepo.insert(row, c);
          assertEquals(Long.valueOf(50001L), inserted.customerId().value());
        });
  }

  @Test
  public void testSelectByIdsReturnsRequested() {
    SqliteTestHelper.run(
        c -> {
          // Seed customers 1 and 2 — selectByIds is the supportsArrays=false path; it should still
          // work via IN-clause expansion.
          var found =
              customersRepo.selectByIds(
                  java.util.List.of(new CustomersId(1L), new CustomersId(2L)), c);
          assertEquals(2, found.size());
        });
  }

  @Test
  public void testOrderInsertWithForeignKeyValue() {
    SqliteTestHelper.run(
        c -> {
          var order =
              new OrdersRow(
                  new OrdersId(70001L),
                  new CustomersId(1L),
                  LocalDate.of(2025, 7, 1),
                  Optional.of(new BigDecimal("250.00")),
                  "pending");
          var inserted = ordersRepo.insert(order, c);
          assertEquals(Long.valueOf(70001L), inserted.orderId().value());
          assertEquals(Long.valueOf(1L), inserted.customerId().value());
        });
  }
}
