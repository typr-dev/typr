package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.Bijection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import testdb.customers.*;
import testdb.orders.*;

/**
 * Tests for mock repository implementations. Mock repos provide in-memory implementations for unit
 * testing without database access.
 */
public class MockRepoTest {

  private static AtomicInteger customerIdCounter = new AtomicInteger(1000);
  private static AtomicInteger orderIdCounter = new AtomicInteger(1000);

  private static CustomersRepoMock createCustomersMock() {
    return new CustomersRepoMock(
        unsaved ->
            unsaved.toRow(
                () -> new CustomersId(customerIdCounter.getAndIncrement()),
                () -> Optional.of(LocalDateTime.now())));
  }

  private static OrdersRepoMock createOrdersMock() {
    return new OrdersRepoMock(
        unsaved ->
            unsaved.toRow(
                LocalDate::now,
                () -> Optional.of("pending"),
                () -> new OrdersId(orderIdCounter.getAndIncrement())));
  }

  @Test
  public void testMockInsertAndSelect() {
    var mock = createCustomersMock();
    var customer =
        new CustomersRow(
            new CustomersId(1), "Mock User", "mock@test.com", Optional.of(LocalDateTime.now()));

    var inserted = mock.insert(customer, null);
    assertNotNull(inserted);
    assertEquals("Mock User", inserted.name());

    var found = mock.selectById(new CustomersId(1), null);
    assertTrue(found.isPresent());
    assertEquals("Mock User", found.get().name());
  }

  @Test
  public void testMockUpdate() {
    var mock = createCustomersMock();
    var customer =
        new CustomersRow(
            new CustomersId(2),
            "Original Name",
            "original@test.com",
            Optional.of(LocalDateTime.now()));

    mock.insert(customer, null);

    var updated = customer.withName("Updated Name");
    mock.update(updated, null);

    var found = mock.selectById(new CustomersId(2), null).orElseThrow();
    assertEquals("Updated Name", found.name());
  }

  @Test
  public void testMockDelete() {
    var mock = createCustomersMock();
    var customer =
        new CustomersRow(
            new CustomersId(3), "To Delete", "delete@test.com", Optional.of(LocalDateTime.now()));

    mock.insert(customer, null);
    assertTrue(mock.selectById(new CustomersId(3), null).isPresent());

    mock.deleteById(new CustomersId(3), null);
    assertFalse(mock.selectById(new CustomersId(3), null).isPresent());
  }

  @Test
  public void testMockDSLQuery() {
    var mock = createCustomersMock();

    mock.insert(
        new CustomersRow(
            new CustomersId(100), "Alice", "alice@test.com", Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(101), "Bob", "bob@test.com", Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(102), "Charlie", "charlie@test.com", Optional.of(LocalDateTime.now())),
        null);

    var results =
        mock.select()
            .where(customer -> customer.customerId().greaterThan(new CustomersId(100)))
            .orderBy(customer -> customer.name().asc())
            .toList(null);

    assertEquals(2, results.size());
    assertEquals("Bob", results.get(0).name());
    assertEquals("Charlie", results.get(1).name());
  }

  @Test
  public void testMockDSLCount() {
    var mock = createCustomersMock();

    mock.insert(
        new CustomersRow(
            new CustomersId(200), "Count1", "count1@test.com", Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(201), "Count2", "count2@test.com", Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(202), "Other", "other@test.com", Optional.of(LocalDateTime.now())),
        null);

    var count =
        mock.select()
            .where(customer -> customer.name().like("Count%", Bijection.asString()))
            .count(null);

    assertEquals(2, count);
  }

  @Test
  public void testMockIsolation() {
    var mock1 = createCustomersMock();
    var mock2 = createCustomersMock();

    mock1.insert(
        new CustomersRow(
            new CustomersId(400), "Mock1 Only", "mock1@test.com", Optional.of(LocalDateTime.now())),
        null);

    assertTrue(mock1.selectById(new CustomersId(400), null).isPresent());
    assertFalse(mock2.selectById(new CustomersId(400), null).isPresent());
  }

  @Test
  public void testMockOrdersDSL() {
    var mock = createOrdersMock();

    mock.insert(
        new OrdersRow(
            new OrdersId(1),
            new CustomersId(1),
            LocalDate.now(),
            Optional.of(new BigDecimal("100.00")),
            Optional.of("PENDING")),
        null);
    mock.insert(
        new OrdersRow(
            new OrdersId(2),
            new CustomersId(1),
            LocalDate.now(),
            Optional.of(new BigDecimal("500.00")),
            Optional.of("SHIPPED")),
        null);

    var largeOrders =
        mock.select()
            .where(o -> o.totalAmount().greaterThan(new BigDecimal("200.00")))
            .toList(null);

    assertEquals(1, largeOrders.size());
  }
}
