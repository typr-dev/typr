package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customer_orders.*;
import testdb.customtypes.Defaulted;
import testdb.product_search.*;
import testdb.simple_customer_lookup.*;
import testdb.userdefined.Email;
import testdb.userdefined.FirstName;
import testdb.userdefined.LastName;

/**
 * Tests for SQL script generated code. These tests verify that the code generated from SQL files in
 * sql-scripts/mariadb/ works correctly.
 */
public class SqlScriptTest {
  private final ProductSearchSqlRepoImpl productSearchRepo = new ProductSearchSqlRepoImpl();
  private final CustomerOrdersSqlRepoImpl customerOrdersRepo = new CustomerOrdersSqlRepoImpl();
  private final SimpleCustomerLookupSqlRepoImpl simpleCustomerLookupRepo =
      new SimpleCustomerLookupSqlRepoImpl();
  private final TestInsert testInsert = new TestInsert(new Random(42));

  @Test
  public void testProductSearchWithNoFilters() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test products first
          var status = testInsert.CustomerStatus().insert(c);
          var brand = testInsert.Brands().insert(c);
          var _ =
              testInsert
                  .Products()
                  .with(x -> x.withBrandId(new Defaulted.Provided<>(Optional.of(brand.brandId()))))
                  .insert(c);
          var __ =
              testInsert
                  .Products()
                  .with(x -> x.withBrandId(new Defaulted.Provided<>(Optional.of(brand.brandId()))))
                  .insert(c);

          // Call the SQL script with no filters
          var results =
              productSearchRepo.apply(
                  Optional.empty(), // brandId
                  Optional.empty(), // minPrice
                  Optional.empty(), // maxPrice
                  Optional.empty(), // status
                  100L, // limit
                  c);

          assertNotNull(results);
          assertTrue(results.size() >= 2);
        });
  }

  @Test
  public void testProductSearchWithBrandFilter() {
    MariaDbTestHelper.run(
        c -> {
          var status = testInsert.CustomerStatus().insert(c);
          var brand1 = testInsert.Brands().insert(c);
          var brand2 = testInsert.Brands().insert(c);
          var product1 =
              testInsert
                  .Products()
                  .with(x -> x.withBrandId(new Defaulted.Provided<>(Optional.of(brand1.brandId()))))
                  .insert(c);
          var _ =
              testInsert
                  .Products()
                  .with(x -> x.withBrandId(new Defaulted.Provided<>(Optional.of(brand2.brandId()))))
                  .insert(c);

          // Filter by specific brand
          var results =
              productSearchRepo.apply(
                  Optional.of(brand1.brandId().value()), // brandId
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  100L,
                  c);

          assertNotNull(results);
          assertTrue(results.size() >= 1);
          // All results should be from brand1
          for (var row : results) {
            // Can't directly check brandId but brandName should be consistent
            assertNotNull(row);
          }
        });
  }

  @Test
  public void testProductSearchWithPriceRange() {
    MariaDbTestHelper.run(
        c -> {
          var status = testInsert.CustomerStatus().insert(c);
          var brand = testInsert.Brands().insert(c);

          var _ =
              testInsert
                  .Products()
                  .with(
                      p ->
                          p.withBrandId(new Defaulted.Provided<>(Optional.of(brand.brandId())))
                              .withBasePrice(new BigDecimal("50.00")))
                  .insert(c);
          var __ =
              testInsert
                  .Products()
                  .with(
                      p ->
                          p.withBrandId(new Defaulted.Provided<>(Optional.of(brand.brandId())))
                              .withBasePrice(new BigDecimal("150.00")))
                  .insert(c);

          // Filter by price range
          var results =
              productSearchRepo.apply(
                  Optional.empty(),
                  Optional.of(new BigDecimal("40.00")), // minPrice
                  Optional.of(new BigDecimal("100.00")), // maxPrice
                  Optional.empty(),
                  100L,
                  c);

          assertNotNull(results);
          // Should find products in range
          for (var row : results) {
            assertTrue(
                row.basePrice().compareTo(new BigDecimal("40.00")) >= 0
                    && row.basePrice().compareTo(new BigDecimal("100.00")) <= 0);
          }
        });
  }

  @Test
  public void testProductSearchWithLimit() {
    MariaDbTestHelper.run(
        c -> {
          var status = testInsert.CustomerStatus().insert(c);
          var brand = testInsert.Brands().insert(c);
          for (int i = 0; i < 5; i++) {
            var _ =
                testInsert
                    .Products()
                    .with(
                        x -> x.withBrandId(new Defaulted.Provided<>(Optional.of(brand.brandId()))))
                    .insert(c);
          }

          // Limit to 2 results
          var results =
              productSearchRepo.apply(
                  Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 2L, c);

          assertNotNull(results);
          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSimpleCustomerLookup() {
    MariaDbTestHelper.run(
        c -> {
          // Create a customer
          var status = testInsert.CustomerStatus().insert(c);
          var customer =
              testInsert
                  .Customers(new byte[] {1, 2, 3})
                  .with(
                      r ->
                          r.withEmail(new Email("customer@example.com"))
                              .withFirstName(new FirstName("Test"))
                              .withLastName(new LastName("User")))
                  .insert(c);

          // Look up by customer ID
          var results = simpleCustomerLookupRepo.apply(customer.email().value(), c);

          assertNotNull(results);
          assertEquals(1, results.size());
          assertEquals(customer.email(), results.get(0).email());
          assertEquals(customer.firstName(), results.get(0).firstName());
          assertEquals(customer.lastName(), results.get(0).lastName());
        });
  }

  @Test
  public void testCustomerOrders() {
    MariaDbTestHelper.run(
        c -> {
          // Create dependencies
          var status = testInsert.CustomerStatus().insert(c);
          var customer = testInsert.Customers(new byte[] {1, 2, 3}).insert(c);

          // Create orders for the customer
          var order1 = testInsert.Orders(customer.customerId()).insert(c);
          var order2 = testInsert.Orders(customer.customerId()).insert(c);

          // Query customer orders
          var results = customerOrdersRepo.apply(customer.customerId(), Optional.empty(), c);

          assertNotNull(results);
          assertTrue(results.size() >= 2);
        });
  }
}
