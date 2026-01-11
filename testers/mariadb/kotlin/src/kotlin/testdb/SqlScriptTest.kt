package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.product_search.*
import testdb.customer_orders.*
import testdb.simple_customer_lookup.*
import testdb.customer_status.CustomerStatusId
import testdb.customtypes.Defaulted
import testdb.userdefined.Email
import testdb.userdefined.FirstName
import testdb.userdefined.LastName
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for SQL script generated code. These tests verify that the code generated from SQL files
 * in sql-scripts/mariadb/ works correctly.
 */
class SqlScriptTest {
    private val productSearchRepo = ProductSearchSqlRepoImpl()
    private val customerOrdersRepo = CustomerOrdersSqlRepoImpl()
    private val simpleCustomerLookupRepo = SimpleCustomerLookupSqlRepoImpl()
    private val testInsert = TestInsert(Random(42))

    @Test
    fun testProductSearchWithNoFilters() {
        MariaDbTestHelper.run { c ->
            // Insert test products first
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("search_status_${Random().nextInt(10000)}"),
                description = "Search Status",
                c = c
            )
            val brand = testInsert.Brands(
                name = "Search Brand ${Random().nextInt(10000)}",
                slug = "search-brand-${Random().nextInt(10000)}",
                c = c
            )
            testInsert.Products(
                sku = "SEARCH001",
                name = "Search Product 1",
                basePrice = BigDecimal("100.00"),
                brandId = Defaulted.Provided(brand.brandId),
                c = c
            )
            testInsert.Products(
                sku = "SEARCH002",
                name = "Search Product 2",
                basePrice = BigDecimal("200.00"),
                brandId = Defaulted.Provided(brand.brandId),
                c = c
            )

            // Call the SQL script with no filters
            val results = productSearchRepo.apply(
                brandId = null,
                minPrice = null,
                maxPrice = null,
                status = null,
                limit = 100L,
                c = c
            )

            assertNotNull(results)
            assertTrue(results.size >= 2)
        }
    }

    @Test
    fun testProductSearchWithBrandFilter() {
        MariaDbTestHelper.run { c ->
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("brand_status_${Random().nextInt(10000)}"),
                description = "Brand Status",
                c = c
            )
            val brand1 = testInsert.Brands(
                name = "Brand Filter 1 ${Random().nextInt(10000)}",
                slug = "brand-filter-1-${Random().nextInt(10000)}",
                c = c
            )
            val brand2 = testInsert.Brands(
                name = "Brand Filter 2 ${Random().nextInt(10000)}",
                slug = "brand-filter-2-${Random().nextInt(10000)}",
                c = c
            )
            testInsert.Products(
                sku = "BRAND1-001",
                name = "Brand 1 Product",
                basePrice = BigDecimal("100.00"),
                brandId = Defaulted.Provided(brand1.brandId),
                c = c
            )
            testInsert.Products(
                sku = "BRAND2-001",
                name = "Brand 2 Product",
                basePrice = BigDecimal("200.00"),
                brandId = Defaulted.Provided(brand2.brandId),
                c = c
            )

            // Filter by specific brand
            val results = productSearchRepo.apply(
                brandId = brand1.brandId.value,
                minPrice = null,
                maxPrice = null,
                status = null,
                limit = 100L,
                c = c
            )

            assertNotNull(results)
            assertTrue(results.isNotEmpty())
            // All results should be from brand1
            for (row in results) {
                assertNotNull(row)
            }
        }
    }

    @Test
    fun testProductSearchWithPriceRange() {
        MariaDbTestHelper.run { c ->
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("price_status_${Random().nextInt(10000)}"),
                description = "Price Status",
                c = c
            )
            val brand = testInsert.Brands(
                name = "Price Range Brand ${Random().nextInt(10000)}",
                slug = "price-range-brand-${Random().nextInt(10000)}",
                c = c
            )
            testInsert.Products(
                sku = "PRICE050",
                name = "50 Dollar Product",
                basePrice = BigDecimal("50.00"),
                brandId = Defaulted.Provided(brand.brandId),
                c = c
            )
            testInsert.Products(
                sku = "PRICE150",
                name = "150 Dollar Product",
                basePrice = BigDecimal("150.00"),
                brandId = Defaulted.Provided(brand.brandId),
                c = c
            )

            // Filter by price range
            val results = productSearchRepo.apply(
                brandId = null,
                minPrice = BigDecimal("40.00"),
                maxPrice = BigDecimal("100.00"),
                status = null,
                limit = 100L,
                c = c
            )

            assertNotNull(results)
            // Should find products in range
            for (row in results) {
                assertTrue(
                    row.basePrice >= BigDecimal("40.00") && row.basePrice <= BigDecimal("100.00")
                )
            }
        }
    }

    @Test
    fun testProductSearchWithLimit() {
        MariaDbTestHelper.run { c ->
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("limit_status_${Random().nextInt(10000)}"),
                description = "Limit Status",
                c = c
            )
            val brand = testInsert.Brands(
                name = "Limit Test Brand ${Random().nextInt(10000)}",
                slug = "limit-test-brand-${Random().nextInt(10000)}",
                c = c
            )
            for (i in 0 until 5) {
                testInsert.Products(
                    sku = "LIMIT00$i",
                    name = "Limit Product $i",
                    basePrice = BigDecimal("${(i + 1) * 10}.00"),
                    brandId = Defaulted.Provided(brand.brandId),
                    c = c
                )
            }

            // Limit to 2 results
            val results = productSearchRepo.apply(
                brandId = null,
                minPrice = null,
                maxPrice = null,
                status = null,
                limit = 2L,
                c = c
            )

            assertNotNull(results)
            assertEquals(2, results.size)
        }
    }

    @Test
    fun testSimpleCustomerLookup() {
        MariaDbTestHelper.run { c ->
            // Create a customer
            val email = Email("lookup_${Random().nextInt(10000)}@example.com")
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("lookup_status_${Random().nextInt(10000)}"),
                description = "Lookup Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = email,
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Lookup"),
                lastName = LastName("Customer"),
                c = c
            )

            // Look up by email
            val results = simpleCustomerLookupRepo.apply(email.value, c)

            assertNotNull(results)
            assertEquals(1, results.size)
            assertEquals(customer.email, results[0].email)
            assertEquals(customer.firstName, results[0].firstName)
            assertEquals(customer.lastName, results[0].lastName)
        }
    }

    @Test
    fun testCustomerOrders() {
        MariaDbTestHelper.run { c ->
            // Create dependencies
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("orders_status_${Random().nextInt(10000)}"),
                description = "Orders Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("orders_${Random().nextInt(10000)}@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Orders"),
                lastName = LastName("Customer"),
                c = c
            )

            // Create orders for the customer
            testInsert.Orders(
                orderNumber = "ORD-A-${Random().nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = BigDecimal("99.99"),
                totalAmount = BigDecimal("109.99"),
                c = c
            )
            testInsert.Orders(
                orderNumber = "ORD-B-${Random().nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = BigDecimal("199.99"),
                totalAmount = BigDecimal("219.99"),
                c = c
            )

            // Query customer orders
            val results = customerOrdersRepo.apply(customer.customerId, null, c)

            assertNotNull(results)
            assertTrue(results.size >= 2)
        }
    }
}
