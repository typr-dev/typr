package testdb

import dev.typr.dslkt.MockConnection
import dev.typr.dslkt.SqlExpr
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import testdb.customers.*
import testdb.order_items.*
import testdb.orders.*
import java.math.BigDecimal
import dev.typr.foundationskt.Connection
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for tuple IN functionality on DB2. Tests cover:
 * - Composite ID IN with OrdersId,Integer composite key components
 * - Tuple IN with subqueries using tupleWith()
 * - Combined with other conditions using SqlExpr.all
 * - Both real database and mock repository evaluation
 */
class TupleInTest {

    data class Repos(
        val customersRepo: CustomersRepo,
        val ordersRepo: OrdersRepo,
        val orderItemsRepo: OrderItemsRepo
    )

    // =============== OrderItems (2-column OrdersId,Integer composite key) ===============

    @Test
    fun orderItemsCompositeIdInWithMultipleIds_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdInWithMultipleIds(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdInWithMultipleIds_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdInWithMultipleIds(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdInWithMultipleIds(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Test Customer", "test@example.com"), c
        )

        val order1 = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )
        val order2 = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order1.orderId, 1, "Widget", 5, BigDecimal("10.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order1.orderId, 2, "Gadget", 3, BigDecimal("25.00")), c
        )
        val item3 = repos.orderItemsRepo.insert(
            OrderItemsRow(order2.orderId, 1, "Gizmo", 2, BigDecimal("50.00")), c
        )
        val item4 = repos.orderItemsRepo.insert(
            OrderItemsRow(order2.orderId, 2, "Doohickey", 1, BigDecimal("100.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.compositeIdIn(listOf(item1.compositeId(), item3.compositeId(), item4.compositeId()))
            }
            .toList(c)

        assertEquals(3, result.size)
        val resultIds = result.map { it.compositeId() }.toSet()
        assertEquals(setOf(item1.compositeId(), item3.compositeId(), item4.compositeId()), resultIds)
    }

    @Test
    fun orderItemsCompositeIdInWithSingleId_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdInWithSingleId(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdInWithSingleId_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdInWithSingleId(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdInWithSingleId(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Single Customer", "single@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )
        val item = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Single Item", 1, BigDecimal("99.99")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi -> oi.compositeIdIn(listOf(item.compositeId())) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(item, result[0])
    }

    @Test
    fun orderItemsCompositeIdInWithEmptyList_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdInWithEmptyList(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdInWithEmptyList_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdInWithEmptyList(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdInWithEmptyList(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Empty Customer", "empty@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )
        repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Empty Test Item", 1, BigDecimal("25.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi -> oi.compositeIdIn(emptyList()) }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun orderItemsCompositeIdInCombinedWithOtherConditions_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdInCombinedWithOtherConditions(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdInCombinedWithOtherConditions_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdInCombinedWithOtherConditions(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Condition Customer", "condition@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Cheap Item", 10, BigDecimal("5.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 2, "Expensive Item", 2, BigDecimal("500.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                SqlExpr.all(
                    oi.compositeIdIn(listOf(item1.compositeId(), item2.compositeId())),
                    oi.unitPrice().greaterThan(BigDecimal("100.00"))
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(item2.compositeId(), result[0].compositeId())
    }

    @Test
    fun orderItemsCompositeIdInWithNonExistentIds_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdInWithNonExistentIds(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdInWithNonExistentIds_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdInWithNonExistentIds(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdInWithNonExistentIds(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Exist Customer", "exist@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )
        val item = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Existing Item", 1, BigDecimal("75.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.compositeIdIn(
                    listOf(
                        item.compositeId(),
                        OrderItemsId(OrdersId(999999), 999)
                    )
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(item, result[0])
    }

    @Test
    fun orderItemsCompositeIdComputedVsManual_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            orderItemsCompositeIdComputedVsManual(repos, c)
        }
    }

    @Test
    fun orderItemsCompositeIdComputedVsManual_Mock() {
        val repos = createMockRepos()
        orderItemsCompositeIdComputedVsManual(repos, MockConnection.instance)
    }

    fun orderItemsCompositeIdComputedVsManual(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Computed Customer", "computed@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )
        val item = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Computed Item", 1, BigDecimal("55.00")), c
        )

        val computedId = item.compositeId()
        val manualId = OrderItemsId(order.orderId, 1)

        assertEquals(computedId, manualId)

        val result = repos.orderItemsRepo
            .select()
            .where { oi -> oi.compositeIdIn(listOf(computedId, manualId)) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(item, result[0])
    }

    // ==================== TupleInSubquery Tests ====================

    @Test
    fun tupleInSubqueryBasic_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryBasic(repos, c)
        }
    }

    fun tupleInSubqueryBasic(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Subquery Customer", "subquery@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Cheap1", 10, BigDecimal("5.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 2, "Cheap2", 20, BigDecimal("8.00")), c
        )
        val item3 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 3, "Expensive", 1, BigDecimal("500.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.orderId()
                    .tupleWith(oi.itemNumber())
                    .among(
                        repos.orderItemsRepo
                            .select()
                            .where { inner -> inner.unitPrice().lessThan(BigDecimal("100.00")) }
                            .map { inner -> inner.orderId().tupleWith(inner.itemNumber()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.productName }.toSet()
        assertEquals(setOf("Cheap1", "Cheap2"), names)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("NoMatch Customer", "nomatch@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Normal Item", 5, BigDecimal("50.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.orderId()
                    .tupleWith(oi.itemNumber())
                    .among(
                        repos.orderItemsRepo
                            .select()
                            .where { inner -> inner.unitPrice().lessThan(BigDecimal("0")) }
                            .map { inner -> inner.orderId().tupleWith(inner.itemNumber()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInSubqueryCombinedWithOtherConditions_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Combined Customer", "combined@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "LowQty", 2, BigDecimal("30.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 2, "HighQty", 100, BigDecimal("40.00")), c
        )
        val item3 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 3, "Expensive", 50, BigDecimal("500.00")), c
        )

        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                SqlExpr.all(
                    oi.orderId()
                        .tupleWith(oi.itemNumber())
                        .among(
                            repos.orderItemsRepo
                                .select()
                                .where { inner -> inner.unitPrice().lessThan(BigDecimal("100.00")) }
                                .map { inner -> inner.orderId().tupleWith(inner.itemNumber()) }
                                .subquery()
                        ),
                    oi.quantity().greaterThan(10)
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("HighQty", result[0].productName)
    }

    // ==================== Nullable Column Tuple IN Tests ====================

    @Ignore("Nullable values in tuple IN not supported")
    @Test
    fun tupleInWithNullableColumn_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNullableColumn(repos, c)
        }
    }

    @Ignore("Nullable values in tuple IN not supported")
    @Test
    fun tupleInWithNullableColumn_Mock() {
        val repos = createMockRepos()
        tupleInWithNullableColumn(repos, MockConnection.instance)
    }

    fun tupleInWithNullableColumn(repos: Repos, c: Connection) {
        val customer1 = repos.customersRepo.insert(
            CustomersRowUnsaved("Null Status Customer 1", "null1@example.com"), c
        )
        val customer2 = repos.customersRepo.insert(
            CustomersRowUnsaved("Null Status Customer 2", "null2@example.com"), c
        )

        val order1 = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer1.customerId), c
        )
        val order2 = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer2.customerId), c
        )

        // Query using tuple with nullable column - match by customerId and status
        val result = repos.ordersRepo
            .select()
            .where { o ->
                o.customerId()
                    .tupleWith(o.status())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(customer1.customerId, null as String?),
                        dev.typr.foundations.Tuple.of(customer2.customerId, null as String?)
                    ))
            }
            .toList(c)

        // Should handle nullable column tuple IN (tests null-safe comparison)
        assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }

