package testdb

import dev.typr.foundations.Bijection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.departments.*
import testdb.userdefined.Email

import java.time.LocalDateTime

class MockRepoTest {
  private def mkMock(): CustomersRepoMock =
    CustomersRepoMock(toRow = unsaved => unsaved.toRow(LocalDateTime.of(2025, 1, 1, 0, 0)))

  @Test def testMockInsertAndSelect(): Unit = {
    val mock = mkMock()
    val row = CustomersRow(CustomersId(1L), "Mock User", Some(Email("mock@test.com")), LocalDateTime.of(2025, 1, 1, 0, 0))
    val inserted = mock.insert(row)(using null)
    assertEquals("Mock User", inserted.name)
    val found = mock.selectById(CustomersId(1L))(using null)
    assertTrue(found.isDefined)
    assertEquals("Mock User", found.get.name)
  }

  @Test def testMockUpdate(): Unit = {
    val mock = mkMock()
    val row = CustomersRow(CustomersId(2L), "Original", None, LocalDateTime.of(2025, 1, 1, 0, 0))
    mock.insert(row)(using null)
    mock.update(row.copy(name = "Updated"))(using null)
    assertEquals("Updated", mock.selectById(CustomersId(2L))(using null).get.name)
  }

  @Test def testMockDelete(): Unit = {
    val mock = mkMock()
    mock.insert(CustomersRow(CustomersId(3L), "Del", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    assertTrue(mock.selectById(CustomersId(3L))(using null).isDefined)
    mock.deleteById(CustomersId(3L))(using null)
    assertFalse(mock.selectById(CustomersId(3L))(using null).isDefined)
  }

  @Test def testMockSelectAll(): Unit = {
    val mock = mkMock()
    mock.insert(CustomersRow(CustomersId(10L), "U1", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(11L), "U2", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(12L), "U3", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    assertEquals(3, mock.selectAll(using null).size)
  }

  @Test def testMockDSLLimit(): Unit = {
    val mock = mkMock()
    (0 until 10).foreach { i =>
      mock.insert(CustomersRow(CustomersId(300L + i), s"Limit$i", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    }
    val results = mock.select.where(c => c.name.like("Limit%", Bijection.asString())).limit(5).toList(using null)
    assertEquals(5, results.size)
  }

  @Test def testMockWithCompositeKey(): Unit = {
    val mock = DepartmentsRepoMock()
    mock.insert(DepartmentsRow("MOCK_IT", "MOCK_US", "Mock IT US", Some(BigDecimal("500000"))))(using null)
    val found = mock.selectById(DepartmentsId("MOCK_IT", "MOCK_US"))(using null)
    assertTrue(found.isDefined)
    assertEquals("Mock IT US", found.get.deptName)
  }

  @Test def testMockDSLQuery(): Unit = {
    val mock = mkMock()
    mock.insert(CustomersRow(CustomersId(100L), "Alice", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(101L), "Bob", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(102L), "Charlie", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)

    val results = mock.select
      .where(c => c.customerId.greaterThan(CustomersId(100L)))
      .orderBy(c => c.name.asc)
      .toList(using null)

    assertEquals(2, results.size)
    assertEquals("Bob", results.head.name)
    assertEquals("Charlie", results(1).name)
  }

  @Test def testMockDSLCount(): Unit = {
    val mock = mkMock()
    mock.insert(CustomersRow(CustomersId(200L), "Count1", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(201L), "Count2", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    mock.insert(CustomersRow(CustomersId(202L), "Other", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)

    val count = mock.select.where(c => c.name.like("Count%", Bijection.asString())).count(using null)
    assertEquals(2, count)
  }

  @Test def testMockIsolation(): Unit = {
    val mock1 = mkMock()
    val mock2 = mkMock()
    mock1.insert(CustomersRow(CustomersId(400L), "Mock1", None, LocalDateTime.of(2025, 1, 1, 0, 0)))(using null)
    assertTrue(mock1.selectById(CustomersId(400L))(using null).isDefined)
    assertFalse(mock2.selectById(CustomersId(400L))(using null).isDefined)
  }
}
