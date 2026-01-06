package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.userdefined.Email

import scala.util.Random

/** Tests for the TestInsert helper class that generates random test data. Tests seeded randomness, customization, and foreign key handling.
  */
class TestInsertTest {
  private val testInsert = TestInsert(Random(42))

  @Test
  def testCustomersInsert(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row = testInsert.Customers(email = Email("test@example.com"))
    assertNotNull(row)
    assertNotNull(row.customerId)
    assertNotNull(row.name)
  }

  @Test
  def testCustomersWithCustomization(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row = testInsert.Customers(email = Email("custom@example.com"), name = "Custom Name")

    assertNotNull(row)
    assertEquals("Custom Name", row.name)
  }

  @Test
  def testProductsInsert(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row = testInsert.Products()

    assertNotNull(row)
    assertNotNull(row.productId)
    assertNotNull(row.name)
    assertNotNull(row.price)
  }

  @Test
  def testAllScalarTypesInsert(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row = testInsert.AllScalarTypes()

    assertNotNull(row)
    assertNotNull(row.id)
    assertNotNull(row.colRowversion)
  }

  @Test
  def testOrdersWithCustomerFK(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("orders-fk@example.com"))

    val order = testInsert.Orders(customer.customerId)

    assertNotNull(order)
    assertEquals(customer.customerId, order.customerId)
  }

  @Test
  def testOrderItemsWithFKs(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("orderitems-fk@example.com"))
    val product = testInsert.Products()
    val order = testInsert.Orders(customer.customerId)

    val orderItem = testInsert.OrderItems(order.orderId, product.productId)

    assertNotNull(orderItem)
    assertEquals(order.orderId, orderItem.orderId)
    assertEquals(product.productId, orderItem.productId)
  }

  @Test
  def testMultipleInserts(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row1 = testInsert.Customers(email = Email("multi1@example.com"))
    val row2 = testInsert.Customers(email = Email("multi2@example.com"))
    val row3 = testInsert.Customers(email = Email("multi3@example.com"))

    assertNotEquals(row1.customerId, row2.customerId)
    assertNotEquals(row2.customerId, row3.customerId)
    assertNotEquals(row1.customerId, row3.customerId)
  }

  @Test
  def testInsertWithSeededRandom(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val testInsert1 = TestInsert(Random(123))
    val testInsert2 = TestInsert(Random(123))

    val row1 = testInsert1.Customers(email = Email("seeded1@test.com"))
    // Use different email to avoid UNIQUE constraint violation (same seed = same email)
    val row2 = testInsert2.Customers(email = Email("seeded2@test.com"))

    assertEquals(row1.name, row2.name)
  }

  @Test
  def testChainedCustomization(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val row = testInsert.Customers(email = Email("first@test.com"), name = "First")

    assertEquals("First", row.name)
    assertEquals(Email("first@test.com"), row.email)
  }
}
