package testdb

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Random

class TestInsertTest {
    private val testInsert = TestInsert(Random(1794141443))

    @Test
    fun testCustomersInsert() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.Customers(
                name = "Test Customer",
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.customerId)
            assertEquals("Test Customer", row.name)
        }
    }

    @Test
    fun testDepartmentsInsert() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.Departments(
                deptCode = "DEPT1",
                deptRegion = "NORTH",
                deptName = "Test Department",
                c = c
            )

            assertNotNull(row)
            assertEquals("DEPT1", row.deptCode)
            assertEquals("NORTH", row.deptRegion)
            assertEquals("Test Department", row.deptName)
        }
    }

    @Test
    fun testProductsInsert() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.Products(
                sku = "SKU001",
                name = "Test Product",
                price = BigDecimal("99.99"),
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.productId)
            assertEquals("SKU001", row.sku)
            assertEquals("Test Product", row.name)
        }
    }

    @Test
    fun testAllScalarTypesInsert() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.AllScalarTypes(
                colNotNull = "required_value",
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.id)
            assertEquals("required_value", row.colNotNull)
        }
    }

    @Test
    fun testEmployeesWithDepartmentFK() {
        DuckDbTestHelper.run { c ->
            val dept = testInsert.Departments(
                deptCode = "EMP_DEPT",
                deptRegion = "WEST",
                deptName = "Employee Dept",
                c = c
            )

            val emp = testInsert.Employees(
                empSuffix = "Jr",
                deptCode = dept.deptCode,
                deptRegion = dept.deptRegion,
                empName = "John Doe",
                c = c
            )

            assertNotNull(emp)
            assertEquals(dept.deptCode, emp.deptCode)
            assertEquals(dept.deptRegion, emp.deptRegion)
        }
    }

    @Test
    fun testOrdersWithCustomerFK() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Order Customer",
                c = c
            )

            val order = testInsert.Orders(
                customerId = customer.customerId.value,
                c = c
            )

            assertNotNull(order)
            assertEquals(customer.customerId.value, order.customerId)
        }
    }

    @Test
    fun testMultipleInserts() {
        DuckDbTestHelper.run { c ->
            val row1 = testInsert.Customers(name = "Customer1", c = c)
            val row2 = testInsert.Customers(name = "Customer2", c = c)
            val row3 = testInsert.Customers(name = "Customer3", c = c)

            assertNotEquals(row1.customerId, row2.customerId)
            assertNotEquals(row2.customerId, row3.customerId)
            assertNotEquals(row1.customerId, row3.customerId)
        }
    }

    @Test
    fun testInsertWithDifferentNames() {
        DuckDbTestHelper.run { c ->
            val row1 = testInsert.Customers(name = "Alpha", c = c)
            val row2 = testInsert.Customers(name = "Beta", c = c)

            assertEquals("Alpha", row1.name)
            assertEquals("Beta", row2.name)
        }
    }
}
