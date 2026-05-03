package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.customer_orders._
import testdb.customer_summary._
import testdb.orders_by_customer._

import scala.util.Random

/** Tests for SQL script generated repositories. These tests exercise the typed query classes generated from SQL files in sql-scripts/db2/.
  */
class SqlScriptTest {
  private val testInsert = TestInsert(Random(1874089758))
  private val customerOrdersRepo = CustomerOrdersSqlRepoImpl()
  private val customerSummaryRepo = CustomerSummarySqlRepoImpl()
  private val ordersByCustomerRepo = OrdersByCustomerSqlRepoImpl()

  @Test
  def testCustomerOrders(): Unit = withConnection {
    val customer = testInsert.Customers()
    val _ = testInsert.Orders(customer.customerId, totalAmount = Some(BigDecimal("150.00")))

    val results = customerOrdersRepo(customer.customerId.value)

    assertTrue(results.nonEmpty)
    assertEquals(customer.customerId.value, results.head.customerId)
  }

  @Test
  def testCustomerOrdersMultiple(): Unit = withConnection {
    val customer = testInsert.Customers()
    val _ = testInsert.Orders(customer.customerId)
    val _ = testInsert.Orders(customer.customerId)
    val _ = testInsert.Orders(customer.customerId)

    val results = customerOrdersRepo(customer.customerId.value)

    assertEquals(3, results.size)
  }

  @Test
  def testCustomerSummary(): Unit = withConnection {
    val customer = testInsert.Customers()
    val _ = testInsert.Orders(customer.customerId, totalAmount = Some(BigDecimal("100.00")))
    val _ = testInsert.Orders(customer.customerId, totalAmount = Some(BigDecimal("200.00")))

    val results = customerSummaryRepo.apply

    assertTrue(results.nonEmpty)
    val summary = results.find(_.customerId == customer.customerId.value)
    assertTrue(summary.isDefined)
    assertEquals(2, summary.get.orderCount.getOrElse(0))
  }

  @Test
  def testCustomerSummaryNoOrders(): Unit = withConnection {
    val customer = testInsert.Customers()

    val results = customerSummaryRepo.apply

    assertTrue(results.nonEmpty)
    val summary = results.find(_.customerId == customer.customerId.value)
    assertTrue(summary.isDefined)
    assertEquals(0, summary.get.orderCount.getOrElse(0))
    assertEquals(0, BigDecimal(0).compareTo(summary.get.totalSpent.getOrElse(BigDecimal(0))))
  }

  @Test
  def testOrdersByCustomer(): Unit = withConnection {
    val customer = testInsert.Customers()
    val order1 = testInsert.Orders(customer.customerId)
    val order2 = testInsert.Orders(customer.customerId)
    // SQL uses INNER JOIN with order_items, so we need to create order items
    val _ = testInsert.OrderItems(order1.orderId)
    val _ = testInsert.OrderItems(order2.orderId)

    val results = ordersByCustomerRepo(customer.customerId.value)

    assertEquals(2, results.size)
  }
}
