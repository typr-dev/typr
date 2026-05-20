package testdb;

import static org.junit.Assert.*;

import dev.typr.dsl.MockConnection;
import dev.typr.foundations.Bijection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.customers.*;
import testdb.departments.*;
import testdb.userdefined.Email;

/** Generated mock repositories — exercised entirely in-process, no SQLite connection. */
public class MockRepoTest {
  private static CustomersRepoMock createCustomersMock() {
    return new CustomersRepoMock(
        unsaved -> unsaved.toRow(() -> LocalDateTime.of(2025, 1, 1, 0, 0)));
  }

  private final CustomersRepoMock customersMock = createCustomersMock();
  private final DepartmentsRepoMock departmentsMock = new DepartmentsRepoMock();

  @Test
  public void testMockInsertAndSelect() {
    var customer =
        new CustomersRow(
            new CustomersId(1L),
            "Mock User",
            Optional.of(new Email("mock@test.com")),
            LocalDateTime.of(2025, 1, 1, 0, 0));

    var inserted = customersMock.insert(customer, null);
    assertNotNull(inserted);
    assertEquals("Mock User", inserted.name());

    var found = customersMock.selectById(new CustomersId(1L), null);
    assertTrue(found.isPresent());
    assertEquals("Mock User", found.get().name());
  }

  @Test
  public void testMockUpdate() {
    var customer =
        new CustomersRow(
            new CustomersId(2L),
            "Original Name",
            Optional.empty(),
            LocalDateTime.of(2025, 1, 1, 0, 0));
    customersMock.insert(customer, null);

    var updated = customer.withName("Updated Name");
    customersMock.update(updated, null);

    var found = customersMock.selectById(new CustomersId(2L), null).orElseThrow();
    assertEquals("Updated Name", found.name());
  }

  @Test
  public void testMockDelete() {
    var customer =
        new CustomersRow(
            new CustomersId(3L), "To Delete", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0));
    customersMock.insert(customer, null);
    assertTrue(customersMock.selectById(new CustomersId(3L), null).isPresent());

    customersMock.deleteById(new CustomersId(3L), null);
    assertFalse(customersMock.selectById(new CustomersId(3L), null).isPresent());
  }

  @Test
  public void testMockSelectAll() {
    var mock = createCustomersMock();
    mock.insert(
        new CustomersRow(
            new CustomersId(10L), "U1", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(11L), "U2", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(12L), "U3", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);

    var all = mock.selectAll(null);
    assertEquals(3, all.size());
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
    var mock = createCustomersMock();

    mock.insert(
        new CustomersRow(
            new CustomersId(100L), "Alice", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(101L), "Bob", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);
    mock.insert(
        new CustomersRow(
            new CustomersId(102L), "Charlie", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);

    var results =
        mock.select()
            .where(c -> c.customerId().greaterThan(new CustomersId(100L)))
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
            new CustomersId(200L), "Count1", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        MockConnection.instance);
    mock.insert(
        new CustomersRow(
            new CustomersId(201L), "Count2", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
        MockConnection.instance);
    mock.insert(
        new CustomersRow(
            new CustomersId(202L), "Other", Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0)),
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
              new CustomersId(300L + i),
              "Limit" + i,
              Optional.empty(),
              LocalDateTime.of(2025, 1, 1, 0, 0)),
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
    var mock1 = createCustomersMock();
    var mock2 = createCustomersMock();

    mock1.insert(
        new CustomersRow(
            new CustomersId(400L),
            "Mock1 Only",
            Optional.empty(),
            LocalDateTime.of(2025, 1, 1, 0, 0)),
        null);

    assertTrue(mock1.selectById(new CustomersId(400L), null).isPresent());
    assertFalse(mock2.selectById(new CustomersId(400L), null).isPresent());
  }
}
