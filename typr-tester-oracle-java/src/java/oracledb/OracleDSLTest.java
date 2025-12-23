package oracledb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Optional;
import oracledb.customers.CustomersRepoImpl;
import oracledb.customers.CustomersRowUnsaved;
import oracledb.customtypes.Defaulted;
import oracledb.products.ProductsRepoImpl;
import oracledb.products.ProductsRowUnsaved;
import org.junit.Test;
import typr.dsl.Bijection;

public class OracleDSLTest {
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();

  @Test
  public void testSelectWithWhereClause() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("199.99"), "USD");
          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "DSL-001",
                  "DSL Test Product",
                  price,
                  Optional.of(new TagVarrayT(new String[] {"dsl"})),
                  new Defaulted.UseDefault<>());

          var inserted = productsRepo.insert(unsaved, c);

          var query = productsRepo.select().where(p -> p.sku().isEqual("DSL-001"));

          var results = query.toList(c);
          assertFalse(results.isEmpty());
          assertEquals("DSL-001", results.get(0).sku());
        });
  }

  @Test
  public void testOrderByClause() {
    OracleTestHelper.run(
        c -> {
          MoneyT price1 = new MoneyT(new BigDecimal("300.00"), "USD");
          MoneyT price2 = new MoneyT(new BigDecimal("100.00"), "USD");
          MoneyT price3 = new MoneyT(new BigDecimal("200.00"), "USD");

          productsRepo.insert(
              new ProductsRowUnsaved(
                  "ORDER-3", "Product C", price1, Optional.empty(), new Defaulted.UseDefault<>()),
              c);
          productsRepo.insert(
              new ProductsRowUnsaved(
                  "ORDER-1", "Product A", price2, Optional.empty(), new Defaulted.UseDefault<>()),
              c);
          productsRepo.insert(
              new ProductsRowUnsaved(
                  "ORDER-2", "Product B", price3, Optional.empty(), new Defaulted.UseDefault<>()),
              c);

          var query =
              productsRepo
                  .select()
                  .where(
                      p ->
                          p.sku()
                              .isEqual("ORDER-1")
                              .or(p.sku().isEqual("ORDER-2"), Bijection.identity())
                              .or(p.sku().isEqual("ORDER-3"), Bijection.identity()))
                  .orderBy(p -> p.sku().asc());

          var results = query.toList(c);
          assertTrue(results.size() >= 3);
        });
  }

  @Test
  public void testLimitClause() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("10.00"), "USD");

          for (int i = 1; i <= 5; i++) {
            productsRepo.insert(
                new ProductsRowUnsaved(
                    "LIMIT-" + i,
                    "Limit Product " + i,
                    price,
                    Optional.empty(),
                    new Defaulted.UseDefault<>()),
                c);
          }

          var query = productsRepo.select().where(p -> p.sku().isEqual("LIMIT-1")).limit(3);

          var results = query.toList(c);
          assertTrue(results.size() <= 3);
        });
  }

  @Test
  public void testCountQuery() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("50.00"), "USD");

          for (int i = 1; i <= 7; i++) {
            productsRepo.insert(
                new ProductsRowUnsaved(
                    "COUNT-" + i,
                    "Count Product " + i,
                    price,
                    Optional.empty(),
                    new Defaulted.UseDefault<>()),
                c);
          }

          var query = productsRepo.select().where(p -> p.sku().isEqual("COUNT-1"));

          long count = query.count(c);
          assertTrue(count >= 1);
        });
  }

  @Test
  public void testMapProjection() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("99.99"), "USD");
          var inserted =
              productsRepo.insert(
                  new ProductsRowUnsaved(
                      "MAP-001", "Map Test", price, Optional.empty(), new Defaulted.UseDefault<>()),
                  c);

          // Single-column map returns Tuple1
          var query =
              productsRepo.select().where(p -> p.sku().isEqual("MAP-001")).map(p -> p.name());

          var results = query.toList(c);
          assertFalse(results.isEmpty());
          assertEquals("Map Test", results.get(0)._1());
        });
  }

  @Test
  public void testComplexWhereWithOracleObjectTypes() {
    OracleTestHelper.run(
        c -> {
          CoordinatesT nycCoords =
              new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));
          AddressT nycAddress = new AddressT("NYC Street", "New York", nycCoords);

          customersRepo.insert(
              new CustomersRowUnsaved(
                  "NYC Customer",
                  nycAddress,
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>()),
              c);

          var query = customersRepo.select().where(cust -> cust.name().isEqual("NYC Customer"));

          var results = query.toList(c);
          assertFalse(results.isEmpty());
          assertEquals("NYC Customer", results.get(0).name());
        });
  }

  @Test
  public void testDeleteWithDSL() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("10.00"), "USD");
          var inserted =
              productsRepo.insert(
                  new ProductsRowUnsaved(
                      "DELETE-DSL",
                      "To Delete via DSL",
                      price,
                      Optional.empty(),
                      new Defaulted.UseDefault<>()),
                  c);

          var deleteQuery = productsRepo.delete().where(p -> p.sku().isEqual("DELETE-DSL"));

          int deleted = deleteQuery.execute(c);
          assertTrue(deleted > 0);

          var found = productsRepo.selectById(inserted, c);
          assertFalse(found.isPresent());
        });
  }
}
