package testdb

import dev.typr.dslkt.MockConnection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.departments.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDateTime

class MockRepoTest {

    private fun mkCustomersMock(): CustomersRepoMock = CustomersRepoMock({ unsaved ->
        CustomersRow(
            customerId = unsaved.customerId,
            name = unsaved.name,
            email = unsaved.email,
            createdAt = unsaved.createdAt.getOrElse { LocalDateTime.of(2025, 1, 1, 0, 0) }
        )
    })

    @Test
    fun testMockInsertAndSelect() {
        val mock = mkCustomersMock()
        val customer = CustomersRow(CustomersId(1L), "Mock User", Email("mock@test.com"), LocalDateTime.of(2025, 1, 1, 0, 0))
        val inserted = mock.insert(customer, MockConnection.instance)
        assertEquals("Mock User", inserted.name)
        val found = mock.selectById(CustomersId(1L), MockConnection.instance)
        assertNotNull(found)
        assertEquals("Mock User", found!!.name)
    }

    @Test
    fun testMockUpdate() {
        val mock = mkCustomersMock()
        val customer = CustomersRow(CustomersId(2L), "Original", null, LocalDateTime.of(2025, 1, 1, 0, 0))
        mock.insert(customer, MockConnection.instance)
        mock.update(customer.copy(name = "Updated"), MockConnection.instance)
        assertEquals("Updated", mock.selectById(CustomersId(2L), MockConnection.instance)!!.name)
    }

    @Test
    fun testMockDelete() {
        val mock = mkCustomersMock()
        mock.insert(CustomersRow(CustomersId(3L), "Del", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        assertNotNull(mock.selectById(CustomersId(3L), MockConnection.instance))
        mock.deleteById(CustomersId(3L), MockConnection.instance)
        assertNull(mock.selectById(CustomersId(3L), MockConnection.instance))
    }

    @Test
    fun testMockSelectAll() {
        val mock = mkCustomersMock()
        mock.insert(CustomersRow(CustomersId(10L), "U1", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(11L), "U2", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(12L), "U3", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        assertEquals(3, mock.selectAll(MockConnection.instance).size)
    }

    @Test
    fun testMockDSLLimit() {
        val mock = mkCustomersMock()
        for (i in 0 until 10) {
            mock.insert(CustomersRow(CustomersId(300L + i), "Limit$i", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        }
        val results = mock.select().where { it.name().like("Limit%") }.limit(5).toList(MockConnection.instance)
        assertEquals(5, results.size)
    }

    @Test
    fun testMockWithCompositeKey() {
        val mock = DepartmentsRepoMock()
        mock.insert(DepartmentsRow("MOCK_IT", "MOCK_US", "Mock IT US", BigDecimal("500000")), MockConnection.instance)
        val found = mock.selectById(DepartmentsId("MOCK_IT", "MOCK_US"), MockConnection.instance)
        assertNotNull(found)
        assertEquals("Mock IT US", found!!.deptName)
    }

    @Test
    fun testMockDSLQuery() {
        val mock = mkCustomersMock()
        mock.insert(CustomersRow(CustomersId(100L), "Alice", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(101L), "Bob", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(102L), "Charlie", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)

        val results = mock.select()
            .where { it.customerId().greaterThan(CustomersId(100L)) }
            .orderBy { it.name().asc() }
            .toList(MockConnection.instance)

        assertEquals(2, results.size)
        assertEquals("Bob", results[0].name)
        assertEquals("Charlie", results[1].name)
    }

    @Test
    fun testMockDSLCount() {
        val mock = mkCustomersMock()
        mock.insert(CustomersRow(CustomersId(200L), "Count1", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(201L), "Count2", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(202L), "Other", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)

        val count = mock.select()
            .where { it.name().like("Count%") }
            .count(MockConnection.instance)
        assertEquals(2, count)
    }

    @Test
    fun testMockIsolation() {
        val mock1 = mkCustomersMock()
        val mock2 = mkCustomersMock()
        mock1.insert(CustomersRow(CustomersId(400L), "M1", null, LocalDateTime.of(2025, 1, 1, 0, 0)), MockConnection.instance)
        assertNotNull(mock1.selectById(CustomersId(400L), MockConnection.instance))
        assertNull(mock2.selectById(CustomersId(400L), MockConnection.instance))
    }
}
