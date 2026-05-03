package testdb

import dev.typr.foundations.data.Json
import dev.typr.foundations.data.Uint1
import dev.typr.foundations.data.Uint2
import dev.typr.foundations.data.Uint4
import dev.typr.foundations.data.Uint8
import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types.*
import testdb.customers.*
import testdb.customer_orders.*
import testdb.order_details.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.util.Random
import java.util.UUID

/**
 * Tests for DuckDB-specific features: enums, special types, and views.
 */
class DatabaseFeaturesTest {
    private val testInsert = TestInsert(Random(1877479655))
    private val allTypesRepo = AllScalarTypesRepoImpl()
    private val customersRepo = CustomersRepoImpl()
    private val customerOrdersViewRepo = CustomerOrdersViewRepoImpl()
    private val orderDetailsViewRepo = OrderDetailsViewRepoImpl()

    // ==================== Enum Tests ====================

    @Test
    fun testPriorityEnumValues() {
        DuckDbTestHelper.run { c ->
            val lowPriority = testInsert.Customers(
                name = "Low Priority",
                priority = testdb.customtypes.Defaulted.Provided(Priority.low),
                c = c
            )
            val highPriority = testInsert.Customers(
                name = "High Priority",
                priority = testdb.customtypes.Defaulted.Provided(Priority.high),
                c = c
            )
            val criticalPriority = testInsert.Customers(
                name = "Critical Priority",
                priority = testdb.customtypes.Defaulted.Provided(Priority.critical),
                c = c
            )

            assertEquals(Priority.low, lowPriority.priority)
            assertEquals(Priority.high, highPriority.priority)
            assertEquals(Priority.critical, criticalPriority.priority)
        }
    }

    @Test
    fun testEnumDSLFilter() {
        DuckDbTestHelper.run { c ->
            testInsert.Customers(name = "Low", priority = testdb.customtypes.Defaulted.Provided(Priority.low), c = c)
            testInsert.Customers(name = "High", priority = testdb.customtypes.Defaulted.Provided(Priority.high), c = c)
            testInsert.Customers(name = "Critical", priority = testdb.customtypes.Defaulted.Provided(Priority.critical), c = c)

            val highPriorityCustomers = customersRepo.select()
                .where { cust -> cust.priority().isEqual(Priority.high) }
                .toList(c)

            assertTrue(highPriorityCustomers.isNotEmpty())
            assertTrue(highPriorityCustomers.all { it.priority == Priority.high })
        }
    }

    @Test
    fun testMoodEnum() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.AllScalarTypes(
                colNotNull = "mood_test",
                colMood = Mood.happy,
                c = c
            )

            assertEquals(Mood.happy, row.colMood)

            val found = allTypesRepo.selectById(row.id, c)!!
            assertEquals(Mood.happy, found.colMood)
        }
    }

    // ==================== UUID Tests ====================

    @Test
    fun testUuidType() {
        DuckDbTestHelper.run { c ->
            val uuid = UUID.randomUUID()
            val row = testInsert.AllScalarTypes(
                colNotNull = "uuid_test",
                colUuid = uuid,
                c = c
            )

            assertEquals(uuid, row.colUuid)
        }
    }

    @Test
    fun testUuidUniqueness() {
        DuckDbTestHelper.run { c ->
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()

            val row1 = testInsert.AllScalarTypes(colNotNull = "uuid1", colUuid = uuid1, c = c)
            val row2 = testInsert.AllScalarTypes(colNotNull = "uuid2", colUuid = uuid2, c = c)

            assertNotEquals(row1.colUuid, row2.colUuid)
        }
    }

    // ==================== JSON Tests ====================

    @Test
    fun testJsonType() {
        DuckDbTestHelper.run { c ->
            val json = Json("{\"name\": \"test\", \"values\": [1, 2, 3], \"nested\": {\"key\": \"value\"}}")
            val row = testInsert.AllScalarTypes(
                colNotNull = "json_test",
                colJson = json,
                c = c
            )

            assertNotNull(row.colJson)
            assertTrue(row.colJson!!.value.contains("name"))
            assertTrue(row.colJson!!.value.contains("nested"))
        }
    }

    // ==================== Date/Time Tests ====================

    @Test
    fun testTimestampWithTimezone() {
        DuckDbTestHelper.run { c ->
            val timestamptz = OffsetDateTime.of(2025, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(-5)).toInstant()
            val row = testInsert.AllScalarTypes(
                colNotNull = "timestamptz_test",
                colTimestamptz = timestamptz,
                c = c
            )

            assertNotNull(row.colTimestamptz)
        }
    }

    @Test
    fun testIntervalType() {
        DuckDbTestHelper.run { c ->
            val interval = Duration.ofDays(30).plusHours(12)
            val row = testInsert.AllScalarTypes(
                colNotNull = "interval_test",
                colInterval = interval,
                c = c
            )

            assertNotNull(row.colInterval)
        }
    }

    // ==================== Large Integer Tests ====================

    @Test
    fun testHugeintType() {
        DuckDbTestHelper.run { c ->
            val hugeValue = BigInteger("170141183460469231731687303715884105727")
            val row = testInsert.AllScalarTypes(
                colNotNull = "hugeint_test",
                colHugeint = hugeValue,
                c = c
            )

            assertNotNull(row.colHugeint)
        }
    }

    @Test
    fun testUnsignedIntegerTypes() {
        DuckDbTestHelper.run { c ->
            val row = testInsert.AllScalarTypes(
                colNotNull = "unsigned_test",
                colUtinyint = Uint1(255),
                colUsmallint = Uint2(65535),
                colUinteger = Uint4(4294967295L),
                colUbigint = Uint8(BigInteger("18446744073709551615")),
                c = c
            )

            assertEquals(Uint1(255), row.colUtinyint)
            assertEquals(Uint2(65535), row.colUsmallint)
            assertEquals(Uint4(4294967295L), row.colUinteger)
        }
    }

    // ==================== View Tests ====================

    @Test
    fun testCustomerOrdersView() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(
                name = "View Test Customer",
                c = c
            )
            testInsert.Orders(
                customerId = customer.customerId.value,
                totalAmount = BigDecimal("199.99"),
                c = c
            )

            val viewResults = customerOrdersViewRepo.select().toList(c)

            assertTrue(viewResults.isNotEmpty())
            val customerView = viewResults.find { it.customerId == customer.customerId.value }
            assertNotNull(customerView)
            assertEquals("View Test Customer", customerView!!.customerName)
        }
    }

    @Test
    fun testOrderDetailsView() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(name = "Order Customer", c = c)
            val product = testInsert.Products(
                sku = "SKU-VIEW",
                name = "View Test Product",
                c = c
            )
            val order = testInsert.Orders(customerId = customer.customerId.value, c = c)
            testInsert.OrderItems(
                orderId = order.orderId.value,
                productId = product.productId.value,
                quantity = testdb.customtypes.Defaulted.Provided(3),
                unitPrice = BigDecimal("25.00"),
                c = c
            )

            val viewResults = orderDetailsViewRepo.select().toList(c)

            assertTrue(viewResults.isNotEmpty())
            val orderDetail = viewResults.find { it.orderId == order.orderId.value }
            assertNotNull(orderDetail)
            assertEquals("View Test Product", orderDetail!!.productName)
            assertEquals(3, orderDetail.quantity)
        }
    }

    // ==================== Default Value Tests ====================

    @Test
    fun testEnumDefaultValue() {
        DuckDbTestHelper.run { c ->
            val customer = testInsert.Customers(name = "Default Priority", c = c)

            assertEquals(Priority.medium, customer.priority)
        }
    }
}
