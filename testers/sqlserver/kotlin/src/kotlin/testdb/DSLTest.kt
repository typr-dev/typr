package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for the DSL query builder functionality. Tests type-safe query building with where, orderBy,
 * limit, count, and projection.
 */
class DSLTest {
    private val testInsert = TestInsert(Random(42))
    private val customersRepo = CustomersRepoImpl()
    private val productsRepo = ProductsRepoImpl()
    private val ordersRepo = OrdersRepoImpl()

    @Test
    fun testSelectWithWhere() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Kotlin DSL Where Test",
                email = Email("dsl-where@test.com"),
                c = c
            )

            val results = customersRepo.select()
                .where { cust -> cust.name().isEqual("Kotlin DSL Where Test") }
                .toList(c)

            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.customerId == customer.customerId })
        }
    }

    @Test
    fun testSelectWithOrderBy() {
        SqlServerTestHelper.run { c ->
            testInsert.Customers(name = "KotlinZebra", email = Email("zebra@test.com"), c = c)
            testInsert.Customers(name = "KotlinAlpha", email = Email("alpha@test.com"), c = c)
            testInsert.Customers(name = "KotlinMike", email = Email("mike@test.com"), c = c)

            val results = customersRepo.select()
                .orderBy { cust -> cust.name().asc() }
                .limit(10)
                .toList(c)

            assertTrue(results.size >= 3)
            var firstName: String? = null
            for (result in results) {
                firstName?.let { prev -> assertTrue(result.name >= prev) }
                firstName = result.name
            }
        }
    }

    @Test
    fun testSelectWithLimit() {
        SqlServerTestHelper.run { c ->
            for (i in 0 until 10) {
                testInsert.Customers(name = "KotlinLimitTest$i", email = Email("limit$i@test.com"), c = c)
            }

            val results = customersRepo.select()
                .where { cust -> cust.name().like("KotlinLimitTest%") }
                .limit(5)
                .toList(c)

            assertEquals(5, results.size)
        }
    }

    @Test
    fun testSelectWithCount() {
        SqlServerTestHelper.run { c ->
            testInsert.Customers(name = "KotlinCountA", email = Email("counta@test.com"), c = c)
            testInsert.Customers(name = "KotlinCountB", email = Email("countb@test.com"), c = c)
            testInsert.Customers(name = "KotlinCountC", email = Email("countc@test.com"), c = c)

            val count = customersRepo.select()
                .where { cust -> cust.name().like("KotlinCount%") }
                .count(c)

            assertEquals(3, count)
        }
    }

    @Test
    fun testSelectWithIn() {
        SqlServerTestHelper.run { c ->
            val c1 = testInsert.Customers(name = "KotlinInTest1", email = Email("in1@test.com"), c = c)
            testInsert.Customers(name = "KotlinInTest2", email = Email("in2@test.com"), c = c)
            val c3 = testInsert.Customers(name = "KotlinInTest3", email = Email("in3@test.com"), c = c)

            val results = customersRepo.select()
                .where { cust -> cust.customerId().`in`(c1.customerId, c3.customerId) }
                .toList(c)

            assertEquals(2, results.size)
        }
    }

    @Test
    fun testSelectWithProjection() {
        SqlServerTestHelper.run { c ->
            testInsert.Customers(
                name = "KotlinProjectionTest",
                email = Email("kotlin-projection@test.com"),
                c = c
            )

            val results = customersRepo.select()
                .where { cust -> cust.name().isEqual("KotlinProjectionTest") }
                .map { cust -> cust.name().tupleWith(cust.email()) }
                .toList(c)

            assertEquals(1, results.size)
            assertEquals("KotlinProjectionTest", results[0]._1())
            assertEquals(Email("kotlin-projection@test.com"), results[0]._2())
        }
    }

    @Test
    fun testProductDSLQuery() {
        SqlServerTestHelper.run { c ->
            testInsert.Products(name = "Kotlin Expensive Product", price = BigDecimal("999.99"), c = c)
            testInsert.Products(name = "Kotlin Cheap Product", price = BigDecimal("9.99"), c = c)

            val expensiveProducts = productsRepo.select()
                .where { p -> p.price().greaterThan(BigDecimal("100.00")) }
                .toList(c)

            assertTrue(expensiveProducts.isNotEmpty())
            assertTrue(expensiveProducts.all { it.price > BigDecimal("100.00") })
        }
    }

    @Test
    fun testOrdersDSLQuery() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Orders DSL Customer",
                email = Email("orders-dsl@test.com"),
                c = c
            )

            testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("500.00"), c = c)
            testInsert.Orders(customerId = customer.customerId, totalAmount = BigDecimal("1500.00"), c = c)

            val largeOrders = ordersRepo.select()
                .where { o -> o.totalAmount().greaterThan(BigDecimal("1000.00")) }
                .toList(c)

            assertTrue(largeOrders.isNotEmpty())
            assertTrue(largeOrders.all { it.totalAmount > BigDecimal("1000.00") })
        }
    }

    @Test
    fun testComplexWhereClause() {
        SqlServerTestHelper.run { c ->
            testInsert.Customers(name = "Kotlin Complex A", email = Email("kotlin-complex-a@test.com"), c = c)
            testInsert.Customers(name = "Kotlin Complex B", email = Email("kotlin-complex-b@test.com"), c = c)
            testInsert.Customers(name = "Kotlin Other", email = Email("kotlin-other@test.com"), c = c)

            val results = customersRepo.select()
                .where { cust ->
                    cust.name().like("Kotlin Complex%").and(cust.email().like("%@test.com", Email.bijection))
                }
                .toList(c)

            assertEquals(2, results.size)
        }
    }
}
