package testdb

import dev.typr.foundations.dsl.Bijection
import org.junit.Assert._
import org.junit.Test
import testdb.customers._
import testdb.products._
import testdb.userdefined.Email

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/** Tests for mock repository implementations. Mock repos provide in-memory implementations for unit testing without database access.
  */
class MockRepoTest {

  private val customerIdCounter = new AtomicInteger(1000)
  private val productIdCounter = new AtomicInteger(1000)

  private def createCustomersMock(): CustomersRepoMock = {
    CustomersRepoMock(unsaved =>
      unsaved.toRow(
        Some(LocalDateTime.now()),
        CustomersId(customerIdCounter.getAndIncrement())
      )
    )
  }

  private def createProductsMock(): ProductsRepoMock = {
    ProductsRepoMock(unsaved => unsaved.toRow(ProductsId(productIdCounter.getAndIncrement())))
  }

  @Test
  def testMockInsertAndSelect(): Unit = {
    val mock = createCustomersMock()
    val customer = CustomersRow(
      CustomersId(1),
      "Mock User",
      Email("mock@test.com"),
      Some(LocalDateTime.now())
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
    val mock = createCustomersMock()
    val customer = CustomersRow(
      CustomersId(2),
      "Original Name",
      Email("original@test.com"),
      Some(LocalDateTime.now())
    )

    val _ = mock.insert(customer)(using null)

    val updated = customer.copy(name = "Updated Name")
    val _ = mock.update(updated)(using null)

    val found = mock.selectById(CustomersId(2))(using null).get
    assertEquals("Updated Name", found.name)
  }

  @Test
  def testMockDelete(): Unit = {
    val mock = createCustomersMock()
    val customer = CustomersRow(
      CustomersId(3),
      "To Delete",
      Email("delete@test.com"),
      Some(LocalDateTime.now())
    )

    val _ = mock.insert(customer)(using null)
    assertTrue(mock.selectById(CustomersId(3))(using null).isDefined)

    val _ = mock.deleteById(CustomersId(3))(using null)
    assertFalse(mock.selectById(CustomersId(3))(using null).isDefined)
  }

  @Test
  def testMockProductsInsertAndSelect(): Unit = {
    val mock = createProductsMock()
    val product = ProductsRow(
      ProductsId(1),
      "Test Product",
      BigDecimal("29.99"),
      Some("A test product")
    )

    val inserted = mock.insert(product)(using null)
    assertNotNull(inserted)
    assertEquals("Test Product", inserted.name)

    val found = mock.selectById(ProductsId(1))(using null)
    assertTrue(found.isDefined)
    assertEquals(0, BigDecimal("29.99").compareTo(found.get.price))
  }

  @Test
  def testMockDSLQuery(): Unit = {
    val mock = createCustomersMock()

    val _ = mock.insert(
      CustomersRow(
        CustomersId(100),
        "Alice",
        Email("alice@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)
    val _ = mock.insert(
      CustomersRow(
        CustomersId(101),
        "Bob",
        Email("bob@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)
    val _ = mock.insert(
      CustomersRow(
        CustomersId(102),
        "Charlie",
        Email("charlie@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)

    val results = mock.select
      .where(_.customerId.greaterThan(CustomersId(100)))
      .orderBy(_.name.asc)
      .toList(using null)

    assertEquals(2, results.size)
    assertEquals("Bob", results.head.name)
    assertEquals("Charlie", results(1).name)
  }

  @Test
  def testMockDSLCount(): Unit = {
    val mock = createCustomersMock()

    val _ = mock.insert(
      CustomersRow(
        CustomersId(200),
        "Count1",
        Email("count1@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)
    val _ = mock.insert(
      CustomersRow(
        CustomersId(201),
        "Count2",
        Email("count2@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)
    val _ = mock.insert(
      CustomersRow(
        CustomersId(202),
        "Other",
        Email("other@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)

    val count = mock.select
      .where(_.name.like("Count%", Bijection.asString()))
      .count(using null)

    assertEquals(2, count)
  }

  @Test
  def testMockIsolation(): Unit = {
    val mock1 = createCustomersMock()
    val mock2 = createCustomersMock()

    val _ = mock1.insert(
      CustomersRow(
        CustomersId(400),
        "Mock1 Only",
        Email("mock1@test.com"),
        Some(LocalDateTime.now())
      )
    )(using null)

    assertTrue(mock1.selectById(CustomersId(400))(using null).isDefined)
    assertFalse(mock2.selectById(CustomersId(400))(using null).isDefined)
  }

  @Test
  def testMockProductsDSL(): Unit = {
    val mock = createProductsMock()

    val _ = mock.insert(
      ProductsRow(
        ProductsId(1),
        "Cheap",
        BigDecimal("9.99"),
        Some("Cheap product")
      )
    )(using null)
    val _ = mock.insert(
      ProductsRow(
        ProductsId(2),
        "Medium",
        BigDecimal("49.99"),
        Some("Medium product")
      )
    )(using null)
    val _ = mock.insert(
      ProductsRow(
        ProductsId(3),
        "Expensive",
        BigDecimal("199.99"),
        Some("Expensive product")
      )
    )(using null)

    val expensiveProducts = mock.select
      .where(_.price.greaterThan(BigDecimal("50.00")))
      .toList(using null)

    assertEquals(1, expensiveProducts.size)
    assertEquals("Expensive", expensiveProducts.head.name)
  }
}
