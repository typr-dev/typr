package testdb

import dev.typr.foundations.dsl.Bijection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.departments.*
import testdb.userdefined.Email

import java.time.LocalDateTime

class MockRepoTest {
  @Test
  def testMockInsertAndSelect(): Unit = {
    val mock = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )
    val customer = CustomersRow(
      CustomersId(1),
      "Mock User",
      Some(Email("mock@test.com")),
      LocalDateTime.now(),
      Some(Priority.medium)
    )

    val inserted = mock.insert(customer)(using null)
    assertNotNull(inserted)
    assertEquals("Mock User", inserted.name)

    val found = mock.selectById(CustomersId(1))(using null)
    assertTrue(found.isDefined)
    assertEquals("Mock User", found.get.name)
  }

  @Test
  def testMockUpdate(): Unit = {
    val mock = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )
    val customer = CustomersRow(CustomersId(2), "Original Name", None, LocalDateTime.now(), None)

    val _ = mock.insert(customer)(using null)

    val updated = customer.copy(name = "Updated Name")
    val _ = mock.update(updated)(using null)

    val found = mock.selectById(CustomersId(2))(using null).get
    assertEquals("Updated Name", found.name)
  }

  @Test
  def testMockDelete(): Unit = {
    val mock = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )
    val customer = CustomersRow(CustomersId(3), "To Delete", None, LocalDateTime.now(), None)

    val _ = mock.insert(customer)(using null)
    assertTrue(mock.selectById(CustomersId(3))(using null).isDefined)

    val _ = mock.deleteById(CustomersId(3))(using null)
    assertFalse(mock.selectById(CustomersId(3))(using null).isDefined)
  }

  @Test
  def testMockWithCompositeKey(): Unit = {
    val mock = DepartmentsRepoMock()
    val dept = DepartmentsRow("MOCK_IT", "MOCK_US", "Mock IT US", Some(BigDecimal("500000")))

    val _ = mock.insert(dept)(using null)

    val id = DepartmentsId("MOCK_IT", "MOCK_US")
    val found = mock.selectById(id)(using null)

    assertTrue(found.isDefined)
    assertEquals("Mock IT US", found.get.deptName)
  }

  @Test
  def testMockDSLQuery(): Unit = {
    val mock = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )

    val _ = mock.insert(CustomersRow(CustomersId(100), "Alice", None, LocalDateTime.now(), None))(using null)
    val _ = mock.insert(CustomersRow(CustomersId(101), "Bob", None, LocalDateTime.now(), None))(using null)
    val _ = mock.insert(CustomersRow(CustomersId(102), "Charlie", None, LocalDateTime.now(), None))(using null)

    val results = mock.select
      .where(c => c.customerId.greaterThan(CustomersId(100)))
      .orderBy(c => c.name.asc)
      .toList(using null)

    assertEquals(2, results.size)
    assertEquals("Bob", results.head.name)
    assertEquals("Charlie", results(1).name)
  }

  @Test
  def testMockDSLCount(): Unit = {
    val mock = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )

    val _ = mock.insert(CustomersRow(CustomersId(200), "Count1", None, LocalDateTime.now(), None))(using null)
    val _ = mock.insert(CustomersRow(CustomersId(201), "Count2", None, LocalDateTime.now(), None))(using null)
    val _ = mock.insert(CustomersRow(CustomersId(202), "Other", None, LocalDateTime.now(), None))(using null)

    val count = mock.select
      .where(c => c.name.like("Count%", Bijection.asString()))
      .count(using null)

    assertEquals(2, count)
  }

  @Test
  def testMockIsolation(): Unit = {
    val mock1 = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )
    val mock2 = CustomersRepoMock(
      toRow = unsaved => unsaved.toRow(LocalDateTime.now(), Some(Priority.medium))
    )

    val _ = mock1.insert(CustomersRow(CustomersId(400), "Mock1 Only", None, LocalDateTime.now(), None))(using null)

    assertTrue(mock1.selectById(CustomersId(400))(using null).isDefined)
    assertFalse(mock2.selectById(CustomersId(400))(using null).isDefined)
  }
}
