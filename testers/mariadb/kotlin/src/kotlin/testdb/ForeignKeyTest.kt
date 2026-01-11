package testdb

import dev.typr.foundations.data.Json
import org.junit.Assert.*
import org.junit.Test
import testdb.customer_status.*
import testdb.customers.*
import testdb.orders.*
import testdb.customtypes.Defaulted.UseDefault
import testdb.userdefined.Email
import testdb.userdefined.FirstName
import testdb.userdefined.LastName
import java.math.BigDecimal
import java.util.Random

/**
 * Tests for foreign key relationships in the MariaDB ordering system. Tests type-safe FK
 * references.
 */
class ForeignKeyTest {
    private val customerStatusRepo = CustomerStatusRepoImpl()
    private val customersRepo = CustomersRepoImpl()
    private val ordersRepo = OrdersRepoImpl()
    private val testInsert = TestInsert(Random(42))

    @Test
    fun testCustomerStatusInsert() {
        MariaDbTestHelper.run { c ->
            val status = CustomerStatusRowUnsaved(
                CustomerStatusId("active_test"),
                "Active Test Status",
                UseDefault()
            )
            val inserted = customerStatusRepo.insert(status, c)

            assertNotNull(inserted)
            assertEquals(CustomerStatusId("active_test"), inserted.statusCode)
            assertEquals("Active Test Status", inserted.description)
        }
    }

    @Test
    fun testCustomerWithForeignKeyToStatus() {
        MariaDbTestHelper.run { c ->
            // First create a customer status
            val status = customerStatusRepo.insert(
                CustomerStatusRowUnsaved(
                    CustomerStatusId("verified"),
                    "Verified Customer",
                    UseDefault()
                ),
                c
            )

            // Create a customer with FK to the status - use short constructor
            val customer = CustomersRowUnsaved(
                Email("test@example.com"),
                byteArrayOf(1, 2, 3, 4),
                FirstName("John"),
                LastName("Doe")
            )

            val insertedCustomer = customersRepo.insert(customer, c)

            assertNotNull(insertedCustomer)
            assertNotNull(insertedCustomer.customerId)
            assertEquals(Email("test@example.com"), insertedCustomer.email)
            assertEquals(FirstName("John"), insertedCustomer.firstName)
            assertEquals(LastName("Doe"), insertedCustomer.lastName)
        }
    }

    @Test
    fun testOrderWithForeignKeyToCustomer() {
        MariaDbTestHelper.run { c ->
            // Create customer status first
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("test_status"),
                description = "Test Status",
                c = c
            )

            // Create a customer
            val customer = testInsert.Customers(
                email = Email("test@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Test"),
                lastName = LastName("User"),
                c = c
            )

            // Create an order with FK to customer - use short constructor
            val order = OrdersRowUnsaved(
                "ORD-001",
                customer.customerId, // FK to customer
                BigDecimal("89.99"),
                BigDecimal("99.99")
            )

            val insertedOrder = ordersRepo.insert(order, c)

            assertNotNull(insertedOrder)
            assertNotNull(insertedOrder.orderId)
            assertEquals("ORD-001", insertedOrder.orderNumber)
            assertEquals(customer.customerId, insertedOrder.customerId)
        }
    }

    @Test
    fun testOrderLookupByCustomer() {
        MariaDbTestHelper.run { c ->
            // Create dependencies
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("lookup_status"),
                description = "Lookup Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("lookup@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Lookup"),
                lastName = LastName("User"),
                c = c
            )

            // Create multiple orders for the same customer
            val order1 = testInsert.Orders(orderNumber = "ORD-L1", customerId = customer.customerId, subtotal = BigDecimal("50.00"), totalAmount = BigDecimal("55.00"), c = c)
            val order2 = testInsert.Orders(orderNumber = "ORD-L2", customerId = customer.customerId, subtotal = BigDecimal("75.00"), totalAmount = BigDecimal("80.00"), c = c)

            // Verify we can find orders
            val foundOrder1 = ordersRepo.selectById(order1.orderId, c)
            val foundOrder2 = ordersRepo.selectById(order2.orderId, c)

            assertNotNull(foundOrder1)
            assertNotNull(foundOrder2)
            assertEquals(customer.customerId, foundOrder1!!.customerId)
            assertEquals(customer.customerId, foundOrder2!!.customerId)
        }
    }

    @Test
    fun testTypeSafeForeignKeyIds() {
        MariaDbTestHelper.run { c ->
            // Demonstrate that IDs are type-safe
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("typesafe_status"),
                description = "Type Safe Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("typesafe@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("TypeSafe"),
                lastName = LastName("User"),
                c = c
            )
            val order = testInsert.Orders(orderNumber = "ORD-TS", customerId = customer.customerId, subtotal = BigDecimal("100.00"), totalAmount = BigDecimal("110.00"), c = c)

            // These are all different types and cannot be confused
            val statusId: CustomerStatusId = status.statusCode
            val customerId: CustomersId = customer.customerId
            val orderId: OrdersId = order.orderId

            // Can't accidentally pass wrong ID type (would be compile error):
            // ordersRepo.selectById(customerId, c)  // Compile error!
            // customersRepo.selectById(orderId, c)  // Compile error!

            // Correct usage
            val foundOrder = ordersRepo.selectById(orderId, c)
            assertNotNull(foundOrder)
        }
    }

    @Test
    fun testCustomerUpdateStatus() {
        MariaDbTestHelper.run { c ->
            // Create initial customer
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("update_status_${Random().nextInt(10000)}"),
                description = "Update Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("update_${Random().nextInt(10000)}@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Update"),
                lastName = LastName("User"),
                c = c
            )

            // Create a new status
            val newStatus = customerStatusRepo.insert(
                CustomerStatusRowUnsaved(
                    CustomerStatusId("premium"),
                    "Premium Customer",
                    UseDefault()
                ),
                c
            )

            // Update customer to use new status
            val updated = customer.copy(status = newStatus.statusCode)
            customersRepo.update(updated, c)

            // Verify update
            val found = customersRepo.selectById(customer.customerId, c)!!
            assertEquals(newStatus.statusCode, found.status)
        }
    }

    @Test
    fun testOptionalFieldsOnCustomer() {
        MariaDbTestHelper.run { c ->
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("optional_status_${Random().nextInt(10000)}"),
                description = "Optional Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("optional_${Random().nextInt(10000)}@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Optional"),
                lastName = LastName("User"),
                c = c
            )

            // Optional fields should be null by default
            assertNull(customer.phone)
            assertNull(customer.preferences)
            assertNull(customer.notes)
            assertNull(customer.lastLoginAt)

            // Update with values
            val updated = customer.copy(
                phone = "+1-555-1234",
                preferences = Json("""{"theme": "dark"}"""),
                notes = "VIP customer"
            )

            customersRepo.update(updated, c)

            val found = customersRepo.selectById(customer.customerId, c)!!
            assertEquals("+1-555-1234", found.phone)
            assertEquals(Json("""{"theme": "dark"}"""), found.preferences)
            assertEquals("VIP customer", found.notes)
        }
    }
}
