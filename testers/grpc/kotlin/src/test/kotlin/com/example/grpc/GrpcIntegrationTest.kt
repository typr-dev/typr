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

class GrpcIntegrationTest {

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var orderClient: OrderServiceClient
    private lateinit var echoClient: EchoServiceClient

    private val testCustomer = Customer(CustomerId.valueOf("CUST-123"), "John Doe", "john@example.com")

    @Before
    fun setUp() {
        val serverName = InProcessServerBuilder.generateName()

        val orderImpl = object : OrderService {
            override fun getCustomer(request: GetCustomerRequest): GetCustomerResponse =
                GetCustomerResponse(Customer(CustomerId.valueOf(request.customerId), "John Doe", "john@example.com"))

            override fun createOrder(request: CreateOrderRequest): CreateOrderResponse =
                CreateOrderResponse(request.order!!.orderId.unwrap(), OrderStatus.ORDER_STATUS_PENDING)

            override fun listOrders(request: ListOrdersRequest): Iterator<OrderUpdate> {
                val updates = mutableListOf(
                    OrderUpdate("ORD-1", OrderStatus.ORDER_STATUS_PENDING, Instant.ofEpochSecond(1000, 500)),
                    OrderUpdate("ORD-2", OrderStatus.ORDER_STATUS_SHIPPED, Instant.ofEpochSecond(2000, 1000)),
                    OrderUpdate("ORD-3", OrderStatus.ORDER_STATUS_DELIVERED, Instant.ofEpochSecond(3000, 0))
                )
                return updates.iterator()
            }

            override fun submitOrders(requests: Iterator<CreateOrderRequest>): OrderSummary =
                throw UnsupportedOperationException()

            override fun chat(requests: Iterator<ChatMessage>): Iterator<ChatMessage> =
                throw UnsupportedOperationException()
        }

        val echoImpl = object : EchoService {
            override fun echoScalarTypes(request: ScalarTypes): ScalarTypes = request
            override fun echoCustomer(request: Customer): Customer = request
            override fun echoOrder(request: Order): Order = request
            override fun echoInventory(request: Inventory): Inventory = request
            override fun echoOuter(request: Outer): Outer = request
            override fun echoOptionalFields(request: OptionalFields): OptionalFields = request
            override fun echoWellKnownTypes(request: WellKnownTypesMessage): WellKnownTypesMessage = request
            override fun echoPaymentMethod(request: PaymentMethod): PaymentMethod = request
            override fun echoNotification(request: Notification): Notification = request
        }

        server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(OrderServiceServer(orderImpl))
            .addService(EchoServiceServer(echoImpl))
            .build()
            .start()

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        orderClient = OrderServiceClient(channel)
        echoClient = EchoServiceClient(channel)
    }

    @After
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    // ---- gRPC service tests ----

    @Test
    fun testGetCustomer() {
        val response = orderClient.getCustomer(GetCustomerRequest("CUST-123"))
        assertNotNull(response)
        assertNotNull(response.customer)
        assertEquals("CUST-123", response.customer!!.customerId.unwrap())
        assertEquals("John Doe", response.customer!!.name)
        assertEquals("john@example.com", response.customer!!.email)
    }

