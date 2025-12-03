package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.orders.OrdersId
import testdb.orders.OrdersRepoImpl
import testdb.update_order_status.UpdateOrderStatusSqlRepoImpl
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
                passwordHash = byteArrayOf(1, 2, 3),
                c = c
            )
            val order = testInsert.Orders(
                customerId = customer.customerId,
                c = c
            )

            assertEquals("pending", order.orderStatus)
            assertFalse(order.confirmedAt.isPresent)

            val rowsAffected = repo.apply("confirmed", order.orderId, c)
            assertEquals(1, rowsAffected)

            val updatedOrder = ordersRepo.selectById(order.orderId, c)
            assertTrue(updatedOrder.isPresent)
            assertEquals("confirmed", updatedOrder.get().orderStatus)
            assertTrue(updatedOrder.get().confirmedAt.isPresent)
        }
    }

    @Test
    fun updateOrderStatusToShipped() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val customer = testInsert.Customers(
                passwordHash = byteArrayOf(1, 2, 3),
                c = c
            )
            val order = testInsert.Orders(
                customerId = customer.customerId,
                c = c
            )

            val rowsAffected = repo.apply("shipped", order.orderId, c)
            assertEquals(1, rowsAffected)

            val updatedOrder = ordersRepo.selectById(order.orderId, c)
            assertTrue(updatedOrder.isPresent)
            assertEquals("shipped", updatedOrder.get().orderStatus)
            assertTrue(updatedOrder.get().shippedAt.isPresent)
        }
    }

    @Test
    fun updateNonExistentOrderReturnsZero() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val customer = testInsert.Customers(
                passwordHash = byteArrayOf(1, 2, 3),
                c = c
            )
            val order = testInsert.Orders(
                customerId = customer.customerId,
                c = c
            )

            val nonExistentId = OrdersId(order.orderId.value.add(BigInteger.valueOf(9999)))
            val rowsAffected = repo.apply("confirmed", nonExistentId, c)
            assertEquals(0, rowsAffected)
        }
    }
}
