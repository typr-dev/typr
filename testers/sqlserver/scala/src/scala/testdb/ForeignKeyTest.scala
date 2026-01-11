package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.customers._
import testdb.order_items._
import testdb.orders._
import testdb.products._
import testdb.userdefined.Email

import scala.util.Random

/** Tests for foreign key relationships between tables. Tests orders -> customers and order_items -> orders/products relationships.
  */
class ForeignKeyTest {
  private val testInsert = TestInsert(Random(42))
  private val customersRepo = CustomersRepoImpl()
  private val productsRepo = ProductsRepoImpl()
  private val ordersRepo = OrdersRepoImpl()
  private val orderItemsRepo = OrderItemsRepoImpl()

  @Test
  def testOrderReferencesCustomer(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("order-ref@example.com"))
    val order = testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("99.99"))

    assertNotNull(order)
    assertEquals(customer.customerId, order.customerId)

    val foundCustomer = customersRepo.selectById(order.customerId)
    assertTrue(foundCustomer.isDefined)
    assertEquals(customer.name, foundCustomer.get.name)
  }

  @Test
  def testOrderItemsReferencesOrderAndProduct(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("item-refs@example.com"))
    val product = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId)

    val orderItem = testInsert.OrderItems(
      orderId = order.orderId,
      productId = product.productId,
      quantity = 5,
      price = BigDecimal("19.99")
    )

    assertNotNull(orderItem)
    assertEquals(order.orderId, orderItem.orderId)
    assertEquals(product.productId, orderItem.productId)

    val foundOrder = ordersRepo.selectById(orderItem.orderId)
    assertTrue(foundOrder.isDefined)

    val foundProduct = productsRepo.selectById(orderItem.productId)
    assertTrue(foundProduct.isDefined)
  }

  @Test
  def testMultipleOrdersForCustomer(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("multi-orders@example.com"))

    val order1 = testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("100.00"))
    val order2 = testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("200.00"))
    val order3 = testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("300.00"))

    assertEquals(customer.customerId, order1.customerId)
    assertEquals(customer.customerId, order2.customerId)
    assertEquals(customer.customerId, order3.customerId)

    assertNotEquals(order1.orderId, order2.orderId)
    assertNotEquals(order2.orderId, order3.orderId)
  }

  @Test
  def testMultipleItemsForOrder(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("multi-items@example.com"))
    val product1 = testInsert.Products()
    val product2 = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId)

    val item1 = testInsert.OrderItems(
      orderId = order.orderId,
      productId = product1.productId,
      quantity = 2,
      price = BigDecimal("10.00")
    )
    val item2 = testInsert.OrderItems(
      orderId = order.orderId,
      productId = product2.productId,
      quantity = 3,
      price = BigDecimal("15.00")
    )

    assertEquals(order.orderId, item1.orderId)
    assertEquals(order.orderId, item2.orderId)
    assertEquals(product1.productId, item1.productId)
    assertEquals(product2.productId, item2.productId)
  }

  @Test
  def testCascadingForeignKeys(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("cascade-fk@example.com"))
    val product = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId)
    val orderItem = testInsert.OrderItems(
      orderId = order.orderId,
      productId = product.productId,
      price = BigDecimal("25.00")
    )

    val foundItem = orderItemsRepo.selectById(orderItem.orderItemId)
    assertTrue(foundItem.isDefined)

    val foundOrder = ordersRepo.selectById(foundItem.get.orderId)
    assertTrue(foundOrder.isDefined)

    val foundCustomer = customersRepo.selectById(foundOrder.get.customerId)
    assertTrue(foundCustomer.isDefined)

    assertEquals(customer.customerId, foundCustomer.get.customerId)
  }
}