    @Test
    fun testCreateOrder() {
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
    fun testListOrders() {
        val updates = orderClient.listOrders(ListOrdersRequest("CUST-123", 10))
        val results = mutableListOf<OrderUpdate>()
        for (update in updates) {
            results.add(update)
        }

        assertEquals(3, results.size)
        assertEquals("ORD-1", results[0].orderId)
        assertEquals(OrderStatus.ORDER_STATUS_PENDING, results[0].status)
        assertEquals("ORD-2", results[1].orderId)
        assertEquals(OrderStatus.ORDER_STATUS_SHIPPED, results[1].status)
        assertEquals("ORD-3", results[2].orderId)
        assertEquals(OrderStatus.ORDER_STATUS_DELIVERED, results[2].status)
    }

    // ---- Echo round-trip tests ----

    @Test
    fun testEchoCustomer() {
        val parsed = echoClient.echoCustomer(testCustomer)
        assertEquals(testCustomer, parsed)
    }

    @Test
    fun testEchoOrder() {
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
    fun testEchoScalarTypes() {
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
    fun testEnumToValueFromValueRoundTrip() {
        for (status in OrderStatus.entries) {
            val wireValue = status.toValue()
            val back = OrderStatus.fromValue(wireValue)
            assertEquals(status, back)
        }
    }

    @Test
    fun testEnumForce() {
        assertEquals(OrderStatus.ORDER_STATUS_PENDING, OrderStatus.force("ORDER_STATUS_PENDING"))
    }

    @Test(expected = RuntimeException::class)
    fun testEnumForceInvalid() {
        OrderStatus.force("NONEXISTENT")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEnumFromValueInvalid() {
        OrderStatus.fromValue(999)
    }

    @Test
    fun testPriorityEnumRoundTrip() {
        for (p in Priority.entries) {
            val wireValue = p.toValue()
            val back = Priority.fromValue(wireValue)
            assertEquals(p, back)
        }
    }

    // ---- Optional fields ----

    @Test
    fun testEchoOptionalFieldsAllPresent() {
        val opt = OptionalFields("Alice", 30, testCustomer)
        val parsed = echoClient.echoOptionalFields(opt)
        assertEquals("Alice", parsed.name)
        assertEquals(30, parsed.age)
        assertNotNull(parsed.customer)
        assertEquals("CUST-123", parsed.customer!!.customerId.unwrap())
    }

    @Test
    fun testEchoOptionalFieldsAllEmpty() {
        val opt = OptionalFields(null, null, null)
        val parsed = echoClient.echoOptionalFields(opt)
        assertNull(parsed.name)
        assertNull(parsed.age)
        assertNull(parsed.customer)
    }

    @Test
    fun testEchoOptionalFieldsPartiallyPresent() {
        val opt = OptionalFields("Bob", null, null)
        val parsed = echoClient.echoOptionalFields(opt)
        assertEquals("Bob", parsed.name)
        assertNull(parsed.age)
        assertNull(parsed.customer)
    }

    // ---- Nested messages ----

    @Test
    fun testEchoOuter() {
        val outer = Outer("outer-name", Inner(42, "inner-desc"))
        val parsed = echoClient.echoOuter(outer)
        assertEquals("outer-name", parsed.name)
        assertNotNull(parsed.inner)
        assertEquals(42, parsed.inner!!.value)
        assertEquals("inner-desc", parsed.inner!!.description)
    }

    // ---- OneOf types ----

    @Test
    fun testEchoPaymentMethodCreditCard() {
        val cc = CreditCard("4111111111111111", "12/25", "123")
        val method: PaymentMethodMethod = PaymentMethodMethod.CreditCardValue(cc)
        val pm = PaymentMethod("PAY-1", method)
        val parsed = echoClient.echoPaymentMethod(pm)
        assertEquals("PAY-1", parsed.id)
        assertNotNull(parsed.method)
        assertTrue(parsed.method is PaymentMethodMethod.CreditCardValue)
        val ccv = parsed.method as PaymentMethodMethod.CreditCardValue
        assertEquals("4111111111111111", ccv.creditCard.cardNumber)
        assertEquals("12/25", ccv.creditCard.expiryDate)
        assertEquals("123", ccv.creditCard.cvv)
    }

    @Test
    fun testEchoPaymentMethodBankTransfer() {
        val bt = BankTransfer("123456789", "021000021")
        val method: PaymentMethodMethod = PaymentMethodMethod.BankTransferValue(bt)
        val pm = PaymentMethod("PAY-2", method)
        val parsed = echoClient.echoPaymentMethod(pm)
        assertEquals("PAY-2", parsed.id)
        assertNotNull(parsed.method)
        assertTrue(parsed.method is PaymentMethodMethod.BankTransferValue)
        val btv = parsed.method as PaymentMethodMethod.BankTransferValue
        assertEquals("123456789", btv.bankTransfer.accountNumber)
        assertEquals("021000021", btv.bankTransfer.routingNumber)
    }

    @Test
    fun testEchoPaymentMethodWallet() {
        val w = Wallet("wallet-42", "Stripe")
        val method: PaymentMethodMethod = PaymentMethodMethod.WalletValue(w)
        val pm = PaymentMethod("PAY-3", method)
        val parsed = echoClient.echoPaymentMethod(pm)
        assertEquals("PAY-3", parsed.id)
        assertNotNull(parsed.method)
        assertTrue(parsed.method is PaymentMethodMethod.WalletValue)
        val wv = parsed.method as PaymentMethodMethod.WalletValue
        assertEquals("wallet-42", wv.wallet.walletId)
        assertEquals("Stripe", wv.wallet.provider)
    }

    @Test
    fun testEchoNotificationWithEmailTarget() {
        val notif = Notification("Hello!", Priority.PRIORITY_HIGH, NotificationTarget.Email("user@example.com"))
        val parsed = echoClient.echoNotification(notif)
        assertEquals("Hello!", parsed.message)
        assertEquals(Priority.PRIORITY_HIGH, parsed.priority)
        assertNotNull(parsed.target)
        assertTrue(parsed.target is NotificationTarget.Email)
        assertEquals("user@example.com", (parsed.target as NotificationTarget.Email).email)
    }

    @Test
    fun testEchoNotificationWithPhoneTarget() {
        val notif = Notification("Alert", Priority.PRIORITY_CRITICAL, NotificationTarget.Phone("+1234567890"))
        val parsed = echoClient.echoNotification(notif)
        assertEquals("Alert", parsed.message)
        assertEquals(Priority.PRIORITY_CRITICAL, parsed.priority)
        assertNotNull(parsed.target)
        assertTrue(parsed.target is NotificationTarget.Phone)
        assertEquals("+1234567890", (parsed.target as NotificationTarget.Phone).phone)
    }

    @Test
    fun testEchoNotificationWithWebhookTarget() {
        val notif =
            Notification("Event", Priority.PRIORITY_LOW, NotificationTarget.WebhookUrl("https://hooks.example.com/abc"))
        val parsed = echoClient.echoNotification(notif)
        assertEquals("Event", parsed.message)
        assertEquals(Priority.PRIORITY_LOW, parsed.priority)
        assertNotNull(parsed.target)
        assertTrue(parsed.target is NotificationTarget.WebhookUrl)
        assertEquals("https://hooks.example.com/abc", (parsed.target as NotificationTarget.WebhookUrl).webhookUrl)
    }

    // ---- Collections ----

    @Test
    fun testEchoInventory() {
        val productIds = listOf("PROD-1", "PROD-2", "PROD-3")
        val stockCounts = mapOf("PROD-1" to 100, "PROD-2" to 200, "PROD-3" to 0)
        val orders = listOf(
            Order(OrderId.valueOf("ORD-1"), CustomerId.valueOf("CUST-1"), 1000L, Instant.ofEpochSecond(1000, 0)),
            Order(OrderId.valueOf("ORD-2"), CustomerId.valueOf("CUST-2"), 2000L, Instant.ofEpochSecond(2000, 0))
        )

        val inventory = Inventory("WH-1", productIds, stockCounts, orders)
        val parsed = echoClient.echoInventory(inventory)
        assertEquals("WH-1", parsed.warehouseId)
        assertEquals(3, parsed.productIds.size)
        assertEquals("PROD-1", parsed.productIds[0])
        assertEquals("PROD-2", parsed.productIds[1])
        assertEquals("PROD-3", parsed.productIds[2])
        assertEquals(3, parsed.stockCounts.size)
        assertEquals(100, parsed.stockCounts["PROD-1"])
        assertEquals(200, parsed.stockCounts["PROD-2"])
        assertEquals(0, parsed.stockCounts["PROD-3"])
        assertEquals(2, parsed.recentOrders.size)
        assertEquals("ORD-1", parsed.recentOrders[0].orderId.unwrap())
        assertEquals("ORD-2", parsed.recentOrders[1].orderId.unwrap())
    }

    @Test
    fun testEchoInventoryEmptyCollections() {
        val inventory = Inventory("WH-EMPTY", emptyList(), emptyMap(), emptyList())
        val parsed = echoClient.echoInventory(inventory)
        assertEquals("WH-EMPTY", parsed.warehouseId)
        assertTrue(parsed.productIds.isEmpty())
        assertTrue(parsed.stockCounts.isEmpty())
        assertTrue(parsed.recentOrders.isEmpty())
    }

    // ---- Well-known types ----

    @Test
    fun testEchoWellKnownTypes() {
        val msg = WellKnownTypesMessage(
            Instant.ofEpochSecond(1700000000L, 123456789),
            Duration.ofSeconds(3600, 500000000),
            "hello",
            42,
            true
        )
        val parsed = echoClient.echoWellKnownTypes(msg)
        assertEquals(msg.createdAt, parsed.createdAt)
        assertEquals(msg.ttl, parsed.ttl)
        assertEquals("hello", parsed.nullableString)
        assertEquals(42, parsed.nullableInt)
        assertEquals(true, parsed.nullableBool)
    }

    // ---- Wrapper ID types ----

    @Test
    fun testCustomerIdValueOf() {
        val id = CustomerId.valueOf("abc")
        assertEquals("abc", id.unwrap())
        assertEquals("abc", id.toString())
    }

    @Test
    fun testOrderIdValueOf() {
        val id = OrderId.valueOf("ORD-1")
        assertEquals("ORD-1", id.unwrap())
        assertEquals("ORD-1", id.toString())
    }

    // ---- With methods (copy) ----

    @Test
    fun testCustomerWithMethods() {
        val updated = testCustomer.copy(name = "Jane Doe")
        assertEquals("Jane Doe", updated.name)
        assertEquals(testCustomer.customerId, updated.customerId)
        assertEquals(testCustomer.email, updated.email)
    }

    @Test
    fun testOrderWithMethods() {
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
    fun testEchoCustomerEmptyStrings() {
        val empty = Customer(CustomerId.valueOf(""), "", "")
        val parsed = echoClient.echoCustomer(empty)
        assertEquals("", parsed.customerId.unwrap())
        assertEquals("", parsed.name)
        assertEquals("", parsed.email)
    }
}
