package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.Test;
import testdb.all_scalar_types.*;
import testdb.customer_orders.*;
import testdb.customers.*;
import testdb.customtypes.Defaulted;
import testdb.order_details.*;
import testdb.orders.*;
import testdb.products.*;

/** Tests for DuckDB-specific features: enums, special types, and views. */
public class DatabaseFeaturesTest {
  private final TestInsert testInsert = new TestInsert(new Random(2093113001));
  private final AllScalarTypesRepoImpl allTypesRepo = new AllScalarTypesRepoImpl();
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final CustomerOrdersViewRepoImpl customerOrdersViewRepo =
      new CustomerOrdersViewRepoImpl();
  private final OrderDetailsViewRepoImpl orderDetailsViewRepo = new OrderDetailsViewRepoImpl();

  // ==================== Enum Tests ====================

  @Test
  public void testPriorityEnumValues() {
    DuckDbTestHelper.run(
        c -> {
          var lowPriority =
              testInsert
                  .Customers()
                  .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.low))))
                  .insert(c);
          var highPriority =
              testInsert
                  .Customers()
                  .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.high))))
                  .insert(c);
          var criticalPriority =
              testInsert
                  .Customers()
                  .with(
                      r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.critical))))
                  .insert(c);

          assertEquals(Optional.of(Priority.low), lowPriority.priority());
          assertEquals(Optional.of(Priority.high), highPriority.priority());
          assertEquals(Optional.of(Priority.critical), criticalPriority.priority());
        });
  }

  @Test
  public void testEnumDSLFilter() {
    DuckDbTestHelper.run(
        c -> {
          testInsert
              .Customers()
              .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.low))))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.high))))
              .insert(c);
          testInsert
              .Customers()
              .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.critical))))
              .insert(c);

          var highPriorityCustomers =
              customersRepo
                  .select()
                  .where(cust -> cust.priority().isEqual(Priority.high))
                  .toList(c);

          assertFalse(highPriorityCustomers.isEmpty());
          assertTrue(
              highPriorityCustomers.stream()
                  .allMatch(cust -> cust.priority().equals(Optional.of(Priority.high))));
        });
  }

  @Test
  public void testMoodEnum() {
    DuckDbTestHelper.run(
        c -> {
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(r -> r.withColMood(Optional.of(Mood.happy)))
                  .insert(c);

          assertEquals(Optional.of(Mood.happy), row.colMood());

          var found = allTypesRepo.selectById(row.id(), c).orElseThrow();
          assertEquals(Optional.of(Mood.happy), found.colMood());
        });
  }

  // ==================== UUID Tests ====================

  @Test
  public void testUuidType() {
    DuckDbTestHelper.run(
        c -> {
          var uuid = UUID.randomUUID();
          var row =
              testInsert.AllScalarTypes().with(r -> r.withColUuid(Optional.of(uuid))).insert(c);

          assertEquals(Optional.of(uuid), row.colUuid());
        });
  }

  @Test
  public void testUuidUniqueness() {
    DuckDbTestHelper.run(
        c -> {
          var uuid1 = UUID.randomUUID();
          var uuid2 = UUID.randomUUID();

          var row1 =
              testInsert.AllScalarTypes().with(r -> r.withColUuid(Optional.of(uuid1))).insert(c);
          var row2 =
              testInsert.AllScalarTypes().with(r -> r.withColUuid(Optional.of(uuid2))).insert(c);

          assertNotEquals(row1.colUuid(), row2.colUuid());
        });
  }

  // ==================== JSON Tests ====================

  @Test
  public void testJsonType() {
    DuckDbTestHelper.run(
        c -> {
          var json =
              "{\"name\": \"test\", \"values\": [1, 2, 3], \"nested\": {\"key\": \"value\"}}";
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(r -> r.withColJson(Optional.of(new Json(json))))
                  .insert(c);

          assertTrue(row.colJson().isPresent());
          assertTrue(row.colJson().get().value().contains("name"));
          assertTrue(row.colJson().get().value().contains("nested"));
        });
  }

  // ==================== Date/Time Tests ====================

  @Test
  public void testTimestampWithTimezone() {
    DuckDbTestHelper.run(
        c -> {
          var timestamptz =
              OffsetDateTime.of(2025, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(-5)).toInstant();
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(r -> r.withColTimestamptz(Optional.of(timestamptz)))
                  .insert(c);

          assertTrue(row.colTimestamptz().isPresent());
        });
  }

  @Test
  public void testIntervalType() {
    DuckDbTestHelper.run(
        c -> {
          var interval = Duration.ofDays(30).plusHours(12);
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(r -> r.withColInterval(Optional.of(interval)))
                  .insert(c);

          assertTrue(row.colInterval().isPresent());
        });
  }

  // ==================== Large Integer Tests ====================

  @Test
  public void testHugeintType() {
    DuckDbTestHelper.run(
        c -> {
          var hugeValue = new BigInteger("170141183460469231731687303715884105727");
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(r -> r.withColHugeint(Optional.of(hugeValue)))
                  .insert(c);

          assertTrue(row.colHugeint().isPresent());
        });
  }

  @Test
  public void testUnsignedIntegerTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row =
              testInsert
                  .AllScalarTypes()
                  .with(
                      r ->
                          r.withColUtinyint(
                                  Optional.of(new dev.typr.foundations.data.Uint1((short) 255)))
                              .withColUsmallint(
                                  Optional.of(new dev.typr.foundations.data.Uint2(65535)))
                              .withColUinteger(
                                  Optional.of(new dev.typr.foundations.data.Uint4(4294967295L)))
                              .withColUbigint(
                                  Optional.of(
                                      dev.typr.foundations.data.Uint8.of(
                                          new BigInteger("18446744073709551615")))))
                  .insert(c);

          assertEquals(
              Optional.of(new dev.typr.foundations.data.Uint1((short) 255)), row.colUtinyint());
          assertEquals(Optional.of(new dev.typr.foundations.data.Uint2(65535)), row.colUsmallint());
          assertEquals(
              Optional.of(new dev.typr.foundations.data.Uint4(4294967295L)), row.colUinteger());
        });
  }

  // ==================== View Tests ====================

  @Test
  public void testCustomerOrdersView() {
    DuckDbTestHelper.run(
        c -> {
          var customer =
              testInsert.Customers().with(r -> r.withName("View Test Customer")).insert(c);
          var order =
              testInsert
                  .Orders()
                  .with(
                      r ->
                          r.withCustomerId(customer.customerId().value())
                              .withTotalAmount(Optional.of(new BigDecimal("199.99"))))
                  .insert(c);

          var viewResults = customerOrdersViewRepo.select().toList(c);

          assertFalse(viewResults.isEmpty());
          var customerView =
              viewResults.stream()
                  .filter(v -> v.customerId().equals(Optional.of(customer.customerId().value())))
                  .findFirst();
          assertTrue(customerView.isPresent());
          assertEquals(Optional.of("View Test Customer"), customerView.get().customerName());
        });
  }

  @Test
  public void testOrderDetailsView() {
    DuckDbTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var product = testInsert.Products().with(r -> r.withName("View Test Product")).insert(c);
          var order =
              testInsert
                  .Orders()
                  .with(r -> r.withCustomerId(customer.customerId().value()))
                  .insert(c);
          testInsert
              .OrderItems()
              .with(
                  r ->
                      r.withOrderId(order.orderId().value())
                          .withProductId(product.productId().value())
                          .withQuantity(new Defaulted.Provided<>(3))
                          .withUnitPrice(new BigDecimal("25.00")))
              .insert(c);

          var viewResults = orderDetailsViewRepo.select().toList(c);

          assertFalse(viewResults.isEmpty());
          var orderDetail =
              viewResults.stream()
                  .filter(v -> v.orderId().equals(Optional.of(order.orderId().value())))
                  .findFirst();
          assertTrue(orderDetail.isPresent());
          assertEquals(Optional.of("View Test Product"), orderDetail.get().productName());
          assertEquals(3, orderDetail.get().quantity().get().intValue());
        });
  }

  // ==================== Default Value Tests ====================

  @Test
  public void testEnumDefaultValue() {
    DuckDbTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);

          assertEquals(Optional.of(Priority.medium), customer.priority());
        });
  }
}
