package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.userdefined.Email
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for the TestInsert helper class that generates random test data.
 */
class TestInsertTest {
    private val testInsert = TestInsert(Random(42))

    @Test
    fun testCustomersInsert() {
        SqlServerTestHelper.run { c ->
            val row = testInsert.Customers(
                name = "Test Customer",
                email = Email("test@example.com"),
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.customerId)
            assertEquals("Test Customer", row.name)
            assertEquals(Email("test@example.com"), row.email)
        }
    }

    @Test
    fun testProductsInsert() {
        SqlServerTestHelper.run { c ->
            val row = testInsert.Products(
                name = "Test Product",
                price = BigDecimal("99.99"),
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.productId)
            assertEquals("Test Product", row.name)
            assertEquals(0, BigDecimal("99.99").compareTo(row.price))
        }
    }

    @Test
    fun testAllScalarTypesInsert() {
        SqlServerTestHelper.run { c ->
            val row = testInsert.AllScalarTypes(c = c)

            assertNotNull(row)
            assertNotNull(row.id)
            assertNotNull(row.colRowversion)
        }
    }

    @Test
    fun testOrdersWithCustomerFK() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "FK Customer",
                email = Email("fk@example.com"),
                c = c
            )

            val order = testInsert.Orders(
                customerId = customer.customerId,
                c = c
            )

            assertNotNull(order)
            assertEquals(customer.customerId, order.customerId)
        }
    }

    @Test
    fun testOrderItemsWithFKs() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "OrderItem Customer",
                email = Email("orderitem@example.com"),
                c = c
            )
            val product = testInsert.Products(
                name = "OrderItem Product",
                price = BigDecimal("50.00"),
                c = c
            )
            val order = testInsert.Orders(
                customerId = customer.customerId,
                c = c
            )

            val orderItem = testInsert.OrderItems(
                orderId = order.orderId,
                productId = product.productId,
                c = c
            )

            assertNotNull(orderItem)
            assertEquals(order.orderId, orderItem.orderId)
            assertEquals(product.productId, orderItem.productId)
        }
    }

    @Test
    fun testMultipleInserts() {
        SqlServerTestHelper.run { c ->
            val row1 = testInsert.Customers(name = "Customer1", email = Email("c1@test.com"), c = c)
            val row2 = testInsert.Customers(name = "Customer2", email = Email("c2@test.com"), c = c)
            val row3 = testInsert.Customers(name = "Customer3", email = Email("c3@test.com"), c = c)

            assertNotEquals(row1.customerId, row2.customerId)
            assertNotEquals(row2.customerId, row3.customerId)
            assertNotEquals(row1.customerId, row3.customerId)
        }
    }

    @Test
    fun testInsertWithDifferentValues() {
        SqlServerTestHelper.run { c ->
            val row1 = testInsert.Customers(name = "Alpha", email = Email("alpha@test.com"), c = c)
            val row2 = testInsert.Customers(name = "Beta", email = Email("beta@test.com"), c = c)

            assertEquals("Alpha", row1.name)
            assertEquals("Beta", row2.name)
            assertEquals(Email("alpha@test.com"), row1.email)
            assertEquals(Email("beta@test.com"), row2.email)
        }
    }
}
