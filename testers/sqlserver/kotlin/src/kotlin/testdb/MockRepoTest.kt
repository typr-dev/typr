package testdb

import dev.typr.foundations.dsl.MockConnection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.products.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for mock repository implementations. Mock repos provide in-memory implementations for unit
 * testing without database access.
 */
class MockRepoTest {

    private fun createCustomersMock(): Pair<CustomersRepoMock, AtomicInteger> {
        val idCounter = AtomicInteger(1)
        val mockRepo = CustomersRepoMock({ unsaved ->
            CustomersRow(
                customerId = CustomersId(idCounter.getAndIncrement()),
                name = unsaved.name,
                email = unsaved.email,
                createdAt = unsaved.createdAt.getOrElse { LocalDateTime.now() }
            )
        })
        return Pair(mockRepo, idCounter)
    }

    private fun createProductsMock(): Pair<ProductsRepoMock, AtomicInteger> {
        val idCounter = AtomicInteger(1)
        val mockRepo = ProductsRepoMock({ unsaved ->
            ProductsRow(
                productId = ProductsId(idCounter.getAndIncrement()),
                name = unsaved.name,
                price = unsaved.price,
                description = unsaved.description
            )
        })
        return Pair(mockRepo, idCounter)
    }

    @Test
    fun testMockInsertAndSelect() {
        val (mock, _) = createCustomersMock()
        val customer = CustomersRow(
            CustomersId(1),
            "Mock User",
            Email("mock@test.com"),
            LocalDateTime.now()
        )

        val inserted = mock.insert(customer, MockConnection.instance)
        assertNotNull(inserted)
        assertEquals("Mock User", inserted.name)

        val found = mock.selectById(CustomersId(1), MockConnection.instance)
        assertNotNull(found)
        assertEquals("Mock User", found!!.name)
    }

    @Test
    fun testMockUpdate() {
        val (mock, _) = createCustomersMock()
        val customer = CustomersRow(
            CustomersId(2),
            "Original Name",
            Email("original@test.com"),
            LocalDateTime.now()
        )

        mock.insert(customer, MockConnection.instance)

        val updated = customer.copy(name = "Updated Name")
        mock.update(updated, MockConnection.instance)

        val found = mock.selectById(CustomersId(2), MockConnection.instance)!!
        assertEquals("Updated Name", found.name)
    }

    @Test
    fun testMockDelete() {
        val (mock, _) = createCustomersMock()
        val customer = CustomersRow(
            CustomersId(3),
            "To Delete",
            Email("delete@test.com"),
            LocalDateTime.now()
        )

        mock.insert(customer, MockConnection.instance)
        assertNotNull(mock.selectById(CustomersId(3), MockConnection.instance))

        mock.deleteById(CustomersId(3), MockConnection.instance)
        assertNull(mock.selectById(CustomersId(3), MockConnection.instance))
    }

    @Test
    fun testMockProductsInsertAndSelect() {
        val (mock, _) = createProductsMock()
        val product = ProductsRow(
            ProductsId(1),
            "Test Product",
            BigDecimal("29.99"),
            "A test product"
        )

        val inserted = mock.insert(product, MockConnection.instance)
        assertNotNull(inserted)
        assertEquals("Test Product", inserted.name)

        val found = mock.selectById(ProductsId(1), MockConnection.instance)
        assertNotNull(found)
        assertEquals(0, BigDecimal("29.99").compareTo(found!!.price))
    }

    @Test
    fun testMockDSLQuery() {
        val (mock, _) = createCustomersMock()

        mock.insert(CustomersRow(CustomersId(100), "Alice", Email("alice@test.com"), LocalDateTime.now()), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(101), "Bob", Email("bob@test.com"), LocalDateTime.now()), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(102), "Charlie", Email("charlie@test.com"), LocalDateTime.now()), MockConnection.instance)

        val results = mock.select()
            .where { customer -> customer.customerId().greaterThan(CustomersId(100)) }
            .orderBy { customer -> customer.name().asc() }
            .toList(MockConnection.instance)

        assertEquals(2, results.size)
        assertEquals("Bob", results[0].name)
        assertEquals("Charlie", results[1].name)
    }

    @Test
    fun testMockDSLCount() {
        val (mock, _) = createCustomersMock()

        mock.insert(CustomersRow(CustomersId(200), "Count1", Email("count1@test.com"), LocalDateTime.now()), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(201), "Count2", Email("count2@test.com"), LocalDateTime.now()), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(202), "Other", Email("other@test.com"), LocalDateTime.now()), MockConnection.instance)

        val count = mock.select()
            .where { customer -> customer.name().like("Count%") }
            .count(MockConnection.instance)

        assertEquals(2, count)
    }

    @Test
    fun testMockIsolation() {
        val (mock1, _) = createCustomersMock()
        val (mock2, _) = createCustomersMock()

        mock1.insert(
            CustomersRow(CustomersId(400), "Mock1 Only", Email("mock1@test.com"), LocalDateTime.now()),
            MockConnection.instance
        )

        assertNotNull(mock1.selectById(CustomersId(400), MockConnection.instance))
        assertNull(mock2.selectById(CustomersId(400), MockConnection.instance))
    }

    @Test
    fun testMockProductsDSL() {
        val (mock, _) = createProductsMock()

        mock.insert(ProductsRow(ProductsId(1), "Cheap", BigDecimal("9.99"), "Cheap product"), MockConnection.instance)
        mock.insert(ProductsRow(ProductsId(2), "Medium", BigDecimal("49.99"), "Medium product"), MockConnection.instance)
        mock.insert(ProductsRow(ProductsId(3), "Expensive", BigDecimal("199.99"), "Expensive product"), MockConnection.instance)

        val expensiveProducts = mock.select()
            .where { p -> p.price().greaterThan(BigDecimal("50.00")) }
            .toList(MockConnection.instance)

        assertEquals(1, expensiveProducts.size)
        assertEquals("Expensive", expensiveProducts[0].name)
    }
}
