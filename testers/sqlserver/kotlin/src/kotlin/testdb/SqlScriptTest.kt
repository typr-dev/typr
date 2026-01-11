package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customer_orders_summary.*
import testdb.find_customers_by_email.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/sqlserver/.
 */
class SqlScriptTest {
    private val testInsert = TestInsert(Random(42))
    private val ordersSummaryRepo = CustomerOrdersSummarySqlRepoImpl()
    private val findByEmailRepo = FindCustomersByEmailSqlRepoImpl()

    @Test
    fun testCustomerOrdersSummaryNoFilters() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Kotlin Summary Test",
                email = Email("summary@test.com"),
                c = c
            )
            testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("150.00"),
                c = c
            )

            val results = ordersSummaryRepo.apply(
                customerNamePattern = null,
                minTotal = null,
                c = c
            )

            assertTrue(results.isNotEmpty())
            val customerSummary = results.find { it.customerId == customer.customerId }
            assertNotNull(customerSummary)
            assertEquals(1, customerSummary!!.orderCount?.value)
        }
    }

    @Test
    fun testCustomerOrdersSummaryWithNamePattern() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "KotlinPatternMatch Customer",
                email = Email("pattern@test.com"),
                c = c
            )
            testInsert.Orders(customerId = customer.customerId, c = c)

            val results = ordersSummaryRepo.apply(
                customerNamePattern = "KotlinPatternMatch%",
                minTotal = null,
                c = c
            )

            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.customerName == "KotlinPatternMatch Customer" })
        }
    }

    @Test
    fun testCustomerOrdersSummaryWithMinTotal() {
        SqlServerTestHelper.run { c ->
            val bigSpender = testInsert.Customers(
                name = "Kotlin Big Spender",
                email = Email("big@test.com"),
                c = c
            )
            testInsert.Orders(
                customerId = bigSpender.customerId,
                totalAmount = BigDecimal("1000.00"),
                c = c
            )

            val smallSpender = testInsert.Customers(
                name = "Kotlin Small Spender",
                email = Email("small@test.com"),
                c = c
            )
            testInsert.Orders(
                customerId = smallSpender.customerId,
                totalAmount = BigDecimal("50.00"),
                c = c
            )

            val results = ordersSummaryRepo.apply(
                customerNamePattern = null,
                minTotal = BigDecimal("500.00"),
                c = c
            )

            assertTrue(results.any { it.customerId == bigSpender.customerId })
            assertFalse(results.any { it.customerId == smallSpender.customerId })
        }
    }

    @Test
    fun testFindCustomersByEmail() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Email Test",
                email = Email("unique-kotlin-sqlserver@example.com"),
                c = c
            )

            val results = findByEmailRepo.apply(
                emailPattern = "%unique-kotlin-sqlserver%",
                c = c
            )

            assertEquals(1, results.size)
            assertEquals(customer.customerId, results[0].customerId)
        }
    }

    @Test
    fun testFindCustomersByEmailNoMatch() {
        SqlServerTestHelper.run { c ->
            testInsert.Customers(
                name = "No Match",
                email = Email("nomatch@test.com"),
                c = c
            )

            val results = findByEmailRepo.apply(
                emailPattern = "%nonexistent-kotlin-pattern%",
                c = c
            )

            assertEquals(0, results.size)
        }
    }

    @Test
    fun testOrderSummaryMultipleOrders() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Kotlin Multi Order Customer",
                email = Email("multi@test.com"),
                c = c
            )

            testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("100.00"),
                c = c
            )
            testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("200.00"),
                c = c
            )
            testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("300.00"),
                c = c
            )

            val results = ordersSummaryRepo.apply(
                customerNamePattern = "Kotlin Multi Order Customer",
                minTotal = null,
                c = c
            )

            assertEquals(1, results.size)
            val summary = results[0]
            assertEquals(3, summary.orderCount?.value)
            assertEquals(0, BigDecimal("600.00").compareTo(summary.totalSpent))
        }
    }
}
