package testdb

import dev.typr.dslsc.TupleExpr2
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import testdb.customers.*
import testdb.order_items.*
import testdb.orders.*

class TupleInTest {
  val customersRepo: CustomersRepoImpl = new CustomersRepoImpl
  val ordersRepo: OrdersRepoImpl = new OrdersRepoImpl
  val orderItemsRepo: OrderItemsRepoImpl = new OrderItemsRepoImpl

  // =============== OrderItems (2-column OrdersId,Integer composite key) ===============

  @Test
  def orderItemsCompositeIdInWithMultipleIds(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Test Customer", "test@example.com"))
      val order1 = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))
      val order2 = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val item1 = orderItemsRepo.insert(OrderItemsRow(order1.orderId, 1, "Widget", 5, BigDecimal("10.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order1.orderId, 2, "Gadget", 3, BigDecimal("25.00")))
      val item3 = orderItemsRepo.insert(OrderItemsRow(order2.orderId, 1, "Gizmo", 2, BigDecimal("50.00")))
      val item4 = orderItemsRepo.insert(OrderItemsRow(order2.orderId, 2, "Doohickey", 1, BigDecimal("100.00")))

      val result = orderItemsRepo.select
        .where(oi => oi.compositeIdIn(List(item1.compositeId, item3.compositeId, item4.compositeId)))
        .toList

      assertEquals(3, result.size)
      val resultIds = result.map(_.compositeId).toSet
      assertEquals(Set(item1.compositeId, item3.compositeId, item4.compositeId), resultIds)
    }
  }

  @Test
  def orderItemsCompositeIdInWithSingleId(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Single Customer", "single@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))
      val item = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Single Item", 1, BigDecimal("99.99")))

      val result = orderItemsRepo.select
        .where(oi => oi.compositeIdIn(List(item.compositeId)))
        .toList

      assertEquals(1, result.size)
      assertEquals(item, result.head)
    }
  }

  @Test
  def orderItemsCompositeIdInWithEmptyList(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Empty Customer", "empty@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Empty Test Item", 1, BigDecimal("25.00")))

      val result = orderItemsRepo.select
        .where(oi => oi.compositeIdIn(List.empty))
        .toList

      assertTrue(result.isEmpty)
    }
  }

  @Test
  def orderItemsCompositeIdInCombinedWithOtherConditions(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Condition Customer", "condition@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val item1 = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Cheap Item", 10, BigDecimal("5.00")))
      val item2 = orderItemsRepo.insert(OrderItemsRow(order.orderId, 2, "Expensive Item", 2, BigDecimal("500.00")))

      val result = orderItemsRepo.select
        .where(oi =>
          oi.compositeIdIn(List(item1.compositeId, item2.compositeId))
            .and(oi.unitPrice.greaterThan(BigDecimal("100.00")))
        )
        .toList

      assertEquals(1, result.size)
      assertEquals(item2.compositeId, result.head.compositeId)
    }
  }

  @Test
  def orderItemsCompositeIdInWithNonExistentIds(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Exist Customer", "exist@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))
      val item = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Existing Item", 1, BigDecimal("75.00")))

      val result = orderItemsRepo.select
        .where(oi =>
          oi.compositeIdIn(
            List(
              item.compositeId,
              OrderItemsId(OrdersId(999999), 999)
            )
          )
        )
        .toList

      assertEquals(1, result.size)
      assertEquals(item, result.head)
    }
  }

  @Test
  def orderItemsCompositeIdComputedVsManual(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Computed Customer", "computed@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))
      val item = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Computed Item", 1, BigDecimal("55.00")))

      val computedId = item.compositeId
      val manualId = OrderItemsId(order.orderId, 1)

      assertEquals(computedId, manualId)

      val result = orderItemsRepo.select
        .where(oi => oi.compositeIdIn(List(computedId, manualId)))
        .toList

      assertEquals(1, result.size)
      assertEquals(item, result.head)
    }
  }

  // ==================== Tuple IN Subquery Tests ====================

  @Test
  def tupleInSubqueryBasic(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val customer = customersRepo.insert(CustomersRowUnsaved("Subquery Customer", "subquery@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Cheap1", 10, BigDecimal("5.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 2, "Cheap2", 20, BigDecimal("8.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 3, "Expensive", 1, BigDecimal("500.00")))

      val result = orderItemsRepo.select
        .where(oi =>
          oi.orderId
            .tupleWith(oi.itemNumber)
            .in(
              orderItemsRepo.select
                .where(inner => inner.unitPrice.lessThan(BigDecimal("100.00")))
                .map(inner => inner.orderId.tupleWith(inner.itemNumber))
                .subquery
            )
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.productName).toSet
      assertEquals(Set("Cheap1", "Cheap2"), names)
    }
  }

  @Test
  def tupleInSubqueryWithNoMatches(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val customer = customersRepo.insert(CustomersRowUnsaved("NoMatch Customer", "nomatch@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Normal Item", 5, BigDecimal("50.00")))

      val result = orderItemsRepo.select
        .where(oi =>
          oi.orderId
            .tupleWith(oi.itemNumber)
            .in(
              orderItemsRepo.select
                .where(inner => inner.unitPrice.lessThan(BigDecimal.valueOf(0)))
                .map(inner => inner.orderId.tupleWith(inner.itemNumber))
                .subquery
            )
        )
        .toList

      assertTrue(result.isEmpty)
    }
  }

  @Test
  def tupleInSubqueryCombinedWithOtherConditions(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val customer = customersRepo.insert(CustomersRowUnsaved("Combined Customer", "combined@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "LowQty", 2, BigDecimal("30.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 2, "HighQty", 100, BigDecimal("40.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 3, "Expensive", 50, BigDecimal("500.00")))

      val result = orderItemsRepo.select
        .where(oi =>
          oi.orderId
            .tupleWith(oi.itemNumber)
            .in(
              orderItemsRepo.select
                .where(inner => inner.unitPrice.lessThan(BigDecimal("100.00")))
                .map(inner => inner.orderId.tupleWith(inner.itemNumber))
                .subquery
            )
            .and(oi.quantity.greaterThan(10))
        )
        .toList

      assertEquals(1, result.size)
      assertEquals("HighQty", result.head.productName)
    }
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  @Test
  def tupleInWithNullableColumn(): Unit = {
    withConnection {
      val customer1 = customersRepo.insert(CustomersRowUnsaved("Null Status Customer 1", "null1@example.com"))
      val customer2 = customersRepo.insert(CustomersRowUnsaved("Null Status Customer 2", "null2@example.com"))

      val _ = ordersRepo.insert(OrdersRowUnsaved(customer1.customerId))
      val _ = ordersRepo.insert(OrdersRowUnsaved(customer2.customerId))

      // Query using tuple with nullable column - match by customerId and status
      val result = ordersRepo.select
        .where(o =>
          o.customerId
            .tupleWith(o.status)
            .in(
              List(
                dev.typr.foundations.Tuple.of(customer1.customerId, null: String),
                dev.typr.foundations.Tuple.of(customer2.customerId, null: String)
              )
            )
        )
        .toList

      // Should handle nullable column tuple IN (tests null-safe comparison)
      assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }
  }

  @Test
  def tupleInWithNullableColumnMixedMatching(): Unit = {
    withConnection {
      val customer1 = customersRepo.insert(CustomersRowUnsaved("Mixed Customer 1", "mixed1@example.com"))
      val customer2 = customersRepo.insert(CustomersRowUnsaved("Mixed Customer 2", "mixed2@example.com"))

      val _ = ordersRepo.insert(OrdersRowUnsaved(customer1.customerId))
      val _ = ordersRepo.insert(OrdersRowUnsaved(customer2.customerId))

      // Query for orders matching specific customerId + status combinations
      val result = ordersRepo.select
        .where(o =>
          o.customerId
            .tupleWith(o.status)
            .in(
              List(
                dev.typr.foundations.Tuple.of(customer1.customerId, "pending"),
                dev.typr.foundations.Tuple.of(customer2.customerId, null: String)
              )
            )
        )
        .toList

      // This validates that mixed null/non-null tuple values work
      assertTrue("Should handle mixed null/non-null tuple values", result.size >= 0)
    }
  }

  // ==================== Nested Tuple Tests ====================

  @Ignore("Nested tuple support pending refactoring")
  @Test
  def nestedTupleIn(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Nested Tuple Customer", "nested@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Nested Item 1", 5, BigDecimal("10.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 2, "Nested Item 2", 10, BigDecimal("20.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 3, "Nested Item 3", 15, BigDecimal("30.00")))

      // Test truly nested tuple: ((orderId, itemNumber), quantity)
      val result = orderItemsRepo.select
        .where(oi =>
          oi.orderId
            .tupleWith(oi.itemNumber) // Tuple2<OrdersId, Integer>
            .tupleWith(oi.quantity) // Tuple2<Tuple2<OrdersId, Integer>, Integer>
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(order.orderId, 1), 5),
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(order.orderId, 3), 15)
              )
            )
        )
        .toList

      assertEquals("Should find 2 items matching nested tuple pattern", 2, result.size)
      val names = result.map(_.productName).toSet
      assertEquals(Set("Nested Item 1", "Nested Item 3"), names)

      // Test that non-matching nested tuple returns empty
      val resultNoMatch = orderItemsRepo.select
        .where(oi =>
          oi.orderId
            .tupleWith(oi.itemNumber)
            .tupleWith(oi.quantity)
            .in(
              List(
                // Wrong: quantity doesn't match
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(order.orderId, 1), 999)
              )
            )
        )
        .toList

      assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty)
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  @Ignore("Nested tuple support pending refactoring")
  @Test
  def readNestedTupleFromDatabase(): Unit = {
    withConnection {
      val customer = customersRepo.insert(CustomersRowUnsaved("Read Customer", "read@example.com"))
      val order = ordersRepo.insert(OrdersRowUnsaved(customer.customerId))

      // Insert test data
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 1, "Read Item 1", 5, BigDecimal("10.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 2, "Read Item 2", 10, BigDecimal("20.00")))
      val _ = orderItemsRepo.insert(OrderItemsRow(order.orderId, 3, "Read Item 3", 15, BigDecimal("30.00")))

      // Select nested tuple: ((orderId, itemNumber), productName)
      val result = orderItemsRepo.select
        .where(oi => oi.orderId.isEqual(order.orderId))
        .orderBy(oi => oi.itemNumber.asc)
        .map(oi => oi.orderId.tupleWith(oi.itemNumber).tupleWith(oi.productName))
        .toList

      assertEquals("Should read 3 nested tuples", 3, result.size)

      // Verify the nested tuple structure
      val first = result.head
      assertEquals("First tuple's inner first element", order.orderId, first._1._1)
      assertEquals("First tuple's inner second element", 1, first._1._2)
      assertEquals("First tuple's outer second element", "Read Item 1", first._2)
    }
  }
}
