package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customer_orders.*
import testdb.customer_summary.*
import testdb.orders_by_customer.*
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/db2/.
 */
class SqlScriptTest {
    private val testInsert = TestInsert(Random(313927486))
    private val customerOrdersRepo = CustomerOrdersSqlRepoImpl()
    private val customerSummaryRepo = CustomerSummarySqlRepoImpl()
    private val ordersByCustomerRepo = OrdersByCustomerSqlRepoImpl()

    @Test
    fun testCustomerOrders() {
        Db2TestHelper.run { c ->
            val customer = testInsert.Customers(name = "Order Test", email = "order@test.com", c = c)
            testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("150.00"), c = c)

            val results = customerOrdersRepo.apply(customer.customerId.value, c)

            assertTrue(results.size >= 1)
            assertEquals(customer.customerId.value, results[0].customerId)
        }
    }

    @Test
    fun testCustomerOrdersMultiple() {
        Db2TestHelper.run { c ->
            val customer = testInsert.Customers(name = "Multi Order", email = "multi@test.com", c = c)
            testInsert.Orders(customerId = customer.customerId, c = c)
            testInsert.Orders(customerId = customer.customerId, c = c)
            testInsert.Orders(customerId = customer.customerId, c = c)

            val results = customerOrdersRepo.apply(customer.customerId.value, c)

            assertEquals(3, results.size)
        }
    }

    @Test
    fun testCustomerSummary() {
        Db2TestHelper.run { c ->
            val customer = testInsert.Customers(name = "Summary Test", email = "summary@test.com", c = c)
            testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("100.00"), c = c)
            testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("200.00"), c = c)

            val results = customerSummaryRepo.apply(c)

            assertTrue(results.size >= 1)
            val summary = results.find { it.customerId == customer.customerId.value }
            assertNotNull(summary)
            assertEquals(2, summary!!.orderCount ?: 0)
        }
    }

    @Test
    fun testCustomerSummaryNoOrders() {
        Db2TestHelper.run { c ->
            val customer = testInsert.Customers(name = "No Orders", email = "noorders@test.com", c = c)

            val results = customerSummaryRepo.apply(c)

            assertTrue(results.size >= 1)
            val summary = results.find { it.customerId == customer.customerId.value }
            assertNotNull(summary)
            assertEquals(0, summary!!.orderCount ?: 0)
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalSpent ?: BigDecimal.ZERO))
        }
    }

    @Test
    fun testOrdersByCustomer() {
        Db2TestHelper.run { c ->
            val customer = testInsert.Customers(name = "Orders By", email = "ordersby@test.com", c = c)
            val order1 = testInsert.Orders(customerId = customer.customerId, c = c)
            val order2 = testInsert.Orders(customerId = customer.customerId, c = c)
            // Create order items - SQL uses INNER JOIN with order_items
            testInsert.OrderItems(orderId = order1.orderId, productName = "Product 1", c = c)
            testInsert.OrderItems(orderId = order2.orderId, productName = "Product 2", c = c)

            val results = ordersByCustomerRepo.apply(customer.customerId.value, c)

            assertEquals(2, results.size)
        }
    }
}
