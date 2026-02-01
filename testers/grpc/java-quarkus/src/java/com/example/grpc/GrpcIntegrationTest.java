package com.example.grpc;

import static org.junit.Assert.*;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GrpcIntegrationTest {

  private Server server;
  private ManagedChannel channel;
  private OrderServiceClient orderClient;
  private EchoServiceClient echoClient;

  private final Customer testCustomer =
      new Customer(CustomerId.valueOf("CUST-123"), "John Doe", "john@example.com");

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();

    OrderService orderImpl =
        new OrderService() {
          @Override
          public GetCustomerResponse getCustomer(GetCustomerRequest request) {
            return new GetCustomerResponse(
                new Customer(
                    CustomerId.valueOf(request.customerId()), "John Doe", "john@example.com"));
          }

          @Override
          public CreateOrderResponse createOrder(CreateOrderRequest request) {
            return new CreateOrderResponse(
                request.order().orderId().unwrap(), OrderStatus.ORDER_STATUS_PENDING);
          }

          @Override
          public Iterator<OrderUpdate> listOrders(ListOrdersRequest request) {
            List<OrderUpdate> updates = new ArrayList<>();
            updates.add(
                new OrderUpdate(
                    "ORD-1", OrderStatus.ORDER_STATUS_PENDING, Instant.ofEpochSecond(1000, 500)));
            updates.add(
                new OrderUpdate(
                    "ORD-2", OrderStatus.ORDER_STATUS_SHIPPED, Instant.ofEpochSecond(2000, 1000)));
            updates.add(
                new OrderUpdate(
                    "ORD-3", OrderStatus.ORDER_STATUS_DELIVERED, Instant.ofEpochSecond(3000, 0)));
            return updates.iterator();
          }

          @Override
          public OrderSummary submitOrders(Iterator<CreateOrderRequest> requests) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Iterator<ChatMessage> chat(Iterator<ChatMessage> requests) {
            throw new UnsupportedOperationException();
          }
        };

    EchoService echoImpl =
        new EchoService() {
          @Override
          public ScalarTypes echoScalarTypes(ScalarTypes request) {
            return request;
          }

          @Override
          public Customer echoCustomer(Customer request) {
            return request;
          }

          @Override
          public Order echoOrder(Order request) {
            return request;
          }

          @Override
          public Inventory echoInventory(Inventory request) {
            return request;
          }

          @Override
          public Outer echoOuter(Outer request) {
            return request;
          }

          @Override
          public OptionalFields echoOptionalFields(OptionalFields request) {
            return request;
          }

          @Override
          public WellKnownTypesMessage echoWellKnownTypes(WellKnownTypesMessage request) {
            return request;
          }

          @Override
          public PaymentMethod echoPaymentMethod(PaymentMethod request) {
            return request;
          }

          @Override
          public Notification echoNotification(Notification request) {
            return request;
          }
        };

    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new OrderServiceServer(orderImpl))
            .addService(new EchoServiceServer(echoImpl))
            .build()
            .start();

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    orderClient = new OrderServiceClient(channel);
    echoClient = new EchoServiceClient(channel);
  }

  @After
  public void tearDown() throws Exception {
    channel.shutdownNow();
    server.shutdownNow();
  }

  // ---- gRPC service tests ----

  @Test
  public void testGetCustomer() {
    GetCustomerResponse response = orderClient.getCustomer(new GetCustomerRequest("CUST-123"));
    assertNotNull(response);
    assertNotNull(response.customer());
    assertEquals("CUST-123", response.customer().customerId().unwrap());
    assertEquals("John Doe", response.customer().name());
    assertEquals("john@example.com", response.customer().email());
  }

  @Test
  public void testCreateOrder() {
    Order order =
        new Order(
            OrderId.valueOf("ORD-42"),
            CustomerId.valueOf("CUST-1"),
            9999L,
            Instant.ofEpochSecond(1700000000L, 123456789));

    CreateOrderResponse response = orderClient.createOrder(new CreateOrderRequest(order));
    assertNotNull(response);
    assertEquals("ORD-42", response.orderId());
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, response.status());
  }

  @Test
  public void testListOrders() {
    Iterator<OrderUpdate> updates = orderClient.listOrders(new ListOrdersRequest("CUST-123", 10));
    List<OrderUpdate> results = new ArrayList<>();
    updates.forEachRemaining(results::add);

    assertEquals(3, results.size());
    assertEquals("ORD-1", results.get(0).orderId());
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, results.get(0).status());
    assertEquals("ORD-2", results.get(1).orderId());
    assertEquals(OrderStatus.ORDER_STATUS_SHIPPED, results.get(1).status());
    assertEquals("ORD-3", results.get(2).orderId());
    assertEquals(OrderStatus.ORDER_STATUS_DELIVERED, results.get(2).status());
  }

  // ---- Echo round-trip tests ----

  @Test
  public void testEchoCustomer() {
    Customer parsed = echoClient.echoCustomer(testCustomer);
    assertEquals(testCustomer, parsed);
  }

  @Test
  public void testEchoOrder() {
    Order order =
        new Order(
            OrderId.valueOf("ORD-1"),
            CustomerId.valueOf("CUST-1"),
            5000L,
            Instant.ofEpochSecond(1700000000L, 123456789));
    Order parsed = echoClient.echoOrder(order);
    assertEquals(order.orderId(), parsed.orderId());
    assertEquals(order.customerId(), parsed.customerId());
    assertEquals(order.amountCents(), parsed.amountCents());
    assertEquals(order.createdAt(), parsed.createdAt());
  }

  // ---- Scalar types ----

  @Test
  public void testEchoScalarTypes() {
    ScalarTypes scalars =
        new ScalarTypes(
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
            ByteString.copyFromUtf8("binary data"));
    ScalarTypes parsed = echoClient.echoScalarTypes(scalars);
    assertEquals(scalars.doubleVal(), parsed.doubleVal(), 0.0001);
    assertEquals(scalars.floatVal(), parsed.floatVal(), 0.0001f);
    assertEquals(scalars.int32Val(), parsed.int32Val());
    assertEquals(scalars.int64Val(), parsed.int64Val());
    assertEquals(scalars.uint32Val(), parsed.uint32Val());
    assertEquals(scalars.uint64Val(), parsed.uint64Val());
    assertEquals(scalars.sint32Val(), parsed.sint32Val());
    assertEquals(scalars.sint64Val(), parsed.sint64Val());
    assertEquals(scalars.fixed32Val(), parsed.fixed32Val());
    assertEquals(scalars.fixed64Val(), parsed.fixed64Val());
    assertEquals(scalars.sfixed32Val(), parsed.sfixed32Val());
    assertEquals(scalars.sfixed64Val(), parsed.sfixed64Val());
    assertEquals(scalars.boolVal(), parsed.boolVal());
    assertEquals(scalars.stringVal(), parsed.stringVal());
    assertEquals(scalars.bytesVal(), parsed.bytesVal());
  }

  // ---- Enum tests ----

  @Test
  public void testEnumToValueFromValueRoundTrip() {
    for (OrderStatus status : OrderStatus.values()) {
      Integer wireValue = status.toValue();
      OrderStatus back = OrderStatus.fromValue(wireValue);
      assertEquals(status, back);
    }
  }

  @Test
  public void testEnumForce() {
    assertEquals(OrderStatus.ORDER_STATUS_PENDING, OrderStatus.force("ORDER_STATUS_PENDING"));
  }

  @Test(expected = RuntimeException.class)
  public void testEnumForceInvalid() {
    OrderStatus.force("NONEXISTENT");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEnumFromValueInvalid() {
    OrderStatus.fromValue(999);
  }

  @Test
  public void testPriorityEnumRoundTrip() {
    for (Priority p : Priority.values()) {
      Integer wireValue = p.toValue();
      Priority back = Priority.fromValue(wireValue);
      assertEquals(p, back);
    }
  }

  // ---- Optional fields ----

  @Test
  public void testEchoOptionalFieldsAllPresent() {
    OptionalFields opt =
        new OptionalFields(Optional.of("Alice"), Optional.of(30), Optional.of(testCustomer));
    OptionalFields parsed = echoClient.echoOptionalFields(opt);
    assertEquals(Optional.of("Alice"), parsed.name());
    assertEquals(Optional.of(30), parsed.age());
    assertTrue(parsed.customer().isPresent());
    assertEquals("CUST-123", parsed.customer().get().customerId().unwrap());
  }

  @Test
  public void testEchoOptionalFieldsAllEmpty() {
    OptionalFields opt = new OptionalFields(Optional.empty(), Optional.empty(), Optional.empty());
    OptionalFields parsed = echoClient.echoOptionalFields(opt);
    assertEquals(Optional.empty(), parsed.name());
    assertEquals(Optional.empty(), parsed.age());
    assertEquals(Optional.empty(), parsed.customer());
  }

  @Test
  public void testEchoOptionalFieldsPartiallyPresent() {
    OptionalFields opt = new OptionalFields(Optional.of("Bob"), Optional.empty(), Optional.empty());
    OptionalFields parsed = echoClient.echoOptionalFields(opt);
    assertEquals(Optional.of("Bob"), parsed.name());
    assertEquals(Optional.empty(), parsed.age());
    assertEquals(Optional.empty(), parsed.customer());
  }

  // ---- Nested messages ----

  @Test
  public void testEchoOuter() {
    Outer outer = new Outer("outer-name", new Inner(42, "inner-desc"));
    Outer parsed = echoClient.echoOuter(outer);
    assertEquals("outer-name", parsed.name());
    assertNotNull(parsed.inner());
    assertEquals(Integer.valueOf(42), parsed.inner().value());
    assertEquals("inner-desc", parsed.inner().description());
  }

  // ---- OneOf types ----

  @Test
  public void testEchoPaymentMethodCreditCard() {
    CreditCard cc = new CreditCard("4111111111111111", "12/25", "123");
    PaymentMethodMethod method = new PaymentMethodMethod.CreditCardValue(cc);
    PaymentMethod pm = new PaymentMethod("PAY-1", method);
    PaymentMethod parsed = echoClient.echoPaymentMethod(pm);
    assertEquals("PAY-1", parsed.id());
    assertTrue(parsed.method() instanceof PaymentMethodMethod.CreditCardValue);
    PaymentMethodMethod.CreditCardValue ccv = (PaymentMethodMethod.CreditCardValue) parsed.method();
    assertEquals("4111111111111111", ccv.creditCard().cardNumber());
    assertEquals("12/25", ccv.creditCard().expiryDate());
    assertEquals("123", ccv.creditCard().cvv());
  }

  @Test
  public void testEchoPaymentMethodBankTransfer() {
    BankTransfer bt = new BankTransfer("123456789", "021000021");
    PaymentMethodMethod method = new PaymentMethodMethod.BankTransferValue(bt);
    PaymentMethod pm = new PaymentMethod("PAY-2", method);
    PaymentMethod parsed = echoClient.echoPaymentMethod(pm);
    assertEquals("PAY-2", parsed.id());
    assertTrue(parsed.method() instanceof PaymentMethodMethod.BankTransferValue);
    PaymentMethodMethod.BankTransferValue btv =
        (PaymentMethodMethod.BankTransferValue) parsed.method();
    assertEquals("123456789", btv.bankTransfer().accountNumber());
    assertEquals("021000021", btv.bankTransfer().routingNumber());
  }

  @Test
  public void testEchoPaymentMethodWallet() {
    Wallet w = new Wallet("wallet-42", "Stripe");
    PaymentMethodMethod method = new PaymentMethodMethod.WalletValue(w);
    PaymentMethod pm = new PaymentMethod("PAY-3", method);
    PaymentMethod parsed = echoClient.echoPaymentMethod(pm);
    assertEquals("PAY-3", parsed.id());
    assertTrue(parsed.method() instanceof PaymentMethodMethod.WalletValue);
    PaymentMethodMethod.WalletValue wv = (PaymentMethodMethod.WalletValue) parsed.method();
    assertEquals("wallet-42", wv.wallet().walletId());
    assertEquals("Stripe", wv.wallet().provider());
  }

  @Test
  public void testEchoNotificationWithEmailTarget() {
    Notification notif =
        new Notification(
            "Hello!", Priority.PRIORITY_HIGH, new NotificationTarget.Email("user@example.com"));
    Notification parsed = echoClient.echoNotification(notif);
    assertEquals("Hello!", parsed.message());
    assertEquals(Priority.PRIORITY_HIGH, parsed.priority());
    assertTrue(parsed.target() instanceof NotificationTarget.Email);
    assertEquals("user@example.com", ((NotificationTarget.Email) parsed.target()).email());
  }

  @Test
  public void testEchoNotificationWithPhoneTarget() {
    Notification notif =
        new Notification(
            "Alert", Priority.PRIORITY_CRITICAL, new NotificationTarget.Phone("+1234567890"));
    Notification parsed = echoClient.echoNotification(notif);
    assertEquals("Alert", parsed.message());
    assertEquals(Priority.PRIORITY_CRITICAL, parsed.priority());
    assertTrue(parsed.target() instanceof NotificationTarget.Phone);
    assertEquals("+1234567890", ((NotificationTarget.Phone) parsed.target()).phone());
  }

  @Test
  public void testEchoNotificationWithWebhookTarget() {
    Notification notif =
        new Notification(
            "Event",
            Priority.PRIORITY_LOW,
            new NotificationTarget.WebhookUrl("https://hooks.example.com/abc"));
    Notification parsed = echoClient.echoNotification(notif);
    assertEquals("Event", parsed.message());
    assertEquals(Priority.PRIORITY_LOW, parsed.priority());
    assertTrue(parsed.target() instanceof NotificationTarget.WebhookUrl);
    assertEquals(
        "https://hooks.example.com/abc",
        ((NotificationTarget.WebhookUrl) parsed.target()).webhookUrl());
  }

  // ---- Collections ----

  @Test
  public void testEchoInventory() {
    List<String> productIds = List.of("PROD-1", "PROD-2", "PROD-3");
    Map<String, Integer> stockCounts = new LinkedHashMap<>();
    stockCounts.put("PROD-1", 100);
    stockCounts.put("PROD-2", 200);
    stockCounts.put("PROD-3", 0);
    List<Order> orders =
        List.of(
            new Order(
                OrderId.valueOf("ORD-1"),
                CustomerId.valueOf("CUST-1"),
                1000L,
                Instant.ofEpochSecond(1000, 0)),
            new Order(
                OrderId.valueOf("ORD-2"),
                CustomerId.valueOf("CUST-2"),
                2000L,
                Instant.ofEpochSecond(2000, 0)));

    Inventory inventory = new Inventory("WH-1", productIds, stockCounts, orders);
    Inventory parsed = echoClient.echoInventory(inventory);
    assertEquals("WH-1", parsed.warehouseId());
    assertEquals(3, parsed.productIds().size());
    assertEquals("PROD-1", parsed.productIds().get(0));
    assertEquals("PROD-2", parsed.productIds().get(1));
    assertEquals("PROD-3", parsed.productIds().get(2));
    assertEquals(3, parsed.stockCounts().size());
    assertEquals(Integer.valueOf(100), parsed.stockCounts().get("PROD-1"));
    assertEquals(Integer.valueOf(200), parsed.stockCounts().get("PROD-2"));
    assertEquals(Integer.valueOf(0), parsed.stockCounts().get("PROD-3"));
    assertEquals(2, parsed.recentOrders().size());
    assertEquals("ORD-1", parsed.recentOrders().get(0).orderId().unwrap());
    assertEquals("ORD-2", parsed.recentOrders().get(1).orderId().unwrap());
  }

  @Test
  public void testEchoInventoryEmptyCollections() {
    Inventory inventory = new Inventory("WH-EMPTY", List.of(), Map.of(), List.of());
    Inventory parsed = echoClient.echoInventory(inventory);
    assertEquals("WH-EMPTY", parsed.warehouseId());
    assertTrue(parsed.productIds().isEmpty());
    assertTrue(parsed.stockCounts().isEmpty());
    assertTrue(parsed.recentOrders().isEmpty());
  }

  // ---- Well-known types ----

  @Test
  public void testEchoWellKnownTypes() {
    WellKnownTypesMessage msg =
        new WellKnownTypesMessage(
            Instant.ofEpochSecond(1700000000L, 123456789),
            Duration.ofSeconds(3600, 500000000),
            Optional.of("hello"),
            Optional.of(42),
            Optional.of(true));
    WellKnownTypesMessage parsed = echoClient.echoWellKnownTypes(msg);
    assertEquals(msg.createdAt(), parsed.createdAt());
    assertEquals(msg.ttl(), parsed.ttl());
    assertEquals(Optional.of("hello"), parsed.nullableString());
    assertEquals(Optional.of(42), parsed.nullableInt());
    assertEquals(Optional.of(true), parsed.nullableBool());
  }

  // ---- Wrapper ID types ----

  @Test
  public void testCustomerIdValueOf() {
    CustomerId id = CustomerId.valueOf("abc");
    assertEquals("abc", id.unwrap());
    assertEquals("abc", id.toString());
  }

  @Test
  public void testOrderIdValueOf() {
    OrderId id = OrderId.valueOf("ORD-1");
    assertEquals("ORD-1", id.unwrap());
    assertEquals("ORD-1", id.toString());
  }

  // ---- With methods ----

  @Test
  public void testCustomerWithMethods() {
    Customer updated = testCustomer.withName("Jane Doe");
    assertEquals("Jane Doe", updated.name());
    assertEquals(testCustomer.customerId(), updated.customerId());
    assertEquals(testCustomer.email(), updated.email());
  }

  @Test
  public void testOrderWithMethods() {
    Order order =
        new Order(
            OrderId.valueOf("ORD-1"),
            CustomerId.valueOf("CUST-1"),
            1000L,
            Instant.ofEpochSecond(1000, 0));
    Order updated = order.withAmountCents(2000L);
    assertEquals(Long.valueOf(2000L), updated.amountCents());
    assertEquals(order.orderId(), updated.orderId());
  }

  // ---- Echo with empty strings ----

  @Test
  public void testEchoCustomerEmptyStrings() {
    Customer empty = new Customer(CustomerId.valueOf(""), "", "");
    Customer parsed = echoClient.echoCustomer(empty);
    assertEquals("", parsed.customerId().unwrap());
    assertEquals("", parsed.name());
    assertEquals("", parsed.email());
  }
}
