package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.Bijection;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customers.*;
import testdb.orders.*;

/**
 * Tests for the DSL query builder functionality. Tests type-safe query building with where,
 * orderBy, limit, count, and projection.
 */
public class DSLTest {
  private final TestInsert testInsert = new TestInsert(new Random(1985535425));
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();

  @Test
  public void testSelectWithWhere() {
    Db2TestHelper.run(
        c -> {
          var customer =
              testInsert.Customers().with(r -> r.withName("DB2 DSL Where Test")).insert(c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().isEqual("DB2 DSL Where Test"))
                  .toList(c);

          assertTrue(results.size() >= 1);
          assertTrue(results.stream().anyMatch(r -> r.customerId().equals(customer.customerId())));
        });
  }

  @Test
  public void testSelectWithOrderBy() {
    Db2TestHelper.run(
        c -> {
          testInsert.Customers().with(r -> r.withName("Db2Zebra")).insert(c);
          testInsert.Customers().with(r -> r.withName("Db2Alpha")).insert(c);
          testInsert.Customers().with(r -> r.withName("Db2Mike")).insert(c);

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
    Db2TestHelper.run(
        c -> {
          for (int i = 0; i < 10; i++) {
            final int idx = i;
            testInsert.Customers().with(r -> r.withName("Db2LimitTest" + idx)).insert(c);
          }

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Db2LimitTest%", Bijection.asString()))
                  .limit(5)
                  .toList(c);

          assertEquals(5, results.size());
        });
  }

  @Test
  public void testSelectWithCount() {
    Db2TestHelper.run(
        c -> {
          testInsert.Customers().with(r -> r.withName("Db2CountA")).insert(c);
          testInsert.Customers().with(r -> r.withName("Db2CountB")).insert(c);
          testInsert.Customers().with(r -> r.withName("Db2CountC")).insert(c);

          var count =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Db2Count%", Bijection.asString()))
                  .count(c);

          assertEquals(3, count);
        });
  }

  @Test
  public void testSelectWithIn() {
    Db2TestHelper.run(
        c -> {
          var c1 = testInsert.Customers().with(r -> r.withName("Db2InTest1")).insert(c);
          testInsert.Customers().with(r -> r.withName("Db2InTest2")).insert(c);
          var c3 = testInsert.Customers().with(r -> r.withName("Db2InTest3")).insert(c);

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
    Db2TestHelper.run(
        c -> {
          testInsert
              .Customers()
              .with(r -> r.withName("Db2ProjectionTest").withEmail("db2-projection@test.com"))
              .insert(c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().isEqual("Db2ProjectionTest"))
                  .map(cust -> cust.name().tupleWith(cust.email()))
                  .toList(c);

          assertEquals(1, results.size());
          assertEquals("Db2ProjectionTest", results.get(0)._1());
          assertEquals("db2-projection@test.com", results.get(0)._2());
        });
  }

  @Test
  public void testOrdersDSLQuery() {
    Db2TestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("500.00"))))
              .insert(c);
          testInsert
              .Orders(customer.customerId())
              .with(r -> r.withTotalAmount(Optional.of(new BigDecimal("1500.00"))))
              .insert(c);

          var largeOrders =
              ordersRepo
                  .select()
                  .where(o -> o.totalAmount().greaterThan(new BigDecimal("1000.00")))
                  .toList(c);

          assertTrue(largeOrders.size() >= 1);
        });
  }

  @Test
  public void testComplexWhereClause() {
    Db2TestHelper.run(
        c -> {
          testInsert
              .Customers()
              .with(r -> r.withName("Db2 Complex A").withEmail("db2-complex-a@test.com"))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withName("Db2 Complex B").withEmail("db2-complex-b@test.com"))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withName("Db2 Other").withEmail("db2-other@test.com"))
              .insert(c);

          var results =
              customersRepo
                  .select()
                  .where(
                      cust ->
                          cust.name()
                              .like("Db2 Complex%", Bijection.asString())
                              .and(
                                  cust.email().like("%@test.com", Bijection.asString()),
                                  Bijection.asBool()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }
}
