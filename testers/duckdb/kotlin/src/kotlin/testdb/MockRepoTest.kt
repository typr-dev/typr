package testdb

import dev.typr.foundations.dsl.MockConnection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.departments.*
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDateTime

class MockRepoTest {

    private fun createCustomersMock(): CustomersRepoMock {
        return CustomersRepoMock({ unsaved ->
            CustomersRow(
                customerId = unsaved.customerId,
                name = unsaved.name,
                email = unsaved.email,
                createdAt = unsaved.createdAt.getOrElse { LocalDateTime.now() },
                priority = unsaved.priority.getOrElse { Priority.medium }
            )
        })
    }

    private fun createDepartmentsMock(): DepartmentsRepoMock {
        return DepartmentsRepoMock()
    }

    @Test
    fun testMockInsertAndSelect() {
        val mock = createCustomersMock()
        val customer = CustomersRow(
            CustomersId(1), "Mock User", Email("mock@test.com"),
            LocalDateTime.now(), Priority.medium
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
        val mock = createCustomersMock()
        val customer = CustomersRow(CustomersId(2), "Original Name", null, LocalDateTime.now(), null)

        mock.insert(customer, MockConnection.instance)

        val updated = customer.copy(name = "Updated Name")
        mock.update(updated, MockConnection.instance)

        val found = mock.selectById(CustomersId(2), MockConnection.instance)!!
        assertEquals("Updated Name", found.name)
    }

    @Test
    fun testMockDelete() {
        val mock = createCustomersMock()
        val customer = CustomersRow(CustomersId(3), "To Delete", null, LocalDateTime.now(), null)

        mock.insert(customer, MockConnection.instance)
        assertNotNull(mock.selectById(CustomersId(3), MockConnection.instance))

        mock.deleteById(CustomersId(3), MockConnection.instance)
        assertNull(mock.selectById(CustomersId(3), MockConnection.instance))
    }

    @Test
    fun testMockWithCompositeKey() {
        val mock = createDepartmentsMock()
        val dept = DepartmentsRow("MOCK_IT", "MOCK_US", "Mock IT US", BigDecimal("500000"))

        mock.insert(dept, MockConnection.instance)

        val id = DepartmentsId("MOCK_IT", "MOCK_US")
        val found = mock.selectById(id, MockConnection.instance)

        assertNotNull(found)
        assertEquals("Mock IT US", found!!.deptName)
    }

    @Test
    fun testMockDSLQuery() {
        val mock = createCustomersMock()

        mock.insert(CustomersRow(CustomersId(100), "Alice", null, LocalDateTime.now(), null), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(101), "Bob", null, LocalDateTime.now(), null), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(102), "Charlie", null, LocalDateTime.now(), null), MockConnection.instance)

        val results = mock.select()
            .where { c -> c.customerId().greaterThan(CustomersId(100)) }
            .orderBy { c -> c.name().asc() }
            .toList(MockConnection.instance)

        assertEquals(2, results.size)
        assertEquals("Bob", results[0].name)
        assertEquals("Charlie", results[1].name)
    }

    @Test
    fun testMockDSLCount() {
        val mock = createCustomersMock()

        mock.insert(CustomersRow(CustomersId(200), "Count1", null, LocalDateTime.now(), null), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(201), "Count2", null, LocalDateTime.now(), null), MockConnection.instance)
        mock.insert(CustomersRow(CustomersId(202), "Other", null, LocalDateTime.now(), null), MockConnection.instance)

        val count = mock.select()
            .where { c -> c.name().like("Count%") }
            .count(MockConnection.instance)

        assertEquals(2, count)
    }

    @Test
    fun testMockIsolation() {
        val mock1 = createCustomersMock()
        val mock2 = createCustomersMock()

        mock1.insert(CustomersRow(CustomersId(400), "Mock1 Only", null, LocalDateTime.now(), null), MockConnection.instance)

        assertNotNull(mock1.selectById(CustomersId(400), MockConnection.instance))
        assertNull(mock2.selectById(CustomersId(400), MockConnection.instance))
    }
}
