package com.example.events

import com.example.events.common.Money
import com.example.events.precisetypes.Decimal10_2
import com.example.events.precisetypes.Decimal18_4
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class JsonSerializationTest {

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(Jdk8Module())
        .registerModule(JavaTimeModule())

    @Test
    fun testCustomerOrderRoundTrip() {
        val order = CustomerOrder(
            orderId = OrderId.valueOf("order-123"),
            customerId = CustomerId.valueOf(456L),
            email = Email.valueOf("test@example.com"),
            amount = 1000L
        )

        val json = mapper.writeValueAsString(order)
        val deserialized = mapper.readValue(json, CustomerOrder::class.java)

        assertEquals(order.orderId.unwrap(), deserialized.orderId.unwrap())
        assertEquals(order.customerId.unwrap(), deserialized.customerId.unwrap())
        assertEquals(order.email!!.unwrap(), deserialized.email!!.unwrap())
        assertEquals(order.amount, deserialized.amount)
    }

    @Test
    fun testOrderPlacedRoundTrip() {
        val event = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 123L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
            placedAt = Instant.parse("2024-01-15T10:30:00Z"),
            items = listOf("item1", "item2"),
            shippingAddress = "123 Main St"
        )

        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, OrderPlaced::class.java)

        assertEquals(event.orderId, deserialized.orderId)
        assertEquals(event.customerId, deserialized.customerId)
        assertEquals(0, event.totalAmount.decimalValue().compareTo(deserialized.totalAmount.decimalValue()))
        assertEquals(event.items, deserialized.items)
        assertEquals(event.placedAt, deserialized.placedAt)
        assertEquals(event.shippingAddress, deserialized.shippingAddress)
    }

    @Test
    fun testAddressRoundTrip() {
        val address = Address(
            street = "123 Main St",
            city = "Springfield",
            postalCode = "62701",
            country = "US"
        )

        val json = mapper.writeValueAsString(address)
        val deserialized = mapper.readValue(json, Address::class.java)

        assertEquals(address.street, deserialized.street)
        assertEquals(address.city, deserialized.city)
        assertEquals(address.postalCode, deserialized.postalCode)
        assertEquals(address.country, deserialized.country)
    }

    @Test
    fun testMoneyRoundTrip() {
        val money = Money(
            amount = Decimal18_4.unsafeForce(BigDecimal("123.45")),
            currency = "USD"
        )

        val json = mapper.writeValueAsString(money)
        val deserialized = mapper.readValue(json, Money::class.java)

        assertEquals(0, money.amount.decimalValue().compareTo(deserialized.amount.decimalValue()))
        assertEquals(money.currency, deserialized.currency)
    }

    @Test
    fun testEnumRoundTrip() {
        val status = OrderStatus.SHIPPED

        val json = mapper.writeValueAsString(status)
        val deserialized = mapper.readValue(json, OrderStatus::class.java)

        assertEquals(status, deserialized)
    }

    @Test
    fun testInvoiceWithNestedRecords() {
        val invoice = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 456L,
            total = Money(
                amount = Decimal18_4.unsafeForce(BigDecimal("500.00")),
                currency = "EUR"
            ),
            issuedAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        val json = mapper.writeValueAsString(invoice)
        val deserialized = mapper.readValue(json, Invoice::class.java)

        assertEquals(invoice.invoiceId, deserialized.invoiceId)
        assertEquals(invoice.customerId, deserialized.customerId)
        assertEquals(0, invoice.total.amount.decimalValue().compareTo(deserialized.total.amount.decimalValue()))
        assertEquals(invoice.total.currency, deserialized.total.currency)
        assertEquals(invoice.issuedAt, deserialized.issuedAt)
    }

    @Test
    fun testTreeNodeRecursive() {
        val leaf = TreeNode(value = "leaf", left = null, right = null)
        val root = TreeNode(value = "root", left = leaf, right = null)

        val json = mapper.writeValueAsString(root)
        val deserialized = mapper.readValue(json, TreeNode::class.java)

        assertEquals(root.value, deserialized.value)
        assertEquals("leaf", deserialized.left!!.value)
        assertNull(deserialized.right)
    }

    @Test
    fun testLinkedListNode() {
        val tail = LinkedListNode(value = 3, next = null)
        val middle = LinkedListNode(value = 2, next = tail)
        val head = LinkedListNode(value = 1, next = middle)

        val json = mapper.writeValueAsString(head)
        val deserialized = mapper.readValue(json, LinkedListNode::class.java)

        assertEquals(1, deserialized.value)
        assertEquals(2, deserialized.next!!.value)
        assertEquals(3, deserialized.next!!.next!!.value)
        assertNull(deserialized.next!!.next!!.next)
    }

    @Test
    fun testOrderUpdatedWithNestedAddress() {
        val address = Address(
            street = "456 Test St",
            city = "TestCity",
            postalCode = "12345",
            country = "TC"
        )
        val event = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.PENDING,
            newStatus = OrderStatus.SHIPPED,
            updatedAt = Instant.now(),
            shippingAddress = address
        )

        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, OrderUpdated::class.java)

        assertEquals(event.orderId, deserialized.orderId)
        assertEquals(event.previousStatus, deserialized.previousStatus)
        assertEquals(event.newStatus, deserialized.newStatus)
        assertEquals(address.street, deserialized.shippingAddress!!.street)
    }

    @Test
    fun testOrderCancelledRoundTrip() {
        val event = OrderCancelled(
            orderId = UUID.randomUUID(),
            customerId = 789L,
            reason = "Customer request",
            cancelledAt = Instant.now(),
            refundAmount = null
        )

        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, OrderCancelled::class.java)

        assertEquals(event.orderId, deserialized.orderId)
        assertEquals(event.customerId, deserialized.customerId)
        assertEquals(event.reason, deserialized.reason)
        assertNull(deserialized.refundAmount)
    }

    @Test
    fun testAllEnumValues() {
        for (status in OrderStatus.entries) {
            val json = mapper.writeValueAsString(status)
            val deserialized = mapper.readValue(json, OrderStatus::class.java)
            assertEquals(status, deserialized)
        }
    }
}
