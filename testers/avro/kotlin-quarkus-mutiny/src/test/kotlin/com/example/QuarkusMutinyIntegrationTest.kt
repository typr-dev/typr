package com.example

import com.example.events.*
import com.example.events.common.Money
import com.example.events.precisetypes.Decimal10_2
import com.example.events.precisetypes.Decimal18_4
import com.example.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.smallrye.mutiny.Uni
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Integration tests for Kotlin Quarkus/Mutiny Avro code generation.
 *
 * Tests data classes, union types, Result ADT, and UserService interface with Mutiny.
 */
class QuarkusMutinyIntegrationTest {

    companion object {
        private lateinit var objectMapper: ObjectMapper

        @BeforeClass
        @JvmStatic
        fun setup() {
            objectMapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .registerModule(JavaTimeModule())
                .registerModule(Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    // ========== Data Class Tests ==========

    @Test
    fun testOrderPlacedDataClass() {
        val order = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 12345L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
            placedAt = Instant.now(),
            items = listOf("item-1", "item-2", "item-3"),
            shippingAddress = "123 Main St"
        )

        assertEquals(12345L, order.customerId)
        assertEquals(3, order.items.size)
        assertEquals("123 Main St", order.shippingAddress)
    }

    @Test
    fun testOrderPlacedWithNullOptionalField() {
        val order = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 12345L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("50.00")),
            placedAt = Instant.now(),
            items = listOf("item-a"),
            shippingAddress = null
        )

        assertNull(order.shippingAddress)
    }

    @Test
    fun testOrderUpdatedWithNestedRecord() {
        val address = Address("456 Oak Ave", "Springfield", "12345", "US")
        val order = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.PENDING,
            newStatus = OrderStatus.SHIPPED,
            updatedAt = Instant.now(),
            shippingAddress = address
        )

