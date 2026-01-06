package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.order_items.*
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for foreign key relationships between tables. Tests orders -> customers and order_items ->
 * orders/products relationships.
 */
class ForeignKeyTest {
    private val testInsert = TestInsert(Random(42))
    private val customersRepo = CustomersRepoImpl()
    private val productsRepo = ProductsRepoImpl()
    private val ordersRepo = OrdersRepoImpl()
    private val orderItemsRepo = OrderItemsRepoImpl()

    @Test
    fun testOrderReferencesCustomer() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "FK Test Customer",
                email = Email("fktest@example.com"),
                c = c
            )
            val order = testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("99.99"),
                c = c
            )

            assertNotNull(order)
            assertEquals(customer.customerId, order.customerId)

            val foundCustomer = customersRepo.selectById(order.customerId, c)
            assertNotNull(foundCustomer)
            assertEquals(customer.name, foundCustomer!!.name)
        }
    }

    @Test
    fun testOrderItemsReferencesOrderAndProduct() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "OrderItems Test Customer",
                email = Email("orderitems@example.com"),
                c = c
            )
            val product = testInsert.Products(name = "Test Product", c = c)
            val order = testInsert.Orders(customerId = customer.customerId, c = c)

            val orderItem = testInsert.OrderItems(
                orderId = order.orderId,
                productId = product.productId,
                quantity = 5,
                price = BigDecimal("19.99"),
                c = c
            )

            assertNotNull(orderItem)
            assertEquals(order.orderId, orderItem.orderId)
            assertEquals(product.productId, orderItem.productId)

            val foundOrder = ordersRepo.selectById(orderItem.orderId, c)
            assertNotNull(foundOrder)

            val foundProduct = productsRepo.selectById(orderItem.productId, c)
            assertNotNull(foundProduct)
        }
    }

    @Test
    fun testMultipleOrdersForCustomer() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Multiple Orders Customer",
                email = Email("multiorder@example.com"),
                c = c
            )

            val order1 = testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("100.00"),
                c = c
            )
            val order2 = testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("200.00"),
                c = c
            )
            val order3 = testInsert.Orders(
                customerId = customer.customerId,
                totalAmount = BigDecimal("300.00"),
                c = c
            )

            assertEquals(customer.customerId, order1.customerId)
            assertEquals(customer.customerId, order2.customerId)
            assertEquals(customer.customerId, order3.customerId)

            assertNotEquals(order1.orderId, order2.orderId)
            assertNotEquals(order2.orderId, order3.orderId)
        }
    }

    @Test
    fun testMultipleItemsForOrder() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Multiple Items Customer",
                email = Email("multiitems@example.com"),
                c = c
            )
            val product1 = testInsert.Products(name = "Product 1", c = c)
            val product2 = testInsert.Products(name = "Product 2", c = c)
            val order = testInsert.Orders(customerId = customer.customerId, c = c)

            val item1 = testInsert.OrderItems(
                orderId = order.orderId,
                productId = product1.productId,
                quantity = 2,
                c = c
            )
            val item2 = testInsert.OrderItems(
                orderId = order.orderId,
                productId = product2.productId,
                quantity = 3,
                c = c
            )

            assertEquals(order.orderId, item1.orderId)
            assertEquals(order.orderId, item2.orderId)
            assertEquals(product1.productId, item1.productId)
            assertEquals(product2.productId, item2.productId)
        }
    }

    @Test
    fun testCascadingForeignKeys() {
        SqlServerTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Cascade Test Customer",
                email = Email("cascade@example.com"),
                c = c
            )
            val product = testInsert.Products(name = "Cascade Product", c = c)
            val order = testInsert.Orders(customerId = customer.customerId, c = c)
            val orderItem = testInsert.OrderItems(
                orderId = order.orderId,
                productId = product.productId,
                c = c
            )

            val foundItem = orderItemsRepo.selectById(orderItem.orderItemId, c)
            assertNotNull(foundItem)

            val foundOrder = ordersRepo.selectById(foundItem!!.orderId, c)
            assertNotNull(foundOrder)

            val foundCustomer = customersRepo.selectById(foundOrder!!.customerId, c)
            assertNotNull(foundCustomer)

            assertEquals(customer.customerId, foundCustomer!!.customerId)
        }
    }
}
