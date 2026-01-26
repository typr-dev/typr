package com.example.events

import com.example.events.common.Money
import com.example.events.precisetypes.{Decimal10_2, Decimal18_4}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.Assert._
import org.junit.Test

import java.time.Instant
import java.util.UUID

class JsonSerializationTest {

  private val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .registerModule(new Jdk8Module())
    .registerModule(new JavaTimeModule())

  @Test
  def testCustomerOrderRoundTrip(): Unit = {
    val order = CustomerOrder(
      orderId = OrderId.valueOf("order-123"),
      customerId = CustomerId.valueOf(456L),
      email = Some(Email.valueOf("test@example.com")),
      amount = 1000L
    )

    val json = mapper.writeValueAsString(order)
    val deserialized = mapper.readValue(json, classOf[CustomerOrder])

    assertEquals(order.orderId.unwrap, deserialized.orderId.unwrap)
    assertEquals(order.customerId.unwrap, deserialized.customerId.unwrap)
    assertEquals(order.email.get.unwrap, deserialized.email.get.unwrap)
    assertEquals(order.amount, deserialized.amount)
  }

  @Test
  def testOrderPlacedRoundTrip(): Unit = {
    val event = OrderPlaced(
      orderId = UUID.randomUUID(),
      customerId = 123L,
      totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
      placedAt = Instant.parse("2024-01-15T10:30:00Z"),
      items = List("item1", "item2"),
      shippingAddress = Some("123 Main St")
    )

    val json = mapper.writeValueAsString(event)
    val deserialized = mapper.readValue(json, classOf[OrderPlaced])

    assertEquals(event.orderId, deserialized.orderId)
    assertEquals(event.customerId, deserialized.customerId)
    assertEquals(0, event.totalAmount.decimalValue.compareTo(deserialized.totalAmount.decimalValue))
    assertEquals(event.items, deserialized.items)
    assertEquals(event.placedAt, deserialized.placedAt)
    assertEquals(event.shippingAddress, deserialized.shippingAddress)
  }

  @Test
  def testAddressRoundTrip(): Unit = {
    val address = Address(
      street = "123 Main St",
      city = "Springfield",
      postalCode = "62701",
      country = "US"
    )

    val json = mapper.writeValueAsString(address)
    val deserialized = mapper.readValue(json, classOf[Address])

    assertEquals(address.street, deserialized.street)
    assertEquals(address.city, deserialized.city)
    assertEquals(address.postalCode, deserialized.postalCode)
    assertEquals(address.country, deserialized.country)
  }

  @Test
  def testMoneyRoundTrip(): Unit = {
    val money = Money(
      amount = Decimal18_4.unsafeForce(BigDecimal("123.45")),
      currency = "USD"
    )

    val json = mapper.writeValueAsString(money)
    val deserialized = mapper.readValue(json, classOf[Money])

    assertEquals(0, money.amount.decimalValue.compareTo(deserialized.amount.decimalValue))
    assertEquals(money.currency, deserialized.currency)
  }

  @Test
  def testEnumRoundTrip(): Unit = {
    val status = OrderStatus.SHIPPED

    val json = mapper.writeValueAsString(status)
    val deserialized = mapper.readValue(json, classOf[OrderStatus])

    assertEquals(status, deserialized)
  }

  @Test
  def testInvoiceWithNestedRecords(): Unit = {
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
    val deserialized = mapper.readValue(json, classOf[Invoice])

    assertEquals(invoice.invoiceId, deserialized.invoiceId)
    assertEquals(invoice.customerId, deserialized.customerId)
    assertEquals(0, invoice.total.amount.decimalValue.compareTo(deserialized.total.amount.decimalValue))
    assertEquals(invoice.total.currency, deserialized.total.currency)
    assertEquals(invoice.issuedAt, deserialized.issuedAt)
  }

  @Test
  def testTreeNodeRecursive(): Unit = {
    val leaf = TreeNode(value = "leaf", left = None, right = None)
    val root = TreeNode(value = "root", left = Some(leaf), right = None)

    val json = mapper.writeValueAsString(root)
    val deserialized = mapper.readValue(json, classOf[TreeNode])

    assertEquals(root.value, deserialized.value)
    assertTrue(deserialized.left.isDefined)
    assertEquals("leaf", deserialized.left.get.value)
    assertTrue(deserialized.right.isEmpty)
  }

  @Test
  def testLinkedListNode(): Unit = {
    val tail = LinkedListNode(value = 3, next = None)
    val middle = LinkedListNode(value = 2, next = Some(tail))
    val head = LinkedListNode(value = 1, next = Some(middle))

    val json = mapper.writeValueAsString(head)
    val deserialized = mapper.readValue(json, classOf[LinkedListNode])

    assertEquals(Integer.valueOf(1), deserialized.value)
    assertEquals(Integer.valueOf(2), deserialized.next.get.value)
    assertEquals(Integer.valueOf(3), deserialized.next.get.next.get.value)
    assertTrue(deserialized.next.get.next.get.next.isEmpty)
  }

  @Test
  def testOrderUpdatedRoundTrip(): Unit = {
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
      updatedAt = Instant.parse("2024-01-15T10:30:00Z"),
      shippingAddress = Some(address)
    )

    val json = mapper.writeValueAsString(event)
    val deserialized = mapper.readValue(json, classOf[OrderUpdated])

    assertEquals(event.orderId, deserialized.orderId)
    assertEquals(event.previousStatus, deserialized.previousStatus)
    assertEquals(event.newStatus, deserialized.newStatus)
    assertEquals(event.updatedAt, deserialized.updatedAt)
    assertEquals(address.street, deserialized.shippingAddress.get.street)
  }

  @Test
  def testOrderCancelledRoundTrip(): Unit = {
    val event = OrderCancelled(
      orderId = UUID.randomUUID(),
      customerId = 789L,
      reason = Some("Customer request"),
      cancelledAt = Instant.now(),
      refundAmount = None
    )

    val json = mapper.writeValueAsString(event)
    val deserialized = mapper.readValue(json, classOf[OrderCancelled])

    assertEquals(event.orderId, deserialized.orderId)
    assertEquals(event.customerId, deserialized.customerId)
    assertEquals(event.reason, deserialized.reason)
    assertTrue(deserialized.refundAmount.isEmpty)
  }

  @Test
  def testAllEnumValuesRoundTrip(): Unit = {
    for (status <- OrderStatus.All) {
      val json = mapper.writeValueAsString(status)
      val deserialized = mapper.readValue(json, classOf[OrderStatus])
      assertEquals(status, deserialized)
    }
  }
}
