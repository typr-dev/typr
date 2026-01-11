package testdb

import dev.typr.foundations.data.maria.Inet4
import dev.typr.foundations.data.maria.Inet6
import org.junit.Assert.*
import org.junit.Test
import testdb.customer_status.CustomerStatusId
import testdb.userdefined.Email
import testdb.userdefined.FirstName
import testdb.userdefined.LastName
import java.time.Year
import java.util.Random

/** Tests for TestInsert functionality - automatic random data generation for testing. */
class TestInsertTest {
    private val testInsert = TestInsert(Random(42))

    @Test
    fun testMariatestIdentityInsert() {
        MariaDbTestHelper.run { c ->
            // TestInsert generates random data for required fields
            val row = testInsert.MariatestIdentity(
                name = "Test Name",
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.id)
            assertEquals("Test Name", row.name)
        }
    }

    @Test
    fun testMariatestIdentityWithRandomName() {
        MariaDbTestHelper.run { c ->
            // Use random to generate a name
            val randomName = "Random_${Random().nextInt(10000)}"
            val row = testInsert.MariatestIdentity(
                name = randomName,
                c = c
            )

            assertNotNull(row)
            assertEquals(randomName, row.name)
        }
    }

    @Test
    fun testMariatestInsert() {
        MariaDbTestHelper.run { c ->
            // Mariatest requires additional parameters for complex types
            val row = testInsert.Mariatest(
                bitCol = byteArrayOf(0xFF.toByte()),
                bit1Col = byteArrayOf(0x01.toByte()),
                charCol = "testchar  ",
                varcharCol = "testvarchar",
                tinytextCol = "tinytext",
                textCol = "text",
                mediumtextCol = "mediumtext",
                longtextCol = "longtext",
                binaryCol = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                varbinaryCol = byteArrayOf(1, 2, 3),
                tinyblobCol = byteArrayOf(4, 5, 6),
                blobCol = byteArrayOf(7, 8, 9),
                mediumblobCol = byteArrayOf(10, 11, 12),
                longblobCol = byteArrayOf(13, 14, 15),
                yearCol = Year.of(2025),
                setCol = XYZSet.fromString("x,y"),
                inet4Col = Inet4("192.168.1.1"),
                inet6Col = Inet6("::1"),
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.intCol)
        }
    }

    @Test
    fun testCustomerStatusInsert() {
        MariaDbTestHelper.run { c ->
            val row = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("test_status_${Random().nextInt(10000)}"),
                description = "Test Status Description",
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.statusCode)
            assertNotNull(row.description)
        }
    }

    @Test
    fun testCustomersInsertWithForeignKey() {
        MariaDbTestHelper.run { c ->
            // First create the required customer status
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("cust_status_${Random().nextInt(10000)}"),
                description = "Customer Status",
                c = c
            )

            // Now create a customer - requires password_hash
            val customer = testInsert.Customers(
                email = Email("test_${Random().nextInt(10000)}@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("John"),
                lastName = LastName("Doe"),
                c = c
            )

            assertNotNull(customer)
            assertNotNull(customer.customerId)
            assertNotNull(customer.email)
        }
    }

    @Test
    fun testOrdersInsertChain() {
        MariaDbTestHelper.run { c ->
            // Create required dependencies
            val status = testInsert.CustomerStatus(
                statusCode = CustomerStatusId("order_status_${Random().nextInt(10000)}"),
                description = "Order Status",
                c = c
            )
            val customer = testInsert.Customers(
                email = Email("order_${Random().nextInt(10000)}@example.com"),
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = FirstName("Jane"),
                lastName = LastName("Smith"),
                c = c
            )

            // Create an order
            val order = testInsert.Orders(
                orderNumber = "ORD-${Random().nextInt(100000)}",
                customerId = customer.customerId,
                subtotal = java.math.BigDecimal("99.99"),
                totalAmount = java.math.BigDecimal("109.99"),
                c = c
            )

            assertNotNull(order)
            assertNotNull(order.orderId)
            assertEquals(customer.customerId, order.customerId)
        }
    }

    @Test
    fun testMariatestUniqueInsert() {
        MariaDbTestHelper.run { c ->
            val uniqueId = Random().nextInt(100000)
            val row = testInsert.MariatestUnique(
                email = Email("unique_$uniqueId@example.com"),
                code = "CODE$uniqueId",
                category = "CAT$uniqueId",
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.id)
            assertNotNull(row.email)
            assertNotNull(row.code)
            assertNotNull(row.category)
        }
    }

    @Test
    fun testMariatestnullInsert() {
        MariaDbTestHelper.run { c ->
            // Mariatestnull has all nullable columns - use short constructor
            val row = testInsert.Mariatestnull(c = c)

            assertNotNull(row)
            // Fields should be null by default
        }
    }

    @Test
    fun testMultipleInserts() {
        MariaDbTestHelper.run { c ->
            // Insert multiple rows using same TestInsert instance
            val row1 = testInsert.MariatestIdentity(name = "Name1", c = c)
            val row2 = testInsert.MariatestIdentity(name = "Name2", c = c)
            val row3 = testInsert.MariatestIdentity(name = "Name3", c = c)

            assertNotEquals(row1.id, row2.id)
            assertNotEquals(row2.id, row3.id)
            assertNotEquals(row1.id, row3.id)
        }
    }

    @Test
    fun testInsertWithDifferentNames() {
        MariaDbTestHelper.run { c ->
            // Create rows with different names
            val row1 = testInsert.MariatestIdentity(name = "Alpha", c = c)
            val row2 = testInsert.MariatestIdentity(name = "Beta", c = c)

            assertEquals("Alpha", row1.name)
            assertEquals("Beta", row2.name)
        }
    }
}
