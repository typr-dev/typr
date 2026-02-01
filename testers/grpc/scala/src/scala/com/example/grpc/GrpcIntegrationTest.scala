package com.example.grpc

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.time.Duration
import java.time.Instant
import java.util

class GrpcIntegrationTest {

  private var server: Server = _
  private var channel: ManagedChannel = _
  private var orderClient: OrderServiceClient = _
  private var echoClient: EchoServiceClient = _

  private val testCustomer =
    Customer(CustomerId.valueOf("CUST-123"), "John Doe", "john@example.com")

  @Before
  def setUp(): Unit = {
    val serverName = InProcessServerBuilder.generateName()

    val orderImpl = new OrderService {
      override def getCustomer(request: GetCustomerRequest): GetCustomerResponse =
        GetCustomerResponse(
          Customer(CustomerId.valueOf(request.customerId), "John Doe", "john@example.com")
        )

      override def createOrder(request: CreateOrderRequest): CreateOrderResponse =
        CreateOrderResponse(request.order.orderId.unwrap, OrderStatus.ORDER_STATUS_PENDING)

      override def listOrders(request: ListOrdersRequest): util.Iterator[OrderUpdate] = {
        val updates = new util.ArrayList[OrderUpdate]()
        updates.add(OrderUpdate("ORD-1", OrderStatus.ORDER_STATUS_PENDING, Instant.ofEpochSecond(1000, 500)))
        updates.add(OrderUpdate("ORD-2", OrderStatus.ORDER_STATUS_SHIPPED, Instant.ofEpochSecond(2000, 1000)))
        updates.add(OrderUpdate("ORD-3", OrderStatus.ORDER_STATUS_DELIVERED, Instant.ofEpochSecond(3000, 0)))
        updates.iterator()
      }

      override def submitOrders(requests: util.Iterator[CreateOrderRequest]): OrderSummary =
        throw new UnsupportedOperationException()

      override def chat(requests: util.Iterator[ChatMessage]): util.Iterator[ChatMessage] =
        throw new UnsupportedOperationException()
    }

    val echoImpl = new EchoService {
      override def echoScalarTypes(request: ScalarTypes): ScalarTypes = request
      override def echoCustomer(request: Customer): Customer = request
      override def echoOrder(request: Order): Order = request
      override def echoInventory(request: Inventory): Inventory = request
      override def echoOuter(request: Outer): Outer = request
      override def echoOptionalFields(request: OptionalFields): OptionalFields = request
      override def echoWellKnownTypes(request: WellKnownTypesMessage): WellKnownTypesMessage = request
      override def echoPaymentMethod(request: PaymentMethod): PaymentMethod = request
      override def echoNotification(request: Notification): Notification = request
    }

    server = InProcessServerBuilder
      .forName(serverName)
      .directExecutor()
      .addService(new OrderServiceServer(orderImpl))
      .addService(new EchoServiceServer(echoImpl))
      .build()
      .start()

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
    orderClient = new OrderServiceClient(channel)
    echoClient = new EchoServiceClient(channel)
  }

  @After
  def tearDown(): Unit = {
    channel.shutdownNow()
    server.shutdownNow()
  }

  // ---- gRPC service tests ----

  @Test
  def testGetCustomer(): Unit = {
    val response = orderClient.getCustomer(GetCustomerRequest("CUST-123"))
    assertNotNull(response)
    assertNotNull(response.customer)
    assertEquals("CUST-123", response.customer.customerId.unwrap)
    assertEquals("John Doe", response.customer.name)
    assertEquals("john@example.com", response.customer.email)
  }

