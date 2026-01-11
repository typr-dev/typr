package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.Bijection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import testdb.customers.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Tests for mock repository implementations. Mock repos provide in-memory implementations for unit
 * testing without database access.
 */
public class MockRepoTest {

  private static AtomicInteger customerIdCounter = new AtomicInteger(1000);
  private static AtomicInteger productIdCounter = new AtomicInteger(1000);

  private static CustomersRepoMock createCustomersMock() {
    return new CustomersRepoMock(
        unsaved ->
            unsaved.toRow(
                () -> Optional.of(LocalDateTime.now()),
                () -> new CustomersId(customerIdCounter.getAndIncrement())));
  }

  private static ProductsRepoMock createProductsMock() {
    return new ProductsRepoMock(
        unsaved -> unsaved.toRow(() -> new ProductsId(productIdCounter.getAndIncrement())));
  }

  @Test
  public void testMockInsertAndSelect() {
    var mock = createCustomersMock();
    var customer =
        new CustomersRow(
            new CustomersId(1),
            "Mock User",
            new Email("mock@test.com"),
            Optional.of(LocalDateTime.now()));

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
            new Email("original@test.com"),
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
            new CustomersId(3),
            "To Delete",
            new Email("delete@test.com"),
            Optional.of(LocalDateTime.now()));

    mock.insert(customer, null);
    assertTrue(mock.selectById(new CustomersId(3), null).isPresent());

    mock.deleteById(new CustomersId(3), null);
    assertFalse(mock.selectById(new CustomersId(3), null).isPresent());
  }

  @Test
  public void testMockProductsInsertAndSelect() {
    var mock = createProductsMock();
    var product =
        new ProductsRow(
            new ProductsId(1),
            "Test Product",
            new BigDecimal("29.99"),
            Optional.of("A test product"));

    var inserted = mock.insert(product, null);
    assertNotNull(inserted);
    assertEquals("Test Product", inserted.name());

    var found = mock.selectById(new ProductsId(1), null);
    assertTrue(found.isPresent());
    assertEquals(0, new BigDecimal("29.99").compareTo(found.get().price()));
  }

  @Test
  public void testMockDSLQuery() {
    var mock = createCustomersMock();

    mock.insert(
        new CustomersRow(
            new CustomersId(100),
            "Alice",
            new Email("alice@test.com"),
            Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(101),
            "Bob",
            new Email("bob@test.com"),
            Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(102),
            "Charlie",
            new Email("charlie@test.com"),
            Optional.of(LocalDateTime.now())),
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
            new CustomersId(200),
            "Count1",
            new Email("count1@test.com"),
            Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(201),
            "Count2",
            new Email("count2@test.com"),
            Optional.of(LocalDateTime.now())),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(202),
            "Other",
            new Email("other@test.com"),
            Optional.of(LocalDateTime.now())),
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
            new CustomersId(400),
            "Mock1 Only",
            new Email("mock1@test.com"),
            Optional.of(LocalDateTime.now())),
        null);

    assertTrue(mock1.selectById(new CustomersId(400), null).isPresent());
    assertFalse(mock2.selectById(new CustomersId(400), null).isPresent());
  }

  @Test
  public void testMockProductsDSL() {
    var mock = createProductsMock();

    mock.insert(
        new ProductsRow(
            new ProductsId(1), "Cheap", new BigDecimal("9.99"), Optional.of("Cheap product")),
        null);
    mock.insert(
        new ProductsRow(
            new ProductsId(2), "Medium", new BigDecimal("49.99"), Optional.of("Medium product")),
        null);
    mock.insert(
        new ProductsRow(
            new ProductsId(3),
            "Expensive",
            new BigDecimal("199.99"),
            Optional.of("Expensive product")),
        null);

    var expensiveProducts =
        mock.select().where(p -> p.price().greaterThan(new BigDecimal("50.00"))).toList(null);

    assertEquals(1, expensiveProducts.size());
    assertEquals("Expensive", expensiveProducts.get(0).name());
  }
}