        assertEquals(OrderStatus.PENDING, order.previousStatus)
        assertEquals(OrderStatus.SHIPPED, order.newStatus)
        assertNotNull(order.shippingAddress)
        assertEquals("Springfield", order.shippingAddress?.city)
    }

    @Test
    fun testOrderUpdatedWithNullNestedRecord() {
        val order = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.CONFIRMED,
            newStatus = OrderStatus.CANCELLED,
            updatedAt = Instant.now(),
            shippingAddress = null
        )

        assertNull(order.shippingAddress)
    }

    @Test
    fun testAllEnumValues() {
        for (status in OrderStatus.entries) {
            val order = OrderUpdated(
                orderId = UUID.randomUUID(),
                previousStatus = status,
                newStatus = status,
                updatedAt = Instant.now(),
                shippingAddress = null
            )
            assertEquals(status, order.previousStatus)
            assertEquals(status, order.newStatus)
        }
    }

    @Test
    fun testInvoiceWithMoneyRef() {
        val total = Money(Decimal18_4.unsafeForce(BigDecimal("1234.5678")), "USD")
        val invoice = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 12345L,
            total = total,
            issuedAt = Instant.now()
        )

        assertEquals(12345L, invoice.customerId)
        assertEquals("USD", invoice.total.currency)
    }

    @Test
    fun testTreeNodeSimple() {
        val leaf = TreeNode("leaf", null, null)

        assertEquals("leaf", leaf.value)
        assertNull(leaf.left)
        assertNull(leaf.right)
    }

    @Test
    fun testTreeNodeRecursive() {
        val leftChild = TreeNode("left-child", null, null)
        val rightChild = TreeNode("right-child", null, null)
        val root = TreeNode("root", leftChild, rightChild)

        assertEquals("root", root.value)
        assertEquals("left-child", root.left?.value)
        assertEquals("right-child", root.right?.value)
    }

    @Test
    fun testTreeNodeDeeplyNested() {
        val level3 = TreeNode("level3", null, null)
        val level2 = TreeNode("level2", level3, null)
        val level1 = TreeNode("level1", level2, null)
        val root = TreeNode("root", level1, null)

        assertEquals("root", root.value)
        assertEquals("level1", root.left?.value)
        assertEquals("level2", root.left?.left?.value)
        assertEquals("level3", root.left?.left?.left?.value)
        assertNull(root.left?.left?.left?.left)
    }

    @Test
    fun testLinkedListNodeSimple() {
        val single = LinkedListNode(42, null)

        assertEquals(42, single.value)
        assertNull(single.next)
    }

    @Test
    fun testLinkedListNodeChain() {
        val node3 = LinkedListNode(3, null)
        val node2 = LinkedListNode(2, node3)
        val node1 = LinkedListNode(1, node2)

        assertEquals(1, node1.value)
        assertEquals(2, node1.next?.value)
        assertEquals(3, node1.next?.next?.value)
        assertNull(node1.next?.next?.next)
    }

    // ========== Union Type Tests ==========

    @Test
    fun testStringOrIntOrBooleanWithString() {
        val value = StringOrIntOrBoolean.of("hello")

        assertTrue(value.isString())
        assertFalse(value.isInt())
        assertFalse(value.isBoolean())
        assertEquals("hello", value.asString())
    }

    @Test
    fun testStringOrIntOrBooleanWithInt() {
        val value = StringOrIntOrBoolean.of(42)

        assertTrue(value.isInt())
        assertFalse(value.isString())
        assertFalse(value.isBoolean())
        assertEquals(42, value.asInt())
    }

    @Test
    fun testStringOrIntOrBooleanWithBoolean() {
        val value = StringOrIntOrBoolean.of(true)

        assertTrue(value.isBoolean())
        assertFalse(value.isString())
        assertFalse(value.isInt())
        assertTrue(value.asBoolean())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testStringOrIntOrBooleanThrowsOnWrongType() {
        val stringValue = StringOrIntOrBoolean.of("hello")
        stringValue.asInt() // Should throw
    }

    @Test
    fun testDynamicValueWithUnions() {
        val withString = DynamicValue(
            id = "id-1",
            value = StringOrIntOrBoolean.of("test-string"),
            optionalValue = null
        )

        assertEquals("id-1", withString.id)
        assertTrue(withString.value.isString())
        assertEquals("test-string", withString.value.asString())
        assertNull(withString.optionalValue)

        val withInt = DynamicValue(
            id = "id-2",
            value = StringOrIntOrBoolean.of(123),
            optionalValue = StringOrLong.of(456L)
        )

        assertEquals("id-2", withInt.id)
        assertTrue(withInt.value.isInt())
        assertEquals(123, withInt.value.asInt())
        val optVal = withInt.optionalValue
        assertNotNull(optVal)
        assertTrue(optVal!!.isLong())
        assertEquals(456L, optVal.asLong())
    }

    // ========== Result ADT Tests ==========

    @Test
    fun testResultOk() {
        val result: Result<String, String> = Result.Ok("success")

        assertTrue(result is Result.Ok)
        assertEquals("success", (result as Result.Ok).value)
    }

    @Test
    fun testResultErr() {
        val result: Result<String, String> = Result.Err("failure")

        assertTrue(result is Result.Err)
        assertEquals("failure", (result as Result.Err).error)
    }

    @Test
    fun testResultPatternMatching() {
        val ok: Result<Int, String> = Result.Ok(42)
        val err: Result<Int, String> = Result.Err("error")

        val okMessage = when (ok) {
            is Result.Ok -> "Got: ${ok.value}"
            is Result.Err -> "Error: ${ok.error}"
        }
        assertEquals("Got: 42", okMessage)

        val errMessage = when (err) {
            is Result.Ok -> "Got: ${err.value}"
            is Result.Err -> "Error: ${err.error}"
        }
        assertEquals("Error: error", errMessage)
    }

    // ========== UserService Tests with Mutiny ==========

    @Test
    fun testUserServiceImplementation() {
        val userStore = mutableMapOf<String, User>()

        val service = object : UserService {
            override fun getUser(userId: String): Uni<Result<User, UserNotFoundError>> {
                val user = userStore[userId]
                return if (user != null) {
                    Uni.createFrom().item(Result.Ok(user))
                } else {
                    Uni.createFrom().item(Result.Err(UserNotFoundError(userId, "User not found: $userId")))
                }
            }

            override fun createUser(email: String, name: String): Uni<Result<User, ValidationError>> {
                if (!email.contains("@")) {
                    return Uni.createFrom().item(Result.Err(ValidationError("email", "Invalid email format")))
                }
                val userId = UUID.randomUUID().toString()
                val user = User(userId, email, name, Instant.now())
                userStore[userId] = user
                return Uni.createFrom().item(Result.Ok(user))
            }

            override fun deleteUser(userId: String): Uni<Result<Unit, UserNotFoundError>> {
                return if (userStore.containsKey(userId)) {
                    userStore.remove(userId)
                    Uni.createFrom().item(Result.Ok(Unit))
                } else {
                    Uni.createFrom().item(Result.Err(UserNotFoundError(userId, "Cannot delete: user not found")))
                }
            }

            override fun notifyUser(userId: String, message: String): Uni<Void> {
                return Uni.createFrom().voidItem()
            }
        }

        // Test createUser success
        val createResult = service.createUser("test@example.com", "Test User").await().indefinitely()
        assertTrue(createResult is Result.Ok)
        val createdUser = (createResult as Result.Ok).value
        assertEquals("test@example.com", createdUser.email)
        assertEquals("Test User", createdUser.name)

        // Test getUser success
        val getResult = service.getUser(createdUser.id).await().indefinitely()
        assertTrue(getResult is Result.Ok)
        assertEquals(createdUser.id, (getResult as Result.Ok).value.id)

        // Test deleteUser success
        val deleteResult = service.deleteUser(createdUser.id).await().indefinitely()
        assertTrue(deleteResult is Result.Ok)

        // Test getUser after delete - should fail
        val getAfterDelete = service.getUser(createdUser.id).await().indefinitely()
        assertTrue(getAfterDelete is Result.Err)
        assertEquals(createdUser.id, (getAfterDelete as Result.Err).error.userId)
    }

    @Test
    fun testUserServiceValidationError() {
        val service = object : UserService {
            override fun getUser(userId: String): Uni<Result<User, UserNotFoundError>> =
                Uni.createFrom().item(Result.Err(UserNotFoundError(userId, "Not found")))

            override fun createUser(email: String, name: String): Uni<Result<User, ValidationError>> =
                Uni.createFrom().item(Result.Err(ValidationError("email", "Invalid")))

            override fun deleteUser(userId: String): Uni<Result<Unit, UserNotFoundError>> =
                Uni.createFrom().item(Result.Err(UserNotFoundError(userId, "Not found")))

            override fun notifyUser(userId: String, message: String): Uni<Void> =
                Uni.createFrom().voidItem()
        }

        val createResult = service.createUser("bad-email", "Test").await().indefinitely()
        assertTrue(createResult is Result.Err)
        val err = (createResult as Result.Err).error
        assertEquals("email", err.field)
        assertEquals("Invalid", err.message)
    }

    // ========== JSON Serialization Tests ==========

    @Test
    fun testOrderPlacedJsonRoundTrip() {
        val original = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 12345L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
            placedAt = Instant.parse("2024-01-15T10:30:00Z"),
            items = listOf("item-1", "item-2"),
            shippingAddress = "123 Test St"
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<OrderPlaced>(json)

        assertEquals(original.orderId, deserialized.orderId)
        assertEquals(original.customerId, deserialized.customerId)
        assertEquals(original.items, deserialized.items)
        assertEquals(original.shippingAddress, deserialized.shippingAddress)
    }

    @Test
    fun testAddressJsonRoundTrip() {
        val original = Address("123 Main St", "Springfield", "12345", "US")

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<Address>(json)

        assertEquals(original.street, deserialized.street)
        assertEquals(original.city, deserialized.city)
        assertEquals(original.postalCode, deserialized.postalCode)
        assertEquals(original.country, deserialized.country)
    }

    @Test
    fun testUserJsonRoundTrip() {
        val original = User(
            id = "user-123",
            email = "test@example.com",
            name = "Test User",
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<User>(json)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.email, deserialized.email)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.createdAt, deserialized.createdAt)
    }

    @Test
    fun testInvoiceJsonRoundTrip() {
        val original = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 67890L,
            total = Money(Decimal18_4.unsafeForce(BigDecimal("1234.5678")), "EUR"),
            issuedAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<Invoice>(json)

        assertEquals(original.invoiceId, deserialized.invoiceId)
        assertEquals(original.customerId, deserialized.customerId)
        assertEquals(original.total.currency, deserialized.total.currency)
    }

    @Test
    fun testTreeNodeJsonRoundTrip() {
        val original = TreeNode(
            value = "root",
            left = TreeNode("left", null, null),
            right = TreeNode("right", null, null)
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<TreeNode>(json)

        assertEquals(original.value, deserialized.value)
        assertEquals(original.left?.value, deserialized.left?.value)
        assertEquals(original.right?.value, deserialized.right?.value)
    }

    // ========== Data Class Equality Tests ==========

    @Test
    fun testDataClassEquality() {
        val address1 = Address("123 Main St", "Springfield", "12345", "US")
        val address2 = Address("123 Main St", "Springfield", "12345", "US")
        val address3 = Address("456 Oak Ave", "Springfield", "12345", "US")

        assertEquals(address1, address2)
        assertNotEquals(address1, address3)
        assertEquals(address1.hashCode(), address2.hashCode())
    }

    @Test
    fun testOrderEventsSealed() {
        val placed: OrderEvents = OrderPlaced(
            UUID.randomUUID(), 1L, Decimal10_2.unsafeForce(BigDecimal("10.00")),
            Instant.now(), listOf("item"), null
        )
        val cancelled: OrderEvents = OrderCancelled(
            orderId = UUID.randomUUID(),
            customerId = 1L,
            reason = "Changed my mind",
            cancelledAt = Instant.now(),
            refundAmount = null
        )

        assertTrue(placed is OrderPlaced)
        assertTrue(cancelled is OrderCancelled)

        // Pattern matching on sealed interface
        val result = when (placed) {
            is OrderPlaced -> "placed"
            is OrderCancelled -> "cancelled"
            is OrderUpdated -> "updated"
        }
        assertEquals("placed", result)
    }
}
