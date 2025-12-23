package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customtypes.Defaulted
import testdb.orders.OrdersId
import testdb.orders.OrdersRepoImpl
import testdb.update_order_status.UpdateOrderStatusSqlRepoImpl
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Random

class UpdateOrderStatusSqlRepoTest {
    private val repo = UpdateOrderStatusSqlRepoImpl()
    private val ordersRepo = OrdersRepoImpl()

    @Test
    fun updateOrderStatusToConfirmed() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val customer = testInsert.Customers(
                email = "test@example.com",
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = "John",
                lastName = "Doe",
                phone = Defaulted.UseDefault(),
                status = Defaulted.UseDefault(),
                tier = Defaulted.UseDefault(),
                preferences = Defaulted.UseDefault(),
                marketingFlags = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                createdAt = Defaulted.UseDefault(),
                updatedAt = Defaulted.UseDefault(),
                lastLoginAt = Defaulted.UseDefault(),
                c = c
            )
            val order = testInsert.Orders(
                orderNumber = "ORD-${testInsert.random.nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = BigDecimal("100.00"),
                totalAmount = BigDecimal("110.00"),
                orderStatus = Defaulted.UseDefault(),
                paymentStatus = Defaulted.UseDefault(),
                shippingAddressId = Defaulted.UseDefault(),
                billingAddressId = Defaulted.UseDefault(),
                shippingCost = Defaulted.UseDefault(),
                taxAmount = Defaulted.UseDefault(),
                discountAmount = Defaulted.UseDefault(),
                currencyCode = Defaulted.UseDefault(),
                promotionId = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                internalNotes = Defaulted.UseDefault(),
                ipAddress = Defaulted.UseDefault(),
                userAgent = Defaulted.UseDefault(),
                orderedAt = Defaulted.UseDefault(),
                confirmedAt = Defaulted.UseDefault(),
                shippedAt = Defaulted.UseDefault(),
                deliveredAt = Defaulted.UseDefault(),
                c = c
            )

            assertEquals("pending", order.orderStatus)
            assertNull(order.confirmedAt)

            val rowsAffected = repo.apply("confirmed", order.orderId, c)
            assertEquals(1, rowsAffected)

            val updatedOrder = ordersRepo.selectById(order.orderId, c)
            assertNotNull(updatedOrder)
            assertEquals("confirmed", updatedOrder!!.orderStatus)
            assertNotNull(updatedOrder.confirmedAt)
        }
    }

    @Test
    fun updateOrderStatusToShipped() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val customer = testInsert.Customers(
                email = "test2@example.com",
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = "Jane",
                lastName = "Doe",
                phone = Defaulted.UseDefault(),
                status = Defaulted.UseDefault(),
                tier = Defaulted.UseDefault(),
                preferences = Defaulted.UseDefault(),
                marketingFlags = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                createdAt = Defaulted.UseDefault(),
                updatedAt = Defaulted.UseDefault(),
                lastLoginAt = Defaulted.UseDefault(),
                c = c
            )
            val order = testInsert.Orders(
                orderNumber = "ORD-${testInsert.random.nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = BigDecimal("100.00"),
                totalAmount = BigDecimal("110.00"),
                orderStatus = Defaulted.UseDefault(),
                paymentStatus = Defaulted.UseDefault(),
                shippingAddressId = Defaulted.UseDefault(),
                billingAddressId = Defaulted.UseDefault(),
                shippingCost = Defaulted.UseDefault(),
                taxAmount = Defaulted.UseDefault(),
                discountAmount = Defaulted.UseDefault(),
                currencyCode = Defaulted.UseDefault(),
                promotionId = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                internalNotes = Defaulted.UseDefault(),
                ipAddress = Defaulted.UseDefault(),
                userAgent = Defaulted.UseDefault(),
                orderedAt = Defaulted.UseDefault(),
                confirmedAt = Defaulted.UseDefault(),
                shippedAt = Defaulted.UseDefault(),
                deliveredAt = Defaulted.UseDefault(),
                c = c
            )

            val rowsAffected = repo.apply("shipped", order.orderId, c)
            assertEquals(1, rowsAffected)

            val updatedOrder = ordersRepo.selectById(order.orderId, c)
            assertNotNull(updatedOrder)
            assertEquals("shipped", updatedOrder!!.orderStatus)
            assertNotNull(updatedOrder.shippedAt)
        }
    }

    @Test
    fun updateNonExistentOrderReturnsZero() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val customer = testInsert.Customers(
                email = "test3@example.com",
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = "Bob",
                lastName = "Smith",
                phone = Defaulted.UseDefault(),
                status = Defaulted.UseDefault(),
                tier = Defaulted.UseDefault(),
                preferences = Defaulted.UseDefault(),
                marketingFlags = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                createdAt = Defaulted.UseDefault(),
                updatedAt = Defaulted.UseDefault(),
                lastLoginAt = Defaulted.UseDefault(),
                c = c
            )
            val order = testInsert.Orders(
                orderNumber = "ORD-${testInsert.random.nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = BigDecimal("100.00"),
                totalAmount = BigDecimal("110.00"),
                orderStatus = Defaulted.UseDefault(),
                paymentStatus = Defaulted.UseDefault(),
                shippingAddressId = Defaulted.UseDefault(),
                billingAddressId = Defaulted.UseDefault(),
                shippingCost = Defaulted.UseDefault(),
                taxAmount = Defaulted.UseDefault(),
                discountAmount = Defaulted.UseDefault(),
                currencyCode = Defaulted.UseDefault(),
                promotionId = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                internalNotes = Defaulted.UseDefault(),
                ipAddress = Defaulted.UseDefault(),
                userAgent = Defaulted.UseDefault(),
                orderedAt = Defaulted.UseDefault(),
                confirmedAt = Defaulted.UseDefault(),
                shippedAt = Defaulted.UseDefault(),
                deliveredAt = Defaulted.UseDefault(),
                c = c
            )

            val nonExistentId = OrdersId(order.orderId.value.add(BigInteger.valueOf(9999)))
            val rowsAffected = repo.apply("confirmed", nonExistentId, c)
            assertEquals(0, rowsAffected)
        }
    }
}
