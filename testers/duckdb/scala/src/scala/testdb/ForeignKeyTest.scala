package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.customers._
import testdb.customtypes.Defaulted
import testdb.order_items._
import testdb.orders._
import testdb.products._

import scala.util.Random

/** Tests for foreign key relationships between tables. Tests orders -> customers and order_items -> orders/products relationships.
  */
class ForeignKeyTest {
  private val testInsert = TestInsert(Random(578840590))
  private val customersRepo = CustomersRepoImpl()
  private val productsRepo = ProductsRepoImpl()
  private val ordersRepo = OrdersRepoImpl()
  private val orderItemsRepo = OrderItemsRepoImpl()

  @Test
  def testOrderReferencesCustomer(): Unit = withConnection {
    val customer = testInsert.Customers()
    val order = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("99.99")))

    assertNotNull(order)
    assertEquals(customer.customerId.value, order.customerId)

    val foundCustomer = customersRepo.selectById(CustomersId(order.customerId))
    assertTrue(foundCustomer.isDefined)
    assertEquals(customer.name, foundCustomer.get.name)
  }

  @Test
  def testOrderItemsReferencesOrderAndProduct(): Unit = withConnection {
    val customer = testInsert.Customers()
    val product = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId.value)

    val orderItem = testInsert.OrderItems(
      orderId = order.orderId.value,
      productId = product.productId.value,
      quantity = Defaulted.Provided(5),
      unitPrice = BigDecimal("19.99")
    )

    assertNotNull(orderItem)
    assertEquals(order.orderId.value, orderItem.orderId)
    assertEquals(product.productId.value, orderItem.productId)

    val foundOrder = ordersRepo.selectById(OrdersId(orderItem.orderId))
    assertTrue(foundOrder.isDefined)

    val foundProduct = productsRepo.selectById(ProductsId(orderItem.productId))
    assertTrue(foundProduct.isDefined)
  }

  @Test
  def testMultipleOrdersForCustomer(): Unit = withConnection {
    val customer = testInsert.Customers()

    val order1 = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("100.00")))
    val order2 = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("200.00")))
    val order3 = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("300.00")))

    assertEquals(customer.customerId.value, order1.customerId)
    assertEquals(customer.customerId.value, order2.customerId)
    assertEquals(customer.customerId.value, order3.customerId)

    assertNotEquals(order1.orderId, order2.orderId)
    assertNotEquals(order2.orderId, order3.orderId)
  }

  @Test
  def testMultipleItemsForOrder(): Unit = withConnection {
    val customer = testInsert.Customers()
    val product1 = testInsert.Products()
    val product2 = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId.value)

    val item1 = testInsert.OrderItems(
      orderId = order.orderId.value,
      productId = product1.productId.value,
      quantity = Defaulted.Provided(2),
      unitPrice = BigDecimal("10.00")
    )
    val item2 = testInsert.OrderItems(
      orderId = order.orderId.value,
      productId = product2.productId.value,
      quantity = Defaulted.Provided(3),
      unitPrice = BigDecimal("15.00")
    )

    assertEquals(order.orderId.value, item1.orderId)
    assertEquals(order.orderId.value, item2.orderId)
    assertEquals(product1.productId.value, item1.productId)
    assertEquals(product2.productId.value, item2.productId)
  }

  @Test
  def testCascadingForeignKeys(): Unit = withConnection {
    val customer = testInsert.Customers()
    val product = testInsert.Products()
    val order = testInsert.Orders(customerId = customer.customerId.value)
    val orderItem = testInsert.OrderItems(
      orderId = order.orderId.value,
      productId = product.productId.value,
      unitPrice = BigDecimal("25.00")
    )

    val foundItem = orderItemsRepo.selectById(orderItem.id)
    assertTrue(foundItem.isDefined)

    val foundOrder = ordersRepo.selectById(OrdersId(foundItem.get.orderId))
    assertTrue(foundOrder.isDefined)

    val foundCustomer = customersRepo.selectById(CustomersId(foundOrder.get.customerId))
    assertTrue(foundCustomer.isDefined)

    assertEquals(customer.customerId, foundCustomer.get.customerId)
  }
}
