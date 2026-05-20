package testdb

import dev.typr.foundations.data.Json
import org.junit.Assert.*
import org.junit.Test
import testdb.customer_orders.CustomerOrdersViewRepoImpl
import testdb.customers.*
import testdb.customtypes.Defaulted
import testdb.order_details.OrderDetailsViewRepoImpl
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DatabaseFeaturesTest {
    private val customers = CustomersRepoImpl()
    private val products = ProductsRepoImpl()
    private val orders = OrdersRepoImpl()
    private val customerOrdersRepo = CustomerOrdersViewRepoImpl()
    private val orderDetailsRepo = OrderDetailsViewRepoImpl()

    @Test
    fun insertReturningPopulatesGeneratedColumns() {
        SqliteTestHelper.run { c ->
            val unsaved = CustomersRowUnsaved(CustomersId(10001L), "Returning Test")
            val inserted = customers.insert(unsaved, c)
            assertEquals(10001L, inserted.customerId.value)
            assertNotNull(inserted.createdAt)
        }
    }

    @Test
    fun insertWithProvidedDefault() {
        SqliteTestHelper.run { c ->
            val explicit = LocalDateTime.of(2020, 5, 1, 12, 0)
            val unsaved = CustomersRowUnsaved(
                CustomersId(10002L), "Explicit", Email("u@x.com"), Defaulted.Provided(explicit)
            )
            assertEquals(explicit, customers.insert(unsaved, c).createdAt)
        }
    }

    @Test
    fun upsertInsertsNew() {
        SqliteTestHelper.run { c ->
            val row = CustomersRow(CustomersId(20001L), "Upsert Insert", null, LocalDateTime.of(2025, 1, 1, 0, 0))
            assertEquals("Upsert Insert", customers.upsert(row, c).name)
        }
    }

    @Test
    fun upsertUpdatesOnConflict() {
        SqliteTestHelper.run { c ->
            val row = CustomersRow(CustomersId(20002L), "Original", Email("a@x.com"), LocalDateTime.of(2025, 1, 1, 0, 0))
            customers.insert(row, c)
            val updated = row.copy(name = "Updated via Upsert", email = Email("b@x.com"))
            val result = customers.upsert(updated, c)
            assertEquals("Updated via Upsert", result.name)
            assertEquals("Updated via Upsert", customers.selectById(CustomersId(20002L), c)!!.name)
        }
    }

    @Test
    fun uuidJsonRoundTrip() {
        SqliteTestHelper.run { c ->
            val uuid = UUID.randomUUID()
            val product = ProductsRow(
                ProductsId(30001L), "SKU-UUID-$uuid", "UUID Product", BigDecimal("1.00"),
                Json("{\"uuid\":\"$uuid\"}")
            )
            val inserted = products.insert(product, c)
            assertTrue(inserted.metadata!!.value().contains(uuid.toString()))
        }
    }

    @Test
    fun customerOrdersView() {
        SqliteTestHelper.applyRead { c ->
            val rows = customerOrdersRepo.selectAll(c)
            assertEquals(2, rows.size)
            assertTrue(rows.any { it.customerName == "John Doe" })
            assertTrue(rows.any { it.customerName == "Jane Smith" })
        }
    }

    @Test
    fun orderDetailsView() {
        SqliteTestHelper.applyRead { c ->
            val rows = orderDetailsRepo.selectAll(c)
            assertEquals(3, rows.size)
            rows.forEach { r ->
                val s = r.lineTotal!!
                assertTrue(BigDecimal(s) > BigDecimal.ZERO)
            }
        }
    }

    @Test
    fun selectByIdsReturnsRequested() {
        SqliteTestHelper.applyRead { c ->
            val found = customers.selectByIds(listOf(CustomersId(1L), CustomersId(2L)), c)
            assertEquals(2, found.size)
        }
    }

    @Test
    fun insertWithExplicitId() {
        SqliteTestHelper.run { c ->
            val row = CustomersRow(CustomersId(50001L), "Explicit ID", null, LocalDateTime.of(2025, 1, 1, 0, 0))
            val inserted = customers.insert(row, c)
            assertEquals(50001L, inserted.customerId.value)
        }
    }

    @Test
    fun orderInsertWithForeignKeyValue() {
        SqliteTestHelper.run { c ->
            val ord = orders.insert(
                OrdersRow(OrdersId(70001L), CustomersId(1L), LocalDate.of(2025, 7, 1), BigDecimal("250.00"), "pending"), c
            )
            assertEquals(70001L, ord.orderId.value)
        }
    }
}
