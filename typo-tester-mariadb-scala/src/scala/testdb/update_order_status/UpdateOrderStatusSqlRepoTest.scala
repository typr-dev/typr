package testdb.update_order_status

import org.scalatest.funsuite.AnyFunSuite
import testdb.TestInsert
import testdb.orders.OrdersRepoImpl
import testdb.withConnection

import java.math.BigInteger
import java.util.Random

class UpdateOrderStatusSqlRepoTest extends AnyFunSuite {
  val repo = new UpdateOrderStatusSqlRepoImpl
  val ordersRepo = new OrdersRepoImpl

  test("update order status to confirmed") {
    withConnection {
      val testInsert = TestInsert(new Random(0))

      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))
      val order = testInsert.Orders(customerId = customer.customerId)

      assert(order.orderStatus == "pending"): @annotation.nowarn
      assert(order.confirmedAt.isEmpty): @annotation.nowarn

      val rowsAffected = repo("confirmed", order.orderId)
      assert(rowsAffected == 1): @annotation.nowarn

      val updatedOrder = ordersRepo.selectById(order.orderId)
      assert(updatedOrder.isPresent): @annotation.nowarn
      assert(updatedOrder.get().orderStatus == "confirmed"): @annotation.nowarn
      assert(updatedOrder.get().confirmedAt.isPresent)
    }
  }

  test("update order status to shipped") {
    withConnection {
      val testInsert = TestInsert(new Random(0))

      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))
      val order = testInsert.Orders(customerId = customer.customerId)

      val rowsAffected = repo("shipped", order.orderId)
      assert(rowsAffected == 1): @annotation.nowarn

      val updatedOrder = ordersRepo.selectById(order.orderId)
      assert(updatedOrder.isPresent): @annotation.nowarn
      assert(updatedOrder.get().orderStatus == "shipped"): @annotation.nowarn
      assert(updatedOrder.get().shippedAt.isPresent)
    }
  }

  test("update non-existent order returns 0") {
    withConnection {
      val testInsert = TestInsert(new Random(0))

      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))
      val order = testInsert.Orders(customerId = customer.customerId)

      val nonExistentId = testdb.orders.OrdersId(order.orderId.value.add(BigInteger.valueOf(9999)))
      val rowsAffected = repo("confirmed", nonExistentId)
      assert(rowsAffected == 0)
    }
  }
}
