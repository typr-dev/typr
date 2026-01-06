package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.Bijection;
import java.math.BigDecimal;
import java.util.Random;
import org.junit.Test;
import testdb.customers.*;
import testdb.orders.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Tests for the DSL query builder functionality. Tests type-safe query building with where,
 * orderBy, limit, count, and projection.
 */
public class DSLTest {
  private final TestInsert testInsert = new TestInsert(new Random(42));
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();

  @Test
  public void testSelectWithWhere() {
    SqlServerTestHelper.run(
        c -> {
          var customer = testInsert.Customers().with(r -> r.withName("DSL Where Test")).insert(c);

          var results =
              customersRepo.select().where(cust -> cust.name().isEqual("DSL Where Test")).toList(c);

          assertTrue(results.size() >= 1);
          assertTrue(results.stream().anyMatch(r -> r.customerId().equals(customer.customerId())));
        });
  }

  @Test
  public void testSelectWithOrderBy() {
    SqlServerTestHelper.run(
        c -> {
          testInsert.Customers().with(r -> r.withName("Zebra")).insert(c);
          testInsert.Customers().with(r -> r.withName("Alpha")).insert(c);
          testInsert.Customers().with(r -> r.withName("Mike")).insert(c);

          var results =
              customersRepo.select().orderBy(cust -> cust.name().asc()).limit(10).toList(c);

          assertTrue(results.size() >= 3);
          String firstName = null;
          for (var result : results) {
            if (firstName != null) {
              assertTrue(result.name().compareTo(firstName) >= 0);
            }
            firstName = result.name();
          }
        });
  }

  @Test
  public void testSelectWithLimit() {
    SqlServerTestHelper.run(
        c -> {
          for (int i = 0; i < 10; i++) {
            final int idx = i;
            testInsert.Customers().with(r -> r.withName("LimitTest" + idx)).insert(c);
          }

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("LimitTest%", Bijection.asString()))
                  .limit(5)
                  .toList(c);

          assertEquals(5, results.size());
        });
  }

  @Test
  public void testSelectWithCount() {
    SqlServerTestHelper.run(
        c -> {
          testInsert.Customers().with(r -> r.withName("CountA")).insert(c);
          testInsert.Customers().with(r -> r.withName("CountB")).insert(c);
          testInsert.Customers().with(r -> r.withName("CountC")).insert(c);

          var count =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Count%", Bijection.asString()))
                  .count(c);

          assertEquals(3, count);
        });
  }

  @Test
  public void testSelectWithIn() {
    SqlServerTestHelper.run(
        c -> {
          var c1 = testInsert.Customers().with(r -> r.withName("InTest1")).insert(c);
          testInsert.Customers().with(r -> r.withName("InTest2")).insert(c);
          var c3 = testInsert.Customers().with(r -> r.withName("InTest3")).insert(c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.customerId().in(c1.customerId(), c3.customerId()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithProjection() {
    SqlServerTestHelper.run(
        c -> {
          testInsert
              .Customers()
              .with(r -> r.withName("ProjectionTest").withEmail(new Email("projection@test.com")))
              .insert(c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().isEqual("ProjectionTest"))
                  .map(cust -> cust.name().tupleWith(cust.email()))
                  .toList(c);

          assertEquals(1, results.size());
          assertEquals("ProjectionTest", results.get(0)._1());
          assertEquals(new Email("projection@test.com"), results.get(0)._2());
        });
  }

  @Test
  public void testProductDSLQuery() {
    SqlServerTestHelper.run(
        c -> {
          testInsert
              .Products()
              .with(r -> r.withName("Expensive Product").withPrice(new BigDecimal("999.99")))
              .insert(c);
          testInsert
              .Products()
              .with(r -> r.withName("Cheap Product").withPrice(new BigDecimal("9.99")))
              .insert(c);

          var expensiveProducts =
              productsRepo
                  .select()
                  .where(p -> p.price().greaterThan(new BigDecimal("100.00")))
                  .toList(c);

          assertTrue(expensiveProducts.size() >= 1);
          assertTrue(
              expensiveProducts.stream()
                  .allMatch(p -> p.price().compareTo(new BigDecimal("100.00")) > 0));
        });
  }

  @Test
  public void testOrdersDSLQuery() {
    SqlServerTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("500.00")))
              .insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(new BigDecimal("1500.00")))
              .insert(c);

          var largeOrders =
              ordersRepo
                  .select()
                  .where(o -> o.totalAmount().greaterThan(new BigDecimal("1000.00")))
                  .toList(c);

          assertTrue(largeOrders.size() >= 1);
          assertTrue(
              largeOrders.stream()
                  .allMatch(o -> o.totalAmount().compareTo(new BigDecimal("1000.00")) > 0));
        });
  }

  @Test
  public void testComplexWhereClause() {
    SqlServerTestHelper.run(
        c -> {
          testInsert
              .Customers()
              .with(r -> r.withName("Complex A").withEmail(new Email("complex-a@test.com")))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withName("Complex B").withEmail(new Email("complex-b@test.com")))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withName("Other").withEmail(new Email("other@test.com")))
              .insert(c);

          var results =
              customersRepo
                  .select()
                  .where(
                      cust ->
                          cust.name()
                              .like("Complex%", Bijection.asString())
                              .and(
                                  cust.email().like("%@test.com", Email.bijection),
                                  Bijection.asBool()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }
}
