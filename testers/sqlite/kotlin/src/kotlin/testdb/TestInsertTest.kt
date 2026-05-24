package testdb

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Random

class TestInsertTest {
    private val testInsert = TestInsert(Random(1794141443))

    @Test
    fun customersInsert() {
        SqliteTestHelper.run { c ->
            val row = testInsert.Customers(name = "Test Customer", c = c)
            assertNotNull(row.customerId)
            assertEquals("Test Customer", row.name)
        }
    }

    @Test
    fun departmentsInsert() {
        SqliteTestHelper.run { c ->
            val row = testInsert.Departments(
                deptCode = "DEPT1", deptRegion = "NORTH", deptName = "Test Department", c = c
            )
            assertEquals("DEPT1", row.deptCode)
            assertEquals("NORTH", row.deptRegion)
        }
    }

    @Test
    fun productsInsert() {
        SqliteTestHelper.run { c ->
            val row = testInsert.Products(
                sku = "SKU001", name = "Test Product", price = BigDecimal("99.99"), c = c
            )
            assertNotNull(row.productId)
            assertEquals("SKU001", row.sku)
        }
    }

    @Test
    fun allScalarTypesInsert() {
        SqliteTestHelper.run { c ->
            val row = testInsert.AllScalarTypes(colNotNull = "required_value", c = c)
            assertNotNull(row.id)
            assertEquals("required_value", row.colNotNull)
        }
    }

    @Test
    fun employeesWithDepartmentFK() {
        SqliteTestHelper.run { c ->
            val dept = testInsert.Departments(
                deptCode = "EMP_DEPT", deptRegion = "WEST", deptName = "Employee Dept", c = c
            )
            val emp = testInsert.Employees(
                DepartmentsId = dept.compositeId(), empSuffix = "Jr", empName = "John Doe", c = c
            )
            assertEquals(dept.deptCode, emp.deptCode)
            assertEquals(dept.deptRegion, emp.deptRegion)
        }
    }

    @Test
    fun ordersWithCustomerFK() {
        SqliteTestHelper.run { c ->
            val customer = testInsert.Customers(name = "Order Customer", c = c)
            val order = testInsert.Orders(customerId = customer.customerId, c = c)
            assertEquals(customer.customerId, order.customerId)
        }
    }

    @Test
    fun customersWithCustomization() {
        SqliteTestHelper.run { c ->
            val row = testInsert.Customers(name = "Custom Name", c = c)
            assertEquals("Custom Name", row.name)
        }
    }

    @Test
    fun seededRandomReproducible() {
        val id1 = SqliteTestHelper.apply { TestInsert(Random(123)).Customers(name = "x", c = it).customerId.value }
        val id2 = SqliteTestHelper.apply { TestInsert(Random(123)).Customers(name = "x", c = it).customerId.value }
        assertEquals(id1, id2)
    }

    @Test
    fun multipleInserts() {
        SqliteTestHelper.run { c ->
            val r1 = testInsert.Customers(name = "C1", c = c)
            val r2 = testInsert.Customers(name = "C2", c = c)
            val r3 = testInsert.Customers(name = "C3", c = c)
            assertNotEquals(r1.customerId, r2.customerId)
            assertNotEquals(r2.customerId, r3.customerId)
        }
    }
}
