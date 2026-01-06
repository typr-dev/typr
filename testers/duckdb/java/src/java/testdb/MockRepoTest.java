package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.Bijection;
import dev.typr.foundations.dsl.MockConnection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.customers.*;
import testdb.departments.*;
import testdb.userdefined.Email;

/** Tests for mock repository implementations in DuckDB. */
public class MockRepoTest {
  private static CustomersRepoMock createCustomersMock() {
    return new CustomersRepoMock(
        unsaved -> unsaved.toRow(LocalDateTime::now, () -> Optional.of(Priority.medium)));
  }

  private final CustomersRepoMock customersMock = createCustomersMock();
  private final DepartmentsRepoMock departmentsMock = new DepartmentsRepoMock();

  @Test
  public void testMockInsertAndSelect() {
    var customer =
        new CustomersRow(
            new CustomersId(1),
            "Mock User",
            Optional.of(new Email("mock@test.com")),
            LocalDateTime.now(),
            Optional.of(Priority.medium));

    var inserted = customersMock.insert(customer, null);
    assertNotNull(inserted);
    assertEquals("Mock User", inserted.name());

    var found = customersMock.selectById(new CustomersId(1), null);
    assertTrue(found.isPresent());
    assertEquals("Mock User", found.get().name());
  }

  @Test
  public void testMockUpdate() {
    var customer =
        new CustomersRow(
            new CustomersId(2),
            "Original Name",
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty());

    customersMock.insert(customer, null);

    var updated = customer.withName("Updated Name");
    customersMock.update(updated, null);

    var found = customersMock.selectById(new CustomersId(2), null).orElseThrow();
    assertEquals("Updated Name", found.name());
  }

  @Test
  public void testMockDelete() {
    var customer =
        new CustomersRow(
            new CustomersId(3),
            "To Delete",
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty());

    customersMock.insert(customer, null);
    assertTrue(customersMock.selectById(new CustomersId(3), null).isPresent());

    customersMock.deleteById(new CustomersId(3), null);
    assertFalse(customersMock.selectById(new CustomersId(3), null).isPresent());
  }

  @Test
  public void testMockSelectAll() {
    var c1 =
        new CustomersRow(
            new CustomersId(10), "User 1", Optional.empty(), LocalDateTime.now(), Optional.empty());
    var c2 =
        new CustomersRow(
            new CustomersId(11), "User 2", Optional.empty(), LocalDateTime.now(), Optional.empty());
    var c3 =
        new CustomersRow(
            new CustomersId(12), "User 3", Optional.empty(), LocalDateTime.now(), Optional.empty());

    customersMock.insert(c1, null);
    customersMock.insert(c2, null);
    customersMock.insert(c3, null);

    var all = customersMock.selectAll(null);
    assertTrue(all.size() >= 3);
  }

  @Test
  public void testMockWithCompositeKey() {
    var dept =
        new DepartmentsRow(
            "MOCK_IT", "MOCK_US", "Mock IT US", Optional.of(new BigDecimal("500000")));

    departmentsMock.insert(dept, null);

    var id = new DepartmentsId("MOCK_IT", "MOCK_US");
    var found = departmentsMock.selectById(id, null);

    assertTrue(found.isPresent());
    assertEquals("Mock IT US", found.get().deptName());
  }

  @Test
  public void testMockDSLQuery() {
    // Clear any existing data
    var mock = createCustomersMock();

    mock.insert(
        new CustomersRow(
            new CustomersId(100), "Alice", Optional.empty(), LocalDateTime.now(), Optional.empty()),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(101), "Bob", Optional.empty(), LocalDateTime.now(), Optional.empty()),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(102),
            "Charlie",
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty()),
        null);

    var results =
        mock.select()
            .where(c -> c.customerId().greaterThan(new CustomersId(100)))
            .orderBy(c -> c.name().asc())
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
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty()),
        MockConnection.instance);
    mock.insert(
        new CustomersRow(
            new CustomersId(201),
            "Count2",
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty()),
        MockConnection.instance);
    mock.insert(
        new CustomersRow(
            new CustomersId(202), "Other", Optional.empty(), LocalDateTime.now(), Optional.empty()),
        MockConnection.instance);

    var count =
        mock.select()
            .where(c -> c.name().like("Count%", Bijection.asString()))
            .count(MockConnection.instance);

    assertEquals(2, count);
  }

  @Test
  public void testMockDSLLimit() {
    var mock = createCustomersMock();

    for (int i = 0; i < 10; i++) {
      mock.insert(
          new CustomersRow(
              new CustomersId(300 + i),
              "Limit" + i,
              Optional.empty(),
              LocalDateTime.now(),
              Optional.empty()),
          null);
    }

    var results =
        mock.select()
            .where(c -> c.name().like("Limit%", Bijection.asString()))
            .limit(5)
            .toList(null);

    assertEquals(5, results.size());
  }

  @Test
  public void testMockIsolation() {
    // Two mock instances should be independent
    var mock1 = createCustomersMock();
    var mock2 = createCustomersMock();

    mock1.insert(
        new CustomersRow(
            new CustomersId(400),
            "Mock1 Only",
            Optional.empty(),
            LocalDateTime.now(),
            Optional.empty()),
        null);

    assertTrue(mock1.selectById(new CustomersId(400), null).isPresent());
    assertFalse(mock2.selectById(new CustomersId(400), null).isPresent());
  }
}