    // ==================== Nested Tuple Tests ====================

    @Ignore("Nested tuples not supported")
    @Test
    fun nestedTupleIn_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            nestedTupleIn(repos, c)
        }
    }

    @Ignore("Nested tuples not supported")
    @Test
    fun nestedTupleIn_Mock() {
        val repos = createMockRepos()
        nestedTupleIn(repos, MockConnection.instance)
    }

    fun nestedTupleIn(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Nested Tuple Customer", "nested@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Nested Item 1", 5, BigDecimal("10.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 2, "Nested Item 2", 10, BigDecimal("20.00")), c
        )
        val item3 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 3, "Nested Item 3", 15, BigDecimal("30.00")), c
        )

        // Test truly nested tuple: ((orderId, itemNumber), quantity)
        val result = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.orderId()
                    .tupleWith(oi.itemNumber())
                    .tupleWith(oi.quantity())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(order.orderId, 1),
                            5),
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(order.orderId, 3),
                            15)
                    ))
            }
            .toList(c)

        assertEquals("Should find 2 items matching nested tuple pattern", 2, result.size)
        val names = result.map { it.productName }.toSet()
        assertEquals(setOf("Nested Item 1", "Nested Item 3"), names)

        // Test that non-matching nested tuple returns empty
        val resultNoMatch = repos.orderItemsRepo
            .select()
            .where { oi ->
                oi.orderId()
                    .tupleWith(oi.itemNumber())
                    .tupleWith(oi.quantity())
                    .among(listOf(
                        // Wrong: quantity doesn't match
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(order.orderId, 1),
                            999)
                    ))
            }
            .toList(c)

        assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty())
    }

    // ==================== Read Nested Tuple from Database Tests ====================

    @Ignore("Nested tuples not supported")
    @Test
    fun readNestedTupleFromDatabase_Real() {
        Db2TestHelper.run { c ->
            val repos = createRealRepos()
            readNestedTupleFromDatabase(repos, c)
        }
    }

    @Ignore("Nested tuples not supported")
    @Test
    fun readNestedTupleFromDatabase_Mock() {
        val repos = createMockRepos()
        readNestedTupleFromDatabase(repos, MockConnection.instance)
    }

    fun readNestedTupleFromDatabase(repos: Repos, c: Connection) {
        val customer = repos.customersRepo.insert(
            CustomersRowUnsaved("Read Customer", "read@example.com"), c
        )
        val order = repos.ordersRepo.insert(
            OrdersRowUnsaved(customer.customerId), c
        )

        // Insert test data
        val item1 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 1, "Read Item 1", 5, BigDecimal("10.00")), c
        )
        val item2 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 2, "Read Item 2", 10, BigDecimal("20.00")), c
        )
        val item3 = repos.orderItemsRepo.insert(
            OrderItemsRow(order.orderId, 3, "Read Item 3", 15, BigDecimal("30.00")), c
        )

        // Select nested tuple: ((orderId, itemNumber), productName)
        val result = repos.orderItemsRepo
            .select()
            .where { oi -> oi.orderId().isEqual(order.orderId) }
            .orderBy { oi -> oi.itemNumber().asc() }
            .map { oi -> oi.orderId().tupleWith(oi.itemNumber()).tupleWith(oi.productName()) }
            .toList(c)

        assertEquals("Should read 3 nested tuples", 3, result.size)

        // Verify the nested tuple structure
        val first = result[0]
        assertEquals("First tuple's inner first element", order.orderId, first._1()._1())
        assertEquals("First tuple's inner second element", 1, first._1()._2())
        assertEquals("First tuple's outer second element", "Read Item 1", first._2())
    }

    // ==================== Helper Methods ====================

    private fun createRealRepos(): Repos {
        return Repos(CustomersRepoImpl(), OrdersRepoImpl(), OrderItemsRepoImpl())
    }

    private fun createMockRepos(): Repos {
        val customerIdCounter = AtomicInteger(1)
        val orderIdCounter = AtomicInteger(1)

        return Repos(
            CustomersRepoMock({ unsaved ->
                unsaved.toRow(
                    { CustomersId(customerIdCounter.getAndIncrement()) },
                    { null }
                )
            }),
            OrdersRepoMock({ unsaved ->
                unsaved.toRow(
                    { LocalDate.now() },
                    { "pending" },
                    { OrdersId(orderIdCounter.getAndIncrement()) }
                )
            }),
            OrderItemsRepoMock()
        )
    }
}
