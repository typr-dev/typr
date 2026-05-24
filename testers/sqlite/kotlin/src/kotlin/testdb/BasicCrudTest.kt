package testdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import testdb.customers.CustomersId
import testdb.customers.CustomersRepoImpl
import testdb.customers.CustomersRowUnsaved
import testdb.customtypes.Defaulted
import testdb.products.ProductsRepoImpl

class BasicCrudTest {
    private val customers = CustomersRepoImpl()
    private val products = ProductsRepoImpl()

    @Test
    fun selectAllCustomersFromSeedData() {
        val rows = SqliteTestHelper.applyRead { customers.selectAll(it) }
        assertEquals(2, rows.size)
    }

    @Test
    fun findCustomerById() {
        val row = SqliteTestHelper.applyRead { customers.selectById(CustomersId(1L), it) }
        assertNotNull(row)
        assertEquals("John Doe", row!!.name)
    }

    @Test
    fun deleteCustomerById() {
        val deleted = SqliteTestHelper.apply { conn ->
            val inserted = customers.insert(
                CustomersRowUnsaved(CustomersId(999L), "Disposable", null, Defaulted.UseDefault()), conn
            )
            customers.deleteById(inserted.customerId, conn)
        }
        assertTrue(deleted)
    }

    @Test
    fun productsHaveExpectedPrices() {
        val ps = SqliteTestHelper.applyRead { products.selectAll(it) }
        assertEquals(2, ps.size)
        val skus = ps.map { it.sku }.sorted()
        assertEquals(listOf("PROD-001", "PROD-002"), skus)
    }
}
