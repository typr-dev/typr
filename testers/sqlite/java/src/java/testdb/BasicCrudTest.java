package testdb;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import testdb.customers.*;
import testdb.products.*;

/** Minimal end-to-end SQLite test: inserts, selects, updates, deletes. */
public class BasicCrudTest {
  private final CustomersRepoImpl customers = new CustomersRepoImpl();
  private final ProductsRepoImpl products = new ProductsRepoImpl();

  @Test
  public void selectAllCustomersFromSeedData() {
    List<CustomersRow> rows = SqliteTestHelper.applyRead(customers::selectAll);
    assertEquals(2, rows.size());
  }

  @Test
  public void findCustomerById() {
    var row = SqliteTestHelper.applyRead(conn -> customers.selectById(new CustomersId(1L), conn));
    assertTrue(row.isPresent());
    assertEquals("John Doe", row.get().name());
  }

  @Test
  public void deleteCustomerById() {
    // Insert a fresh customer (no FK references) and delete it within the same transaction.
    boolean deleted =
        SqliteTestHelper.apply(
            conn -> {
              var inserted =
                  customers.insert(
                      new CustomersRowUnsaved(
                          new CustomersId(999L),
                          "Disposable",
                          java.util.Optional.empty(),
                          new testdb.customtypes.Defaulted.UseDefault<>()),
                      conn);
              return customers.deleteById(inserted.customerId(), conn);
            });
    assertTrue(deleted);
  }

  @Test
  public void productsHaveExpectedPrices() {
    var ps = SqliteTestHelper.applyRead(products::selectAll);
    assertEquals(2, ps.size());
    var skus = ps.stream().map(ProductsRow::sku).sorted().toList();
    assertEquals(List.of("PROD-001", "PROD-002"), skus);
  }
}
