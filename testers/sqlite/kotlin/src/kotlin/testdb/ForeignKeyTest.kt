package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.order_items.*
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ForeignKeyTest {
    private val customers = CustomersRepoImpl()
    private val products = ProductsRepoImpl()
    private val orders = OrdersRepoImpl()
    private val orderItems = OrderItemsRepoImpl()

    @Test
    fun customerInsert() {
        SqliteTestHelper.run { c ->
            val inserted = customers.insert(
                CustomersRow(CustomersId(100L), "John", Email("john@x.com"), LocalDateTime.of(2025, 1, 1, 0, 0)), c
            )
            assertEquals("John", inserted.name)
        }
    }

    @Test
    fun productInsert() {
        SqliteTestHelper.run { c ->
            val inserted = products.insert(
                ProductsRow(ProductsId(100L), "PROD-100", "Widget", BigDecimal("29.99"), null), c
            )
            assertEquals("PROD-100", inserted.sku)
        }
    }

    @Test
    fun orderWithCustomerFK() {
        SqliteTestHelper.run { c ->
            val cust = customers.insert(
                CustomersRow(CustomersId(101L), "Jane", Email("jane@x.com"), LocalDateTime.of(2025, 1, 2, 0, 0)), c
            )
            val ord = orders.insert(
                OrdersRow(OrdersId(101L), cust.customerId, LocalDate.of(2025, 1, 15), BigDecimal("99.99"), "pending"), c
            )
            assertEquals(cust.customerId, ord.customerId)
        }
    }

    @Test
    fun orderItemWithCompositePK() {
        SqliteTestHelper.run { c ->
            customers.insert(CustomersRow(CustomersId(102L), "x", null, LocalDateTime.of(2025, 1, 1, 0, 0)), c)
            val prod = products.insert(ProductsRow(ProductsId(102L), "PROD-102", "W", BigDecimal("49.99"), null), c)
            val ord = orders.insert(
                OrdersRow(OrdersId(102L), CustomersId(102L), LocalDate.of(2025, 1, 16), BigDecimal("149.97"), "pending"), c
            )
            val item = orderItems.insert(OrderItemsRow(ord.orderId, prod.productId, 3L, BigDecimal("49.99")), c)
            assertEquals(3L, item.quantity)
        }
    }

    @Test
    fun foreignKeyEnforced() {
        var threw = false
        try {
            SqliteTestHelper.run { c ->
                orders.insert(
                    OrdersRow(OrdersId(9999L), CustomersId(99999L), LocalDate.of(2025, 1, 1), null, "pending"), c
                )
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            threw = msg.contains("FOREIGN KEY") || msg.lowercase().contains("foreign key")
        }
        assertTrue("Expected FK violation", threw)
    }

    @Test
    fun typeSafeIds() {
        SqliteTestHelper.run { c ->
            val cid = CustomersId(200L); val pid = ProductsId(200L); val oid = OrdersId(200L)
            customers.insert(CustomersRow(cid, "TS", null, LocalDateTime.of(2025, 1, 1, 0, 0)), c)
            products.insert(ProductsRow(pid, "SKU-200", "TS Prod", BigDecimal("1.00"), null), c)
            orders.insert(OrdersRow(oid, cid, LocalDate.of(2025, 1, 1), null, "pending"), c)
            assertNotNull(customers.selectById(cid, c))
            assertNotNull(products.selectById(pid, c))
            assertNotNull(orders.selectById(oid, c))
        }
    }
}
