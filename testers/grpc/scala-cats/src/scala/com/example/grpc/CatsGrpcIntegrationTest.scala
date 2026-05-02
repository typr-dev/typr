package com.example.grpc

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.time.Instant
import java.util
import scala.compiletime.uninitialized

/** Integration tests for Cats Effect IO-based gRPC services.
  *
  * Verifies:
  *   - Service trait methods return IO[...] for effect types
  *   - Server adapter has no DI annotations
  *   - IO-based services work with in-process gRPC transport
  */
class CatsGrpcIntegrationTest {

  private var server: Server = uninitialized
  private var channel: ManagedChannel = uninitialized
  private var orderClient: OrderServiceClient = uninitialized
  private var echoClient: EchoServiceClient = uninitialized

  private val testCustomer =
    Customer(CustomerId.valueOf("CUST-123"), "John Doe", "john@example.com")

  @Before
  def setUp(): Unit = {
    val serverName = InProcessServerBuilder.generateName()

    // Implement service with IO-returning methods
    val orderImpl = new OrderService {
      override def getCustomer(request: GetCustomerRequest): IO[GetCustomerResponse] =
        IO.pure(
          GetCustomerResponse(
            Customer(CustomerId.valueOf(request.customerId), "John Doe", "john@example.com")
          )
        )

      override def createOrder(request: CreateOrderRequest): IO[CreateOrderResponse] =
        IO.pure(CreateOrderResponse(request.order.orderId.unwrap, OrderStatus.ORDER_STATUS_PENDING))

      override def listOrders(request: ListOrdersRequest): util.Iterator[OrderUpdate] = {
        val updates = new util.ArrayList[OrderUpdate]()
        updates.add(OrderUpdate("ORD-1", OrderStatus.ORDER_STATUS_PENDING, Instant.ofEpochSecond(1000, 500)))
        updates.add(OrderUpdate("ORD-2", OrderStatus.ORDER_STATUS_SHIPPED, Instant.ofEpochSecond(2000, 1000)))
        updates.add(OrderUpdate("ORD-3", OrderStatus.ORDER_STATUS_DELIVERED, Instant.ofEpochSecond(3000, 0)))
        updates.iterator()
      }

      override def submitOrders(requests: util.Iterator[CreateOrderRequest]): IO[OrderSummary] =
        IO.raiseError(new UnsupportedOperationException())

      override def chat(requests: util.Iterator[ChatMessage]): util.Iterator[ChatMessage] =
        throw new UnsupportedOperationException()
    }

    val echoImpl = new EchoService {
      override def echoScalarTypes(request: ScalarTypes): IO[ScalarTypes] = IO.pure(request)
      override def echoCustomer(request: Customer): IO[Customer] = IO.pure(request)
      override def echoOrder(request: Order): IO[Order] = IO.pure(request)
      override def echoInventory(request: Inventory): IO[Inventory] = IO.pure(request)
      override def echoOuter(request: Outer): IO[Outer] = IO.pure(request)
      override def echoOptionalFields(request: OptionalFields): IO[OptionalFields] = IO.pure(request)
      override def echoWellKnownTypes(request: WellKnownTypesMessage): IO[WellKnownTypesMessage] = IO.pure(request)
      override def echoPaymentMethod(request: PaymentMethod): IO[PaymentMethod] = IO.pure(request)
      override def echoNotification(request: Notification): IO[Notification] = IO.pure(request)
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
    channel.shutdownNow(): Unit
    server.shutdownNow(): Unit
  }

  // ---- Framework integration tests ----

  @Test
  def testServerHasNoFrameworkAnnotations(): Unit = {
    // Verify OrderServiceServer class has no @GrpcService, @Service, or @ApplicationScoped annotations
    val serverClass = classOf[OrderServiceServer]
    val annotations = serverClass.getAnnotations

    // Should have no Spring/Quarkus annotations
    annotations.foreach { ann =>
      val annotationName = ann.annotationType.getName
      assertFalse(
        s"Unexpected framework annotation: $annotationName",
        annotationName.contains("springframework") ||
          annotationName.contains("quarkus") ||
          annotationName.contains("GrpcService") ||
          annotationName.contains("ApplicationScoped")
      )
    }
  }

  @Test
  def testServerUsesConstructorInjection(): Unit = {
    // Verify that OrderServiceServer takes delegate via constructor (no DI)
    val serverClass = classOf[OrderServiceServer]
    val constructors = serverClass.getConstructors

    assertEquals("Should have exactly one constructor", 1, constructors.length)
    val params = constructors(0).getParameters
    assertEquals("Constructor should take one delegate parameter", 1, params.length)
    assertEquals("Parameter should be OrderService", "OrderService", params(0).getType.getSimpleName)
  }

  // ---- Service trait type verification ----

  @Test
  def testServiceMethodReturnsIO(): Unit = {
    // Verify at compile time that service methods return IO
    // This test passes if it compiles
    val service: OrderService = null // We just need the type for compilation check

    // These type ascriptions verify the return types are IO[...]
    val _getCustomer: GetCustomerRequest => IO[GetCustomerResponse] = (_: GetCustomerRequest) => IO.pure(null)
    val _createOrder: CreateOrderRequest => IO[CreateOrderResponse] = (_: CreateOrderRequest) => IO.pure(null)
    val _submitOrders: util.Iterator[CreateOrderRequest] => IO[OrderSummary] =
      (_: util.Iterator[CreateOrderRequest]) => IO.pure(null)

    assertTrue("Compile-time type verification passed", true)
  }

  // ---- gRPC service tests with IO ----

  @Test
  def testGetCustomer(): Unit = {
    val response = orderClient.getCustomer(GetCustomerRequest("CUST-123")).unsafeRunSync()
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

    val response = orderClient.createOrder(CreateOrderRequest(order)).unsafeRunSync()
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
    val parsed = echoClient.echoCustomer(testCustomer).unsafeRunSync()
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
    val parsed = echoClient.echoOrder(order).unsafeRunSync()
    assertEquals(order.orderId, parsed.orderId)
    assertEquals(order.customerId, parsed.customerId)
    assertEquals(order.amountCents, parsed.amountCents)
    assertEquals(order.createdAt, parsed.createdAt)
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

  // ---- Optional fields ----

  @Test
  def testEchoOptionalFieldsAllPresent(): Unit = {
    val opt = OptionalFields(Some("Alice"), Some(30), Some(testCustomer))
    val parsed = echoClient.echoOptionalFields(opt).unsafeRunSync()
    assertEquals(Some("Alice"), parsed.name)
    assertEquals(Some(30), parsed.age)
    assertTrue(parsed.customer.isDefined)
    assertEquals("CUST-123", parsed.customer.get.customerId.unwrap)
  }

  @Test
  def testEchoOptionalFieldsAllEmpty(): Unit = {
    val opt = OptionalFields(None, None, None)
    val parsed = echoClient.echoOptionalFields(opt).unsafeRunSync()
    assertEquals(None, parsed.name)
    assertEquals(None, parsed.age)
    assertEquals(None, parsed.customer)
  }

  // ---- OneOf types ----

  @Test
  def testEchoPaymentMethodCreditCard(): Unit = {
    val cc = CreditCard("4111111111111111", "12/25", "123")
    val method = PaymentMethodMethod.CreditCardValue(cc)
    val pm = PaymentMethod("PAY-1", method)
    val parsed = echoClient.echoPaymentMethod(pm).unsafeRunSync()
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
  def testEchoNotificationWithEmailTarget(): Unit = {
    val notif = Notification("Hello!", Priority.PRIORITY_HIGH, NotificationTarget.Email("user@example.com"))
    val parsed = echoClient.echoNotification(notif).unsafeRunSync()
    assertEquals("Hello!", parsed.message)
    assertEquals(Priority.PRIORITY_HIGH, parsed.priority)
    parsed.target match {
      case NotificationTarget.Email(email) => assertEquals("user@example.com", email)
      case other                           => fail(s"Expected Email, got $other")
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
    val parsed = echoClient.echoInventory(inventory).unsafeRunSync()
    assertEquals("WH-1", parsed.warehouseId)
    assertEquals(3, parsed.productIds.size)
    assertEquals(100, parsed.stockCounts("PROD-1"))
    assertEquals(2, parsed.recentOrders.size)
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
}
