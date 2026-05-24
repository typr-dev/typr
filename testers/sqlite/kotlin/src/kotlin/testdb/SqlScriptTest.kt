package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types_search.AllScalarTypesSearchSqlRepoImpl
import testdb.customer_search.CustomerSearchSqlRepoImpl
import testdb.delete_old_orders.DeleteOldOrdersSqlRepoImpl
import testdb.order_summary_by_customer.OrderSummaryByCustomerSqlRepoImpl
import testdb.product_details_with_sales.ProductDetailsWithSalesSqlRepoImpl
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class SqlScriptTest {
    private val customerSearch = CustomerSearchSqlRepoImpl()
    private val orderSummary = OrderSummaryByCustomerSqlRepoImpl()
    private val productDetails = ProductDetailsWithSalesSqlRepoImpl()
    private val scalarSearch = AllScalarTypesSearchSqlRepoImpl()
    private val deleteOld = DeleteOldOrdersSqlRepoImpl()

    @Test
    fun customerSearchAll() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerSearch.apply(null, null, null, 100L, c)
            assertEquals(2, rows.size)
        }
    }

    @Test
    fun customerSearchByName() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerSearch.apply("John%", null, null, 100L, c)
            assertEquals(1, rows.size)
            assertEquals("John Doe", rows[0].name)
        }
    }

    @Test
    fun customerSearchByEmail() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerSearch.apply(null, "%jane%", null, 100L, c)
            assertEquals(1, rows.size)
        }
    }

    @Test
    fun customerSearchLimit() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerSearch.apply(null, null, null, 1L, c)
            assertEquals(1, rows.size)
        }
    }

    @Test
    fun customerSearchByCreatedAfter() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerSearch.apply(null, null, LocalDateTime.of(2099, 1, 1, 0, 0), 100L, c)
            assertEquals(0, rows.size)
        }
    }

    @Test
    fun orderSummaryInRange() {
        SqliteTestHelper.applyRead { c ->
            val rows = orderSummary.apply(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), c)
            assertEquals(2, rows.size)
            val total = rows.mapNotNull { it.totalRevenue }.fold(BigDecimal.ZERO) { a, b -> a.add(b) }
            assertEquals(0, BigDecimal("109.97").compareTo(total))
        }
    }

    @Test
    fun orderSummaryEmptyRange() {
        SqliteTestHelper.applyRead { c ->
            val rows = orderSummary.apply(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31), c)
            assertEquals(0, rows.size)
        }
    }

    @Test
    fun productDetailsAll() {
        SqliteTestHelper.applyRead { c ->
            val rows = productDetails.apply(null, c)
            assertEquals(2, rows.size)
        }
    }

    @Test
    fun productDetailsMinPrice() {
        SqliteTestHelper.applyRead { c ->
            val rows = productDetails.apply(BigDecimal("40.00"), c)
            assertEquals(1, rows.size)
            assertEquals("Widget B", rows[0].productName)
        }
    }

    @Test
    fun allScalarSearchAll() {
        SqliteTestHelper.applyRead { c ->
            val rows = scalarSearch.apply(null, null, null, c)
            assertEquals(1, rows.size)
        }
    }

    @Test
    fun allScalarSearchMinId() {
        SqliteTestHelper.applyRead { c ->
            val rows = scalarSearch.apply(100L, null, null, c)
            assertEquals(0, rows.size)
        }
    }

    @Test
    fun allScalarSearchByText() {
        SqliteTestHelper.applyRead { c ->
            val rows = scalarSearch.apply(null, "hel%", null, c)
            assertEquals(1, rows.size)
        }
    }

    @Test
    fun deleteOldOrders() {
        SqliteTestHelper.run { c ->
            val ordersRepo = testdb.orders.OrdersRepoImpl()
            ordersRepo.insert(
                testdb.orders.OrdersRow(
                    testdb.orders.OrdersId(9001L),
                    testdb.customers.CustomersId(1L),
                    LocalDate.of(2020, 1, 1),
                    BigDecimal("10.00"),
                    "completed"
                ), c
            )
            val deleted = deleteOld.apply(LocalDate.of(2025, 1, 1), c)
            assertEquals(1, deleted)
        }
    }

    @Test
    fun deleteOldOrdersNoneMatch() {
        SqliteTestHelper.run { c ->
            val deleted = deleteOld.apply(LocalDate.of(1900, 1, 1), c)
            assertEquals(0, deleted)
        }
    }
}
