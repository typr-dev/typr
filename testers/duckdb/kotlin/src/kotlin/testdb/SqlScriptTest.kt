package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customer_search.*
import testdb.department_employee_details.*
import testdb.employee_salary_update.*
import testdb.order_summary_by_customer.*
import testdb.product_summary.*
import testdb.customers.CustomersId
import testdb.customtypes.Defaulted
import testdb.userdefined.Email
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Random

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/duckdb/.
 */
class SqlScriptTest {
    private val testInsert = TestInsert(Random(42))
    private val customerSearchRepo = CustomerSearchSqlRepoImpl()
    private val orderSummaryRepo = OrderSummaryByCustomerSqlRepoImpl()
    private val productSummaryRepo = ProductSummarySqlRepoImpl()
    private val deptEmpDetailsRepo = DepartmentEmployeeDetailsSqlRepoImpl()
    private val empSalaryUpdateRepo = EmployeeSalaryUpdateSqlRepoImpl()

    @Test
    fun testCustomerSearchWithNamePattern() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(name = "SearchMe Customer", c = c)

            val results = customerSearchRepo.apply(
                namePattern = "SearchMe%",
                emailPattern = null,
                minPriority = null,
                createdAfter = null,
                maxResults = 10L,
                c = c
            )

            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.customerId == customer.customerId })
        }
    }

    @Test
    fun testCustomerSearchWithEmailPattern() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "Email Customer",
                email = Email("unique-kotlin-search@example.com"),
                c = c
            )

            val results = customerSearchRepo.apply(
                namePattern = null,
                emailPattern = "%unique-kotlin-search%",
                minPriority = null,
                createdAfter = null,
                maxResults = 10L,
                c = c
            )

            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.customerId == customer.customerId })
        }
    }

    @Test
    fun testCustomerSearchWithPriorityFilter() {
        DuckDbTestHelper.run { c ->
            val highPriorityCustomer = testInsert.Customers(
                name = "High Priority Customer",
                priority = Defaulted.Provided(Priority.high),
                c = c
            )

            val results = customerSearchRepo.apply(
                namePattern = null,
                emailPattern = null,
                minPriority = Priority.high,
                createdAfter = null,
                maxResults = 100L,
                c = c
            )

            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.customerId == highPriorityCustomer.customerId })
        }
    }

    @Test
    fun testCustomerSearchWithDateFilter() {
        DuckDbTestHelper.run { c ->
            testInsert.Customers(name = "Date Test Customer", c = c)
            val yesterday = LocalDateTime.now().minusDays(1)

            val results = customerSearchRepo.apply(
                namePattern = null,
                emailPattern = null,
                minPriority = null,
                createdAfter = yesterday,
                maxResults = 10L,
                c = c
            )

            assertTrue(results.isNotEmpty())
        }
    }

    @Test
    fun testCustomerSearchWithMaxResults() {
        DuckDbTestHelper.run { c ->
            for (i in 0 until 5) {
                testInsert.Customers(name = "KotlinLimitTest$i", c = c)
            }

            val results = customerSearchRepo.apply(
                namePattern = "KotlinLimitTest%",
                emailPattern = null,
                minPriority = null,
                createdAfter = null,
                maxResults = 3L,
                c = c
            )

            assertEquals(3, results.size)
        }
    }

    @Test
    fun testOrderSummaryByCustomerNoFilters() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(name = "Order Summary Customer", c = c)
            testInsert.Orders(
                customerId = customer.customerId.value,
                totalAmount = BigDecimal("150.00"),
                c = c
            )

            val results = orderSummaryRepo.apply(
                customerIds = null,
                minTotal = null,
                minOrderCount = null,
                c = c
            )

            assertTrue(results.isNotEmpty())
            val customerSummary = results.find { it.customerId == customer.customerId }
            assertNotNull(customerSummary)
            assertEquals(1L, customerSummary!!.orderCount)
        }
    }

    @Test
    fun testOrderSummaryByCustomerIds() {
        DuckDbTestHelper.run { c ->
            val customer1 = testInsert.Customers(name = "Customer 1", c = c)
            val customer2 = testInsert.Customers(name = "Customer 2", c = c)
            testInsert.Orders(customerId = customer1.customerId.value, c = c)
            testInsert.Orders(customerId = customer2.customerId.value, c = c)

            val ids = arrayOf(customer1.customerId.value, customer2.customerId.value)
            val results = orderSummaryRepo.apply(
                customerIds = ids,
                minTotal = null,
                minOrderCount = null,
                c = c
            )

            assertTrue(results.size >= 2)
        }
    }

    @Test
    fun testOrderSummaryWithMinTotal() {
        DuckDbTestHelper.run { c ->
            val bigSpender = testInsert.Customers(name = "Big Spender", c = c)
            testInsert.Orders(
                customerId = bigSpender.customerId.value,
                totalAmount = BigDecimal("1000.00"),
                c = c
            )

            val smallSpender = testInsert.Customers(name = "Small Spender", c = c)
            testInsert.Orders(
                customerId = smallSpender.customerId.value,
                totalAmount = BigDecimal("10.00"),
                c = c
            )

            val results = orderSummaryRepo.apply(
                customerIds = null,
                minTotal = BigDecimal("500.00"),
                minOrderCount = null,
                c = c
            )

            assertTrue(results.any { it.customerId == bigSpender.customerId })
            assertFalse(results.any { it.customerId == smallSpender.customerId })
        }
    }

    @Test
    fun testOrderSummaryWithMinOrderCount() {
        DuckDbTestHelper.run { c ->
            val frequentBuyer = testInsert.Customers(name = "Frequent Buyer", c = c)
            repeat(3) {
                testInsert.Orders(customerId = frequentBuyer.customerId.value, c = c)
            }

            val results = orderSummaryRepo.apply(
                customerIds = null,
                minTotal = null,
                minOrderCount = 3,
                c = c
            )

            assertTrue(results.any { it.customerId == frequentBuyer.customerId })
        }
    }

    @Test
    fun testProductSummary() {
        DuckDbTestHelper.run { c ->
            testInsert.Products(sku = "TEST-SKU", name = "Test Product", c = c)

            val results = productSummaryRepo.apply(c)

            assertTrue(results.isNotEmpty())
        }
    }

    @Test
    fun testDepartmentEmployeeDetails() {
        DuckDbTestHelper.run { c ->
            val dept = testInsert.Departments(
                deptCode = "DEPT1",
                deptRegion = "REGION1",
                deptName = "Test Department",
                c = c
            )
            testInsert.Employees(
                empSuffix = "A",
                deptCode = dept.deptCode,
                deptRegion = dept.deptRegion,
                empName = "Test Employee",
                c = c
            )

            val results = deptEmpDetailsRepo.apply(
                deptCode = dept.deptCode,
                deptRegion = dept.deptRegion,
                minSalary = null,
                hiredAfter = null,
                c = c
            )

            assertTrue(results.isNotEmpty())
        }
    }

    @Test
    fun testEmployeeSalaryUpdate() {
        DuckDbTestHelper.run { c ->
            val unique = System.nanoTime().toString().takeLast(6)
            val dept = testInsert.Departments(
                deptCode = "SAL$unique",
                deptRegion = "SR$unique",
                deptName = "Salary Test Department",
                c = c
            )
            val emp = testInsert.Employees(
                empSuffix = "B",
                deptCode = dept.deptCode,
                deptRegion = dept.deptRegion,
                empName = "Salary Test Employee",
                salary = BigDecimal("50000"),
                c = c
            )

            val updated = empSalaryUpdateRepo.apply(
                raisePercentage = null,
                newSalary = BigDecimal("55000.00"),
                empNumber = emp.empNumber,
                empSuffix = emp.empSuffix,
                c = c
            )

            assertEquals(1, updated.size)
        }
    }
}