  @Test
  def testCreateOrder(): Unit = {
    val order = Order(
      OrderId.valueOf("ORD-42"),
      CustomerId.valueOf("CUST-1"),
      9999L,
      Instant.ofEpochSecond(1700000000L, 123456789)
    )

    val response = orderClient.createOrder(CreateOrderRequest(order))
    assertNotNull(response)
    assertEquals("ORD-42", response.orderId)
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, response.status)
  }

  @Test
  def testListOrders(): Unit = {
    val updates = orderClient.listOrders(ListOrdersRequest("CUST-123", 10))
    val results = new util.ArrayList[OrderUpdate]()
    updates.forEachRemaining(results.add(_))

    assertEquals(3, results.size())
    assertEquals("ORD-1", results.get(0).orderId)
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, results.get(0).status)
    assertEquals("ORD-2", results.get(1).orderId)
    assertEquals(OrderStatus.ORDER_STATUS_SHIPPED, results.get(1).status)
    assertEquals("ORD-3", results.get(2).orderId)
    assertEquals(OrderStatus.ORDER_STATUS_DELIVERED, results.get(2).status)
  }

  // ---- Echo round-trip tests ----

  @Test
  def testEchoCustomer(): Unit = {
    val parsed = echoClient.echoCustomer(testCustomer)
    assertEquals(testCustomer, parsed)
  }

  @Test
  def testEchoOrder(): Unit = {
    val order = Order(
      OrderId.valueOf("ORD-1"),
      CustomerId.valueOf("CUST-1"),
      5000L,
      Instant.ofEpochSecond(1700000000L, 123456789)
    )
    val parsed = echoClient.echoOrder(order)
    assertEquals(order.orderId, parsed.orderId)
    assertEquals(order.customerId, parsed.customerId)
    assertEquals(order.amountCents, parsed.amountCents)
    assertEquals(order.createdAt, parsed.createdAt)
  }

  // ---- Scalar types ----

  @Test
  def testEchoScalarTypes(): Unit = {
    val scalars = ScalarTypes(
      3.14,
      2.71f,
      42,
      9876543210L,
      100,
      200L,
      -50,
      -100L,
      999,
      888L,
      -777,
      -666L,
      true,
      "hello world",
      ByteString.copyFromUtf8("binary data")
    )
    val parsed = echoClient.echoScalarTypes(scalars)
    assertEquals(scalars.doubleVal, parsed.doubleVal, 0.0001)
    assertEquals(scalars.floatVal, parsed.floatVal, 0.0001f)
    assertEquals(scalars.int32Val, parsed.int32Val)
    assertEquals(scalars.int64Val, parsed.int64Val)
    assertEquals(scalars.uint32Val, parsed.uint32Val)
    assertEquals(scalars.uint64Val, parsed.uint64Val)
    assertEquals(scalars.sint32Val, parsed.sint32Val)
    assertEquals(scalars.sint64Val, parsed.sint64Val)
    assertEquals(scalars.fixed32Val, parsed.fixed32Val)
    assertEquals(scalars.fixed64Val, parsed.fixed64Val)
    assertEquals(scalars.sfixed32Val, parsed.sfixed32Val)
    assertEquals(scalars.sfixed64Val, parsed.sfixed64Val)
    assertEquals(scalars.boolVal, parsed.boolVal)
    assertEquals(scalars.stringVal, parsed.stringVal)
    assertEquals(scalars.bytesVal, parsed.bytesVal)
  }

  // ---- Enum tests ----

  @Test
  def testEnumToValueFromValueRoundTrip(): Unit = {
    for (status <- OrderStatus.values) {
      val wireValue = status.toValue
      val back = OrderStatus.fromValue(wireValue)
      assertEquals(status, back)
    }
  }

  @Test
  def testEnumForce(): Unit = {
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, OrderStatus.force("ORDER_STATUS_PENDING"))
  }

  @Test(expected = classOf[RuntimeException])
  def testEnumForceInvalid(): Unit = {
    OrderStatus.force("NONEXISTENT")
  }

  @Test(expected = classOf[IllegalArgumentException])
  def testEnumFromValueInvalid(): Unit = {
    OrderStatus.fromValue(999)
  }

  @Test
  def testPriorityEnumRoundTrip(): Unit = {
    for (p <- Priority.values) {
      val wireValue = p.toValue
      val back = Priority.fromValue(wireValue)
      assertEquals(p, back)
    }
  }

  // ---- Optional fields ----

  @Test
  def testEchoOptionalFieldsAllPresent(): Unit = {
    val opt = OptionalFields(Some("Alice"), Some(30), Some(testCustomer))
    val parsed = echoClient.echoOptionalFields(opt)
    assertEquals(Some("Alice"), parsed.name)
    assertEquals(Some(30), parsed.age)
    assertTrue(parsed.customer.isDefined)
    assertEquals("CUST-123", parsed.customer.get.customerId.unwrap)
  }

  @Test
  def testEchoOptionalFieldsAllEmpty(): Unit = {
    val opt = OptionalFields(None, None, None)
    val parsed = echoClient.echoOptionalFields(opt)
    assertEquals(None, parsed.name)
    assertEquals(None, parsed.age)
    assertEquals(None, parsed.customer)
  }

  @Test
  def testEchoOptionalFieldsPartiallyPresent(): Unit = {
    val opt = OptionalFields(Some("Bob"), None, None)
    val parsed = echoClient.echoOptionalFields(opt)
    assertEquals(Some("Bob"), parsed.name)
    assertEquals(None, parsed.age)
    assertEquals(None, parsed.customer)
  }

  // ---- Nested messages ----

  @Test
  def testEchoOuter(): Unit = {
    val outer = Outer("outer-name", Inner(42, "inner-desc"))
    val parsed = echoClient.echoOuter(outer)
    assertEquals("outer-name", parsed.name)
    assertNotNull(parsed.inner)
    assertEquals(42, parsed.inner.value)
    assertEquals("inner-desc", parsed.inner.description)
  }

  // ---- OneOf types ----

  @Test
  def testEchoPaymentMethodCreditCard(): Unit = {
    val cc = CreditCard("4111111111111111", "12/25", "123")
    val method = PaymentMethodMethod.CreditCardValue(cc)
    val pm = PaymentMethod("PAY-1", method)
    val parsed = echoClient.echoPaymentMethod(pm)
    assertEquals("PAY-1", parsed.id)
    parsed.method match {
      case PaymentMethodMethod.CreditCardValue(creditCard) =>
        assertEquals("4111111111111111", creditCard.cardNumber)
        assertEquals("12/25", creditCard.expiryDate)
        assertEquals("123", creditCard.cvv)
      case other => fail(s"Expected CreditCardValue, got $other")
    }
  }

  @Test
  def testEchoPaymentMethodBankTransfer(): Unit = {
    val bt = BankTransfer("123456789", "021000021")
    val method = PaymentMethodMethod.BankTransferValue(bt)
    val pm = PaymentMethod("PAY-2", method)
    val parsed = echoClient.echoPaymentMethod(pm)
    assertEquals("PAY-2", parsed.id)
    parsed.method match {
      case PaymentMethodMethod.BankTransferValue(bankTransfer) =>
        assertEquals("123456789", bankTransfer.accountNumber)
        assertEquals("021000021", bankTransfer.routingNumber)
      case other => fail(s"Expected BankTransferValue, got $other")
    }
  }

  @Test
  def testEchoPaymentMethodWallet(): Unit = {
    val w = Wallet("wallet-42", "Stripe")
    val method = PaymentMethodMethod.WalletValue(w)
    val pm = PaymentMethod("PAY-3", method)
    val parsed = echoClient.echoPaymentMethod(pm)
    assertEquals("PAY-3", parsed.id)
    parsed.method match {
      case PaymentMethodMethod.WalletValue(wallet) =>
        assertEquals("wallet-42", wallet.walletId)
        assertEquals("Stripe", wallet.provider)
      case other => fail(s"Expected WalletValue, got $other")
    }
  }

  @Test
  def testEchoNotificationWithEmailTarget(): Unit = {
    val notif = Notification("Hello!", Priority.PRIORITY_HIGH, NotificationTarget.Email("user@example.com"))
    val parsed = echoClient.echoNotification(notif)
    assertEquals("Hello!", parsed.message)
    assertEquals(Priority.PRIORITY_HIGH, parsed.priority)
    parsed.target match {
      case NotificationTarget.Email(email) => assertEquals("user@example.com", email)
      case other                           => fail(s"Expected Email, got $other")
    }
  }

  @Test
  def testEchoNotificationWithPhoneTarget(): Unit = {
    val notif = Notification("Alert", Priority.PRIORITY_CRITICAL, NotificationTarget.Phone("+1234567890"))
    val parsed = echoClient.echoNotification(notif)
    assertEquals("Alert", parsed.message)
    assertEquals(Priority.PRIORITY_CRITICAL, parsed.priority)
    parsed.target match {
      case NotificationTarget.Phone(phone) => assertEquals("+1234567890", phone)
      case other                           => fail(s"Expected Phone, got $other")
    }
  }

  @Test
  def testEchoNotificationWithWebhookTarget(): Unit = {
    val notif = Notification("Event", Priority.PRIORITY_LOW, NotificationTarget.WebhookUrl("https://hooks.example.com/abc"))
    val parsed = echoClient.echoNotification(notif)
    assertEquals("Event", parsed.message)
    assertEquals(Priority.PRIORITY_LOW, parsed.priority)
    parsed.target match {
      case NotificationTarget.WebhookUrl(webhookUrl) => assertEquals("https://hooks.example.com/abc", webhookUrl)
      case other                                     => fail(s"Expected WebhookUrl, got $other")
    }
  }

  // ---- Collections ----

  @Test
  def testEchoInventory(): Unit = {
    val productIds = List("PROD-1", "PROD-2", "PROD-3")
    val stockCounts = Map("PROD-1" -> 100, "PROD-2" -> 200, "PROD-3" -> 0)
    val orders = List(
      Order(OrderId.valueOf("ORD-1"), CustomerId.valueOf("CUST-1"), 1000L, Instant.ofEpochSecond(1000, 0)),
      Order(OrderId.valueOf("ORD-2"), CustomerId.valueOf("CUST-2"), 2000L, Instant.ofEpochSecond(2000, 0))
    )

    val inventory = Inventory("WH-1", productIds, stockCounts, orders)
    val parsed = echoClient.echoInventory(inventory)
    assertEquals("WH-1", parsed.warehouseId)
    assertEquals(3, parsed.productIds.size)
    assertEquals("PROD-1", parsed.productIds(0))
    assertEquals("PROD-2", parsed.productIds(1))
    assertEquals("PROD-3", parsed.productIds(2))
    assertEquals(3, parsed.stockCounts.size)
    assertEquals(100, parsed.stockCounts("PROD-1"))
    assertEquals(200, parsed.stockCounts("PROD-2"))
    assertEquals(0, parsed.stockCounts("PROD-3"))
    assertEquals(2, parsed.recentOrders.size)
    assertEquals("ORD-1", parsed.recentOrders(0).orderId.unwrap)
    assertEquals("ORD-2", parsed.recentOrders(1).orderId.unwrap)
  }

  @Test
  def testEchoInventoryEmptyCollections(): Unit = {
    val inventory = Inventory("WH-EMPTY", List.empty, Map.empty, List.empty)
    val parsed = echoClient.echoInventory(inventory)
    assertEquals("WH-EMPTY", parsed.warehouseId)
    assertTrue(parsed.productIds.isEmpty)
    assertTrue(parsed.stockCounts.isEmpty)
    assertTrue(parsed.recentOrders.isEmpty)
  }

  // ---- Well-known types ----

  @Test
  def testEchoWellKnownTypes(): Unit = {
    val msg = WellKnownTypesMessage(
      Instant.ofEpochSecond(1700000000L, 123456789),
      Duration.ofSeconds(3600, 500000000),
      Some("hello"),
      Some(42),
      Some(true)
    )
    val parsed = echoClient.echoWellKnownTypes(msg)
    assertEquals(msg.createdAt, parsed.createdAt)
    assertEquals(msg.ttl, parsed.ttl)
    assertEquals(Some("hello"), parsed.nullableString)
    assertEquals(Some(42), parsed.nullableInt)
    assertEquals(Some(true), parsed.nullableBool)
  }

  // ---- Wrapper ID types ----

  @Test
  def testCustomerIdValueOf(): Unit = {
    val id = CustomerId.valueOf("abc")
    assertEquals("abc", id.unwrap)
  }

  @Test
  def testOrderIdValueOf(): Unit = {
    val id = OrderId.valueOf("ORD-1")
    assertEquals("ORD-1", id.unwrap)
  }

  // ---- With methods (copy) ----

  @Test
  def testCustomerWithMethods(): Unit = {
    val updated = testCustomer.copy(name = "Jane Doe")
    assertEquals("Jane Doe", updated.name)
    assertEquals(testCustomer.customerId, updated.customerId)
    assertEquals(testCustomer.email, updated.email)
  }

  @Test
  def testOrderWithMethods(): Unit = {
    val order = Order(
      OrderId.valueOf("ORD-1"),
      CustomerId.valueOf("CUST-1"),
      1000L,
      Instant.ofEpochSecond(1000, 0)
    )
    val updated = order.copy(amountCents = 2000L)
    assertEquals(2000L, updated.amountCents)
    assertEquals(order.orderId, updated.orderId)
  }

  // ---- Echo with empty strings ----

  @Test
  def testEchoCustomerEmptyStrings(): Unit = {
    val empty = Customer(CustomerId.valueOf(""), "", "")
    val parsed = echoClient.echoCustomer(empty)
    assertEquals("", parsed.customerId.unwrap)
    assertEquals("", parsed.name)
    assertEquals("", parsed.email)
  }
}
