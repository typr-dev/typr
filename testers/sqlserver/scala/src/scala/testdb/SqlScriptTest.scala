package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.customer_orders_summary._
import testdb.find_customers_by_email._
import testdb.userdefined.Email

import scala.util.Random

/** Tests for SQL script generated repositories. These tests exercise the typed query classes generated from SQL files in sql-scripts/sqlserver/.
  */
class SqlScriptTest {
  private val testInsert = TestInsert(Random(42))
  private val ordersSummaryRepo = CustomerOrdersSummarySqlRepoImpl()
  private val findByEmailRepo = FindCustomersByEmailSqlRepoImpl()

  @Test
  def testCustomerOrdersSummaryNoFilters(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(name = "Summary Test")
    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("150.00"))

    val results = ordersSummaryRepo(None, None)

    assertTrue(results.nonEmpty)
    val customerSummary = results.find(_.customerId == customer.customerId)
    assertTrue(customerSummary.isDefined)
    assertEquals(1, customerSummary.get.orderCount.map(_.value).getOrElse(0))
  }

  @Test
  def testCustomerOrdersSummaryWithNamePattern(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(name = "PatternMatch Customer")
    val _ = testInsert.Orders(customer.customerId)

    val results = ordersSummaryRepo(Some("PatternMatch%"), None)

    assertTrue(results.nonEmpty)
    assertTrue(results.exists(_.customerName == "PatternMatch Customer"))
  }

  @Test
  def testCustomerOrdersSummaryWithMinTotal(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val bigSpender = testInsert.Customers(name = "Big Spender")
    val _ = testInsert.Orders(bigSpender.customerId, totalAmount = BigDecimal("1000.00"))

    val smallSpender = testInsert.Customers(name = "Small Spender")
    val _ = testInsert.Orders(smallSpender.customerId, totalAmount = BigDecimal("50.00"))

    val results = ordersSummaryRepo(None, Some(BigDecimal("500.00")))

    assertTrue(results.exists(_.customerId == bigSpender.customerId))
    assertFalse(results.exists(_.customerId == smallSpender.customerId))
  }

  @Test
  def testFindCustomersByEmail(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Email("unique-sqlserver-test@example.com"))

    val results = findByEmailRepo("%unique-sqlserver-test%")

    assertEquals(1, results.size)
    assertEquals(customer.customerId, results.head.customerId)
  }

  @Test
  def testFindCustomersByEmailNoMatch(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers()

    val results = findByEmailRepo("%nonexistent-email-pattern%")

    assertEquals(0, results.size)
  }

  @Test
  def testOrderSummaryMultipleOrders(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(name = "Multi Order Customer")

    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("100.00"))
    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("200.00"))
    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("300.00"))

    val results = ordersSummaryRepo(Some("Multi Order Customer"), None)

    assertEquals(1, results.size)
    val summary = results.head
    assertEquals(3, summary.orderCount.map(_.value).getOrElse(0))
    assertEquals(0, BigDecimal("600.00").compareTo(summary.totalSpent.getOrElse(BigDecimal(0))))
  }
}
